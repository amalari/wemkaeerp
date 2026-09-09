import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))
    // Ensure IDE Language Server (without KMP support) can resolve shared & domain symbols from compiled jar
    compileOnly(files(rootProject.file("app/shared/build/libs/shared-jvm.jar")))
    compileOnly(files(rootProject.file("core/build/libs/core-jvm.jar")))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.eventverse.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.eventverse.app"
            packageVersion = "1.0.0"
        }
    }
}