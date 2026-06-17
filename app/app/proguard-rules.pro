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

# https://github.com/square/retrofit#r8--proguard
# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn com.google.re2j.**

# With R8 full mode generic signatures are stripped for classes that are not kept.
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.*
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn aQute.bnd.annotation.spi.ServiceProvider

# Keep Protobuf Lite generated classes
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Navigation Safe Args references model classes only by name (as strings) inside
# the compiled navigation graphs, so R8 can't see those usages. Keep the
# Parcelable data models used as nav arguments (UnifiedTorrent, TorrentItem,
# DownloadItem, ...) so they aren't shrunk/obfuscated away.
-keep class com.github.livingwithhippos.unchained.data.model.** implements android.os.Parcelable { *; }

# Moshi serializes enums by looking up their constants reflectively
# (EnumJsonAdapter -> Class.getField("REAL_DEBRID")). Enums aren't Parcelable, so
# the rule above doesn't cover them and R8 would rename the constants, causing a
# NoSuchFieldException at adapter construction (e.g. UnifiedTorrent's DebridService /
# UnifiedTorrentStatus). Keep the model enums' members so JSON works in release.
-keepclassmembers enum com.github.livingwithhippos.unchained.data.model.** { *; }