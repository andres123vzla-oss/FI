plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  // BLD2-08: plugin de secrets retirado (neutralizado desde SEC-10, sin función).
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.mipanelfinanciero.vhkzp"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // SEC-10/BLD2-08: ÚNICA fuente de campos BuildConfig. El antiguo plugin de secrets (que
    // inyectaba campos desde `app/.env` y provocaba duplicados como GEMINI_API_KEY) fue
    // primero neutralizado (SEC-10) y finalmente RETIRADO del build (BLD2-08).
    //
    // Fallback VACÍO seguro: no se hardcodean claves reales; si no hay valor quedan como "" y la
    // app funciona en modo manual/local.
    //
    // MARKET_API_KEY: se lee de la variable de entorno o de una propiedad de Gradle
    // (-PMARKET_API_KEY=...); nunca del código fuente. Vacía ⇒ precios manuales.
    val marketApiKey = System.getenv("MARKET_API_KEY")
      ?: (project.findProperty("MARKET_API_KEY") as String?)
      ?: ""
    buildConfigField("String", "MARKET_API_KEY", "\"$marketApiKey\"")
    // A9: se elimina GEMINI_API_KEY (campo muerto: ningún código de la app lo referencia).
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    // El build type debug usa la firma debug automática de Android (~/.android/debug.keystore),
    // por lo que no se declara ningún signingConfig manual ni se versiona un keystore.
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // Endurecimiento de release: R8 reduce y ofusca el código (dificulta ingeniería inversa) y
      // shrinkResources elimina recursos no usados. Reglas de conservación en proguard-rules.pro.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
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

// ARQ2-07: exportar los esquemas JSON de Room a app/schemas/ (versionados en git) para poder
// testear migraciones con MigrationTestHelper. Acompaña a exportSchema=true en @Database.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // Cifrado en reposo de la base de datos financiera (SQLCipher sobre el SupportSQLite de Room).
  implementation(libs.sqlcipher.android)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
