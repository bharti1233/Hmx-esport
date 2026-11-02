# Keep serializable domain models used by Supabase / Retrofit.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.hmx.arena.**$$serializer { *; }
-keepclassmembers class com.hmx.arena.** { *** Companion; }
-keepclasseswithmembers class com.hmx.arena.** { kotlinx.serialization.KSerializer serializer(...); }

# Supabase Realtime / Ktor
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-dontwarn org.conscrypt.**
