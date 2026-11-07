import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":shared"))
    implementation(project(":clientUi"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    testImplementation(kotlin("test"))
}
compose.desktop {
    application {
        mainClass="com.inoagent.vpn.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi,TargetFormat.Exe)
            packageName="InoagentVPN"
            packageVersion="0.2.0"
            windows { menuGroup="Inoagent VPN";shortcut=true;upgradeUuid="2103810c-8982-4be7-8dba-258fcda5fce8";iconFile.set(project.file("resources/icon.ico")) }
            macOS { packageVersion="1.0.0" }
        }
    }
}
