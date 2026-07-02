# BragBuddy ProGuard rules.
# R8/shrinking is currently disabled in the release build type (see app/build.gradle.kts),
# so these rules are a placeholder for when minification is re-enabled.

# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <methods>; }
