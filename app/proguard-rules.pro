# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.musicstats.app.**$$serializer { *; }
-keepclassmembers class com.musicstats.app.** {
    *** Companion;
}
