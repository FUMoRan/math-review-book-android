# Keep rules for serialization / OkHttp / Markwon.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.fumoran.mathreview.** {
    *** Companion;
}
-keepclasseswithmembers class com.fumoran.mathreview.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
