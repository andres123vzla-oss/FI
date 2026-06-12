# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================================================
# Reglas para R8 (release con isMinifyEnabled = true)
# ============================================================================

# BuildConfig: MarketDataProvider lee MARKET_API_KEY por reflexión (Class.forName).
-keep class com.example.BuildConfig { *; }

# SQLCipher: usa JNI; sus clases nativas no deben renombrarse ni eliminarse.
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**

# Room: las entidades se mapean por nombre; se conservan por seguridad
# (Room ya aporta sus propias reglas de consumidor para el código generado).
-keep class com.example.data.entity.** { *; }

# Conservar números de línea para depurar stacktraces de release (sin exponer el nombre original).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
