# ProGuard rules for Legalny Bushcraft (com.indiana.zwl)

# Keep Room DB entities & DAO
-keep class com.indiana.zwl.data.local.** { *; }
-dontwarn com.indiana.zwl.data.local.**

# Keep Data models / DTOs
-keep class com.indiana.zwl.domain.model.** { *; }

# Keep remote DTO classes used by Gson via reflection
# Without this, R8 renames/removes fields like `kod`, `features`, `properties`
# causing ClassCastException at runtime during JSON deserialization
-keep class com.indiana.zwl.data.remote.** { *; }
-dontwarn com.indiana.zwl.data.remote.**

# Keep Gson annotations and serialization fields
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep MapLibre classes
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Keep JTS (Java Topology Suite) spatial classes
-keep class org.locationtech.jts.** { *; }
-dontwarn org.locationtech.jts.**

# Keep Hilt / Dagger generated classes
-keep class com.google.dagger.** { *; }
-keep class dagger.** { *; }
-keep class * extends androidx.lifecycle.ViewModel



# Keep WorkManager workers and Hilt worker injection
-keep class com.indiana.zwl.data.sync.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.hilt.work.** { *; }

# Keep Retrofit interface annotations and DTO models
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep interface com.indiana.zwl.data.remote.** { *; }
-keep class com.indiana.zwl.data.remote.model.** { *; }
-dontwarn retrofit2.**

# Keep Room database generated implementation
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# SLF4J static binding resolver is not bundled on Android; R8 would fail on the
# missing org.slf4j.impl.StaticLoggerBinder referenced by LoggerFactory
-dontwarn org.slf4j.impl.**

