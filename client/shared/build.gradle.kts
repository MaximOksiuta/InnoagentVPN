plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }
    jvm("desktop")
    val iosArm64 = iosArm64()
    val iosSimulatorArm64 = iosSimulatorArm64()
    val iosX64 = iosX64()
    val macosArm64 = macosArm64()
    val macosX64 = macosX64()

    listOf(iosArm64, iosSimulatorArm64, iosX64, macosArm64, macosX64).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.ktor:ktor-client-core:3.3.2")
            implementation("io.ktor:ktor-client-content-negotiation:3.3.2")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.3.2")
        }
        appleMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.3.2")
        }
        val desktopMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.3.2")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.3.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}

android {
    namespace = "com.inoagent.vpn.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val desktopTests=kotlin.targets.getByName("desktop").compilations.getByName("test") as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
tasks.register<JavaExec>("serviceSmoke") {
    dependsOn("desktopTestClasses")
    mainClass.set("com.inoagent.vpn.shared.ServiceSmokeKt")
    classpath(desktopTests.output.allOutputs,desktopTests.runtimeDependencyFiles)
}
