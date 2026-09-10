plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "com.eventverse.app"
version = "1.0.0"
application {
    mainClass = "com.eventverse.app.ApplicationKt"
}

dependencies {
    implementation(project(":core"))
    // Ensure IDE Language Server (without KMP support) can resolve domain symbols from compiled jar
    compileOnly(files(rootProject.file("core/build/libs/core-jvm.jar")))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)

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

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}