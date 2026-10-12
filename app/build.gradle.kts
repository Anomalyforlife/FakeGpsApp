plugins { id("com.android.application") }
val persistentDebugKey = rootProject.file(".signing/debug.keystore")
android {
    namespace = "dev.scoutsolutions.fakegps"
    compileSdk = 35
    defaultConfig { applicationId = "dev.scoutsolutions.fakegps"; minSdk = 26; targetSdk = 35; versionCode = 4; versionName = "1.3.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    // Retain the executor's debug key, when present, and keep subsequent APK updates signed alike.
    signingConfigs.getByName("debug") {
        val previousKey = storeFile
        if (!persistentDebugKey.exists() && previousKey != null && previousKey.exists()) {
            persistentDebugKey.parentFile.mkdirs()
            previousKey.copyTo(persistentDebugKey)
        }
        // AGP generates a missing key only at its default path. Keep that path on the first build.
        if (persistentDebugKey.exists()) storeFile = persistentDebugKey
    }
    buildTypes { release { isMinifyEnabled = false } }
    lint { abortOnError = true }
}
dependencies {
    // 21.4.0 contains Kotlin 2.3 metadata unsupported by AGP 8.9's lint analyzer.
    implementation("com.google.android.gms:play-services-location:21.3.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
// The platform's single build command also runs the core unit tests and Android lint.
tasks.configureEach {
    if (name == "assembleDebug") {
        dependsOn("testDebugUnitTest", "lintDebug")
        doLast {
            val generatedKey = android.signingConfigs.getByName("debug").storeFile
            if (!persistentDebugKey.exists() && generatedKey != null && generatedKey.exists()) {
                persistentDebugKey.parentFile.mkdirs()
                generatedKey.copyTo(persistentDebugKey)
            }
        }
    }
}
