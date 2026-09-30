package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.TenantId
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import javax.sql.DataSource

object DatabaseFactory {

    private var dataSource: HikariDataSource? = null

    /**
     * The owner connection, held explicitly rather than relied on as Exposed's default.
     *
     * Exposed treats the **most recent** `Database.connect` as the default for any transaction that
     * does not name one. Once the tenant-scoped pool connects, it becomes that default — so platform
     * work that passed no database silently ran as `wemade_app` and was refused by the `ops` schema.
     * Naming both sides removes the dependence on connection order entirely.
     */
    private var platformDatabase: Database? = null

    /**
     * A second pool connecting as a **non-superuser** role, used for tenant-scoped work.
     *
     * Two problems share this one solution.
     *
     * *Row-Level Security is bypassed for superusers.* The default `DB_USER` is `postgres`, so every
     * `apply_tenant_rls()` policy in this schema is currently decorative: tenant isolation rests
     * entirely on repositories remembering their `WHERE tenant_id = ?`, with no net underneath. See
     * `docs/tenant-isolation-rls-status.md`, which recorded this before the ledger existed.
     *
     * *The `ops` schema must stay unreachable from tenant-facing requests* (migration V16), and a
     * role that can read it defeats the point.
     *
     * A single role cannot do both jobs — `wemade_app` cannot see `ops`, and the owner role cannot
     * be subject to RLS. So tenant-scoped queries take this pool and platform queries take the
     * owner's.
     *
     * Null when `DB_APP_USER` is unset, in which case everything falls back to the owner connection
     * and behaviour is exactly as before — including the unenforced RLS. The warning at startup says
     * so out loud rather than letting it pass as configured.
     */
    private var appDataSource: HikariDataSource? = null
    private var appDatabase: Database? = null

    fun init(
        jdbcUrl: String = getEnvOrDefault(
            "DB_JDBC_URL",
            "jdbc:postgresql://${getEnvOrDefault("DB_HOST", "localhost")}:${
                getEnvOrDefault(
                    "DB_PORT",
                    "5432"
                )
            }/${getEnvOrDefault("DB_NAME", "wemake_erp")}"
        ),
        user: String = getEnvOrDefault("DB_USER", "postgres"),
        password: String = getEnvOrDefault("DB_PASSWORD", "postgres"),
        maximumPoolSize: Int = 10,
        runMigrations: Boolean = true
    ): DataSource {
        if (dataSource != null) return dataSource!!

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            this.driverClassName = "org.postgresql.Driver"
            this.maximumPoolSize = maximumPoolSize
            this.isAutoCommit = false
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            this.validate()
        }

        val ds = HikariDataSource(config)
        dataSource = ds

        if (runMigrations) {
            runFlywayMigration(ds)
        }

        platformDatabase = Database.connect(ds)
        connectTenantScopedPool(jdbcUrl, maximumPoolSize)
        return ds
    }

    /**
     * Opens the tenant-scoped pool if `DB_APP_USER` is configured.
     *
     * Migrations still run on the owner connection: `wemade_app` deliberately cannot create or move
     * tables, which is most of the reason it is safe to point request handling at it.
     */
    private fun connectTenantScopedPool(jdbcUrl: String, maximumPoolSize: Int) {
        // Dibaca lewat [EnvLoader], **bukan** `System.getenv`: nilai yang sudah ditulis di `.env`
        // harus berlaku juga untuk `./gradlew :server:run` dan `:server:test`. Selama kedua jalur ini
        // memakai `System.getenv`, penegakan RLS tampak selesai padahal tidak pernah aktif di dev.
        val appUser = EnvLoader.get("DB_APP_USER").takeIf { it.isNotBlank() }
        val appPassword = EnvLoader.get("DB_APP_PASSWORD").takeIf { it.isNotBlank() }

        if (appUser.isNullOrBlank() || appPassword.isNullOrBlank()) {
            println(
                "[DatabaseFactory] DB_APP_USER is not set; tenant-scoped queries will run as the " +
                        "owner role. PostgreSQL bypasses Row-Level Security for superusers, so tenant " +
                        "isolation currently depends on application code alone. Set DB_APP_USER / " +
                        "DB_APP_PASSWORD to wemade_app (created in V16) — in the environment or in .env."
            )
            return
        }

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = appUser
            this.password = appPassword
            this.driverClassName = "org.postgresql.Driver"
            this.maximumPoolSize = maximumPoolSize
            this.isAutoCommit = false
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            this.validate()
        }

        val ds = HikariDataSource(config)
        appDataSource = ds
        appDatabase = Database.connect(ds)
        // Diumumkan positif, bukan hanya kegagalannya: "tidak ada peringatan" tidak bisa dibedakan
        // dari "peringatan tidak tercetak" saat memverifikasi penegakan RLS.
        println("[DatabaseFactory] tenant-scoped pool active as '$appUser'; RLS enforced by PostgreSQL.")
    }

    fun runFlywayMigration(ds: DataSource) {
        val flyway = Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
        flyway.repair()
        flyway.migrate()
    }

    /**
     * Runs [block] in a transaction, choosing the connection from whether the work is tenant-scoped.
     *
     * Passing a [tenantId] means two things at once, and they are the same thing: the query belongs
     * to one factory, and it must therefore run under a role that RLS actually applies to. Omitting
     * it means platform work — the module ledger, the prospect funnel — which lives in the `ops`
     * schema that the tenant-scoped role cannot see.
     *
     * The split needs no new call sites: every repository already says which kind of work it is
     * doing by whether it passes a tenant.
     */
    suspend fun <T> dbQuery(
        tenantId: TenantId? = null,
        block: suspend () -> T
    ): T {
        val database = if (tenantId != null) appDatabase ?: platformDatabase else platformDatabase
        return newSuspendedTransaction(Dispatchers.IO, db = database) {
            if (tenantId != null) {
                // Enforce PostgreSQL Row-Level Security (RLS) for the current transaction
                exec("SET LOCAL app.current_tenant_id = '${tenantId.value}';")
            }
            block()
        }
    }

    fun close() {
        appDataSource?.close()
        appDataSource = null
        appDatabase = null
        platformDatabase = null
        dataSource?.close()
        dataSource = null
    }

    private fun getEnvOrDefault(name: String, default: String): String =
        EnvLoader.get(name).takeIf { it.isNotBlank() } ?: default
}
