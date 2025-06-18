# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 保留Hook入口
-keep class moe.ore.xposed.HookEntry { *; }

# 保留检测激活
-keep class moe.ore.xposed.common.** { *; }

# 保留kotlinIO核心部分
-keep class kotlinx.io.core.** { *; }

# 警告抑制
-dontwarn kotlin.Experimental$Level
-dontwarn kotlin.Experimental

# Gson
-keepclassmembers class moe.ore.xposed.hook.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 保留内部类
-keepclassmembers class * {
    static ** CREATOR;
}

-keep @interface moe.ore.xposed.base.MethodHook
-keepclassmembers class * {
    @moe.ore.xposed.base.MethodHook *;
}

-obfuscationdictionary obf-dict.txt
-classobfuscationdictionary obf-dict.txt
-packageobfuscationdictionary obf-dict.txt
-repackageclasses ''
-allowaccessmodification
-overloadaggressively
