# kotlinx.serialization keeps what it needs through its own rules.

# ML Kit finds its components by reflection, from the manifest, and makes them with
# their no-argument constructors. R8's full mode removes those unless told to keep them,
# and translation then fails with "NoSuchMethodException: CommonComponentRegistrar.<init>".
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keepclassmembers class com.google.mlkit.** { <init>(); }
