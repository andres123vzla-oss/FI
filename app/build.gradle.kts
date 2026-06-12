plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
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

    // SEC-10: ÚNICA fuente de campos BuildConfig. Estos campos se declaran SOLO aquí; el plugin
    // de secrets (bloque `secrets { }` más abajo) se neutraliza apuntándolo a un fichero de
    // propiedades inexistente para que NO emita campos al BuildConfig. Así se evita el riesgo de
    // campo duplicado (p. ej. el antiguo GEMINI_API_KEY estaba a la vez aquí y en `app/.env`,
    // lo que rompía la compilación del BuildConfig generado).
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

// SEC-10: el plugin de secrets se neutraliza a propósito. Los campos de BuildConfig se declaran
// como ÚNICA fuente en defaultConfig (arriba). Apuntar el plugin a un fichero de propiedades
// inexistente evita que emita campos (p. ej. el GEMINI_API_KEY de `app/.env`, que ya no se usa),
// previniendo BuildConfig con campos duplicados y errores de compilación.
secrets {
  propertiesFileName = "secrets.properties.unused"
  defaultPropertiesFileName = "secrets.defaults.unused"
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
