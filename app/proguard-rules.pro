# MetaShield ProGuard / R8 Rules
-keep class org.contentauth.c2pa.** { *; }
-dontwarn org.contentauth.c2pa.**
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.drew.** { *; }
-keep class com.metashield.app.domain.model.** { *; }
