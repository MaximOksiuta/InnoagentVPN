plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.inoagent.vpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.inoagent.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":clientUi"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.11.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.2")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.2")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")

    implementation("com.zaneschepke:amneziawg-android:2.3.7")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.room:room-testing:2.8.3")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

