plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.jrtechofc.tether50d"
    minSdk = 24
    targetSdk = 36
    versionCode = 10
    versionName = "3.0"
    // Phones only (the face model ships native code for each CPU type; x86 is emulators).
    // "-Pabis=arm64-v8a" builds a smaller APK for modern phones only.
    ndk { abiFilters += ((project.findProperty("abis") as String?) ?: "arm64-v8a,armeabi-v7a").split(",") }
  }

  // Compressed native code keeps the APK small to download and send; it is unpacked once at install.
  packaging { jniLibs { useLegacyPackaging = true } }

  signingConfigs {
    // Debug builds, local and on GitHub Actions, share this key so every APK installs over the previous one.
    // It is a standard debug key (password "android"), not a Play Store key.
    getByName("debug") {
      storeFile = file("signing/jrlink-debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    // Release is signed only when KEYSTORE_PATH points to an existing keystore.
    // Without it, `assembleRelease` produces an unsigned APK and `assembleDebug` keeps working.
    val keystorePath = System.getenv("KEYSTORE_PATH")
    if (keystorePath != null && file(keystorePath).exists()) {
      create("release") {
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.service)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  // Bundled face model: works offline, no Google Play services needed.
  implementation(libs.mlkit.face.detection)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
