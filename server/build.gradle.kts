plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "com.eventverse.app"
version = "1.0.0"
application {
    mainClass = "com.eventverse.app.ApplicationKt"
}

/**
 * Auto-reload lokal: `./gradlew :server:run` menyalakan mode development Ktor, yang memuat ulang
 * class dari `build/classes` setiap kali terminal lain menjalankan `./gradlew -t :server:classes`.
 *
 * Sengaja dipasang di task `run` saja, bukan `applicationDefaultJvmArgs`: nilai di sana ikut tertanam
 * ke skrip distribusi, dan mode development di produksi berarti class loader ekstra, pemantauan
 * berkas, dan pesan error yang lebih rinci. Matikan dengan `-PktorDev=false`.
 */
tasks.named<JavaExec>("run") {
    systemProperty("io.ktor.development", providers.gradleProperty("ktorDev").getOrElse("true"))
}

dependencies {
    implementation(project(":core"))
    // Ensure IDE Language Server (without KMP support) can resolve domain symbols from compiled jar
    compileOnly(files(rootProject.file("core/build/libs/core-jvm.jar")))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverSse)
    implementation(libs.ktor.clientApache5)

    // Database & Migrations
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    // Provides a real JSONB column binding, so JSON documents are sent to PostgreSQL as
    // jsonb rather than as varchar (which the server rejects on a jsonb column).
    implementation(libs.exposed.json)
    // Real `timestamp()` column bound to kotlinx.datetime.Instant, for the audit log.
    implementation(libs.exposed.kotlinDatetime)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation("com.auth0:java-jwt:4.4.0")
    implementation(libs.pdfbox)
    implementation(libs.zxing.core)
    // Membaca berkas .xlsx HPP lama beserta gambar mockup yang tertanam di dalam sheet.
    implementation(libs.poi.ooxml)
    // Object storage (S3-compatible / MinIO) untuk berkas PO yang di-upload
    implementation(libs.awssdk.s3)

    // Agent LLM discovery (plan §2 A8, keputusan D4). **Hanya `server`** yang menanggung kerangka ini:
    // `core` tetap murni tanpa framework (DDD §2 DDD-1), dan kontraknya sudah ada di domain sebagai
    // `DiscoveryAgent`. `koog:1.3.0` tidak menarik kotlinx-datetime, jadi versi 0.6.2 repo ini aman.
    implementation(libs.koog.agents)
    // Klien provider DeepSeek (kunci: `DEEPSEEK_API_KEY`). Versi klien dipublikasikan terpisah dari inti.
    implementation(libs.koog.deepseekClient)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
/**
 * Impor batch arsip HPP Excel lama ke Knowledge Base tenant.
 *
 *     ./gradlew :server:importHistoricalCosting --args="--dir=data/excel-hpp --tenant=<tenantId>"
 *
 * Memakai `JavaExec` terpisah, bukan `application { mainClass }`, supaya `./gradlew :server:run`
 * tetap menjalankan server Ktor dan bukan importer.
 */
tasks.register<JavaExec>("importHistoricalCosting") {
    group = "wemade"
    description = "Impor berkas .xlsx HPP historis ke tabel costing_product_benchmarks"
    mainClass.set("com.eventverse.app.cli.HistoricalCostingCliImporter")
    classpath = sourceSets["main"].runtimeClasspath
    // Kunci API dan kredensial database diwariskan dari shell yang menjalankan Gradle.
    environment(System.getenv())
    standardInput = System.`in`
}

/**
 * Test otomatis **tidak boleh** memanggil LLM sungguhan (aturan repo). `EnvLoader` ikut membaca `.env`, jadi mesin
 * dev yang menyalakan `DISCOVERY_AGENT=koog` membuat `DiscoveryApiTest` memanggil DeepSeek (lambat, berbiaya, dan
 * melewati batas 60 detik). Env sistem mengalahkan `.env`, jadi agent dikunci deterministik untuk semua task test.
 * Eval live memakai gerbang tersendiri (`DISCOVERY_LIVE_EVALS`) dan membangun agennya sendiri — tidak terpengaruh.
 *
 * Agent wawancara punya saklar **terpisah** (`INTERVIEW_AGENT`, lihat `InterviewAgents`) dan sempat terlewat: dengan
 * `INTERVIEW_AGENT=koog` + `DEEPSEEK_API_KEY` di `.env`, `DiscoveryInterviewApiTest` memanggil `api.deepseek.com`
 * sungguhan (tiap giliran sampai 20 dtk, perencana sampai 90 dtk) sehingga tes berganti-ganti gagal di batas 60 dtk
 * `runTest`. Dikunci `off` di sini; `from(...)`/`plannerFromEnv()` hanya mengaktifkan agent bila nilainya `koog`.
 */
tasks.withType<Test>().configureEach {
    environment("DISCOVERY_AGENT", "deterministic")
    environment("INTERVIEW_AGENT", "off")

    // Tes tidak boleh menyentuh database kerja. `DatabaseFactory.init()` menjalankan migrasi Flyway dan bawaan
    // `DB_NAME`-nya `wemake_erp` (juga dari `.env`), jadi tanpa ini `:server:test` ikut memigrasi DB dev.
    // Env sistem mengalahkan `.env`; `DB_NAME` scratch yang Anda set sendiri tetap dihormati. Database-nya harus
    // sudah ada: `createdb wemake_erp_scratch_test` (atau `docker exec wemade-postgres psql -U postgres -c "CREATE
    // DATABASE wemake_erp_scratch_test"`). `wemade.requireScratchDb` menyalakan pagar di `DatabaseFactory.init()`
    // yang menolak URL non-scratch (mis. `DB_JDBC_URL` dari `.env`, yang tidak bisa ditimpa dari sini).
    val scratchDb = System.getenv("DB_NAME")?.takeIf { it.contains("scratch") } ?: "wemake_erp_scratch_test"
    environment("DB_NAME", scratchDb)
    systemProperty("wemade.requireScratchDb", "true")
}
