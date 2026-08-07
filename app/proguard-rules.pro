# Proguard rules for Smart Copy
# ML Kit text recognition (bundled)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# DataStore
-keep class androidx.datastore.** { *; }

# Keep our own package structure
-keep class com.github.op88.smartcopy.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# General Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
