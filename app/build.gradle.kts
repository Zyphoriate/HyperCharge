import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.parcelize)
}

// Load keystore properties from file (local) or env vars (CI/CD)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.bufferedReader())
}

fun getKeystoreProp(key: String, envKey: String): String? {
    return keystoreProperties.getProperty(key)
        ?: System.getenv(envKey)
}

// Auto-extract version from git tag in CI (refs/tags/v1.2.3 → 1.2.3)
val ciVersion = run {
    val tagRef = System.getenv("GITHUB_REF") ?: ""
    val ver = tagRef.removePrefix("refs/tags/v")
    if (ver.isNotEmpty() && ver != tagRef) {
        val parts = ver.split(".")
        val major = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
        val minor = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
        val patch = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
        ver to (major * 10000 + minor * 100 + patch)
    } else {
        "2.0.0" to 10
    }
}
val ciVersionName = ciVersion.first
val ciVersionCode = ciVersion.second

android {
    namespace = "io.github.zyphoriate.hypercharge"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 34
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    signingConfigs {
        val ksFile = getKeystoreProp("storeFile", "KEYSTORE_FILE")
        val ksAlias = getKeystoreProp("keyAlias", "KEY_ALIAS")
        if (!ksFile.isNullOrEmpty() && !ksAlias.isNullOrEmpty()) {
            create("release") {
                storeFile = rootProject.file(ksFile)
                keyAlias = ksAlias
                keyPassword = getKeystoreProp("keyPassword", "KEY_PASSWORD")
                storePassword = getKeystoreProp("storePassword", "STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val apkName = "HyperSmartCharge-v2-${ciVersionName}_${ciVersionCode}.apk"
            output.outputFileName.set(apkName)
        }
    }
}

dependencies {
    // Compose — provided via Compose Multiplatform plugin + manual essentials
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.activity)
    debugImplementation(libs.compose.ui.tooling)

    // Miuix UI (brings in compose-multiplatform transitive deps)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)

    // libxposed API 101
    compileOnly(libs.libxposed.api)

    // DexKit
    implementation(libs.dexkit)

    // Xiaomi charge API
    compileOnly(files("libs/mi_charge.jar"))
}
