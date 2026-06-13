// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  // BLD2-08: plugin de secrets retirado por completo (estaba neutralizado desde SEC-10 y solo
  // añadía superficie de configuración; los campos de BuildConfig viven en defaultConfig).
}
