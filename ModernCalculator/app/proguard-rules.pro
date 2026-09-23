# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep generic signatures for reflection
-keepattributes Signature

# Keep line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
