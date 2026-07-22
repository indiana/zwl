# ProGuard rules for Legalny Bushcraft (com.indiana.zwl)

# Keep Room DB entities & DAO
-keep class com.indiana.zwl.data.local.** { *; }
-dontwarn com.indiana.zwl.data.local.**

# Keep Data models / DTOs
-keep class com.indiana.zwl.domain.model.** { *; }

# Keep Gson annotations and serialization fields
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Mapsforge classes
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**

# Keep JTS (Java Topology Suite) spatial classes
-keep class org.locationtech.jts.** { *; }
-dontwarn org.locationtech.jts.**

# Keep Hilt / Dagger generated classes
-keep class com.google.dagger.** { *; }
-keep class dagger.** { *; }
-keep class * extends androidx.lifecycle.ViewModel
