plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    signingConfigs {
        create("release") {
            storeFile = file("../notebox-release.jks")
            storePassword = System.getenv("NOT_EBOX_STORE_PASSWORD") ?: project.findProperty("NOT_EBOX_STORE_PASSWORD") as String?
            keyAlias = System.getenv("NOT_EBOX_KEY_ALIAS") ?: project.findProperty("NOT_EBOX_KEY_ALIAS") as String?
            keyPassword = System.getenv("NOT_EBOX_KEY_PASSWORD") ?: project.findProperty("NOT_EBOX_KEY_PASSWORD") as String?
        }
    }
    namespace = "com.ivangames.notebox"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ivangames.notebox"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }


    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
