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

    fun init(
        jdbcUrl: String = getEnvOrDefault(
            "DB_JDBC_URL",
            "jdbc:postgresql://${getEnvOrDefault("DB_HOST", "localhost")}:${
                getEnvOrDefault(
                    "DB_PORT",
                    "5432"
                )
            }/${getEnvOrDefault("DB_NAME", "wemade_erp")}"
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

        Database.connect(ds)
        return ds
    }

    fun runFlywayMigration(ds: DataSource) {
        val flyway = Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
        flyway.migrate()
    }

    suspend fun <T> dbQuery(
        tenantId: TenantId? = null,
        block: suspend () -> T
    ): T = newSuspendedTransaction(Dispatchers.IO) {
        if (tenantId != null) {
            // Enforce PostgreSQL Row-Level Security (RLS) for the current transaction
            exec("SET LOCAL app.current_tenant_id = '${tenantId.value}';")
        }
        block()
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }

    private fun getEnvOrDefault(name: String, default: String): String =
        EnvLoader.get(name, default)
}
