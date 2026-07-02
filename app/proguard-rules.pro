# libxposed
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# DexKit
-keep class org.luckypray.dexkit.** { *; }

# Keep the module entry
-keep class io.github.zyphoriate.hypercharge.MainModule { *; }
-keep class io.github.zyphoriate.hypercharge.receiver.ModuleReceiver { *; }
