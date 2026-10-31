# Keep the application entry point explicitly.
-keep class com.midoali.atari.MainActivity { *; }
# Add emulator/library-specific rules when the actual core is implemented.

# JNI entry points are looked up by name from native code.
-keep class com.midoali.atari.core.SegaCore { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
