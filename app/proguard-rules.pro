# MeetingRecorder ProGuard Rules

# Keep Room entities
-keep class com.krunventures.meetingrecorder.data.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep Google API
-keep class com.google.api.** { *; }
-dontwarn com.google.api.**

# Keep Compose
-dontwarn androidx.compose.**

# Keep Service classes (STT/Summary pipeline)
-keep class com.krunventures.meetingrecorder.service.** { *; }

# Keep ViewModel
-keep class com.krunventures.meetingrecorder.viewmodel.** { *; }

# Keep Gson serialized/deserialized classes
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Keep OkHttp internals
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn javax.annotation.**

# Keep BroadcastReceiver
-keep class * extends android.content.BroadcastReceiver { *; }
