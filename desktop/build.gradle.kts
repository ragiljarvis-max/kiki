import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    alias(libs.plugins.jetbrainsKotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":net"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.coroutines.core.jvm)
}

compose.desktop {
    application {
        mainClass = "net.garybully.kiki.desktop.MainKt"
        nativeDistributions {
            packageName = "KIKI"
            packageVersion = "1.0.0"
            description = "Kawaii Inter-device Kommunication Interface"
            vendor = "KIKI"
            // AppImage/Deb/MSI + app-image dir (portable) per target OS
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Msi)
        }
    }
}

// Portable app-image on every OS (plugin disables the AppImage format off-Linux,
// but jpackage --type app-image works on Windows too)
afterEvaluate {
    tasks.named<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>("packageAppImage") {
        isEnabled = true
    }
}

// Portable zip: app-image dir (bundled runtime, no install needed)
val packagePortableZip by tasks.registering(Zip::class) {
    dependsOn("packageAppImage")
    from(layout.buildDirectory.dir("compose/binaries/main/app/KIKI")) { into("KIKI") }
    archiveFileName.set("KIKI-portable.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}
