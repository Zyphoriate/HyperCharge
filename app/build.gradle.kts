import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
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

android {
    namespace = "io.github.zyphoriate.hypercharge"
    compileSdk = 37

    // Auto-extract version from git tag in CI (refs/tags/v1.2.3 → 1.2.3)
    // Locally falls back to hardcoded defaults
    val (ciVersionName, ciVersionCode) = run {
        val tagRef = System.getenv("GITHUB_REF") ?: ""
        val versionFromTag = tagRef.removePrefix("refs/tags/v")
        if (versionFromTag.isNotEmpty() && versionFromTag != tagRef) {
            val parts = versionFromTag.split(".")
            val major = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
            val minor = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
            val patch = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
            val code = major * 10000 + minor * 100 + patch
            versionFromTag to code
        } else {
            "2.0.0" to 10
        }
    }

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

    kotlinOptions {
        jvmTarget = "17"
    }

    applicationVariants.all {
        outputs.all {
            val newApkName = "HyperSmartCharge-v2-${versionName}_${versionCode}.apk"
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = newApkName
        }
    }
}

dependencies {
    // Compose — provided via Compose Multiplatform plugin + manual essentials
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
