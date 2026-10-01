plugins {
    kotlin("jvm") version "2.4.10"
    application
}

repositories {
    mavenCentral()
}

group = "com.eventverse.infra"
version = "0.1.0"

// Versi provider di-PIN — nama resource Cloudflare berubah antar major version
// (TRD-PAY-001 §4.2 catatan versi provider). Jangan naikkan tanpa memeriksa
// nama class ZeroTrust*/DnsRecord di versi baru.
dependencies {
    implementation("com.pulumi:pulumi:1.37.3")
    implementation("com.pulumi:oci:5.1.0")
    implementation("com.pulumi:cloudflare:6.21.0")
    implementation("com.pulumi:random:4.21.2")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.eventverse.infra.MainKt")
}
