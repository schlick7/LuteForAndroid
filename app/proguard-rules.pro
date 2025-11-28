# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve JavaScript interfaces used in WebView
-keepclassmembers class com.example.luteforandroidv2.ui.read.ReadFragment$PageTurnInterface {
   public *;
}
-keepclassmembers class com.example.luteforandroidv2.ui.read.ReadFragment$AndroidInterface {
   public *;
}
-keepclassmembers class com.example.luteforandroidv2.ui.nativeread.Dictionary.DictionaryJavaScriptInterface {
   public *;
}
-keepclassmembers class com.example.luteforandroidv2.ui.nativeread.Term.TermDataInterface {
   public *;
}
-keepclassmembers class com.example.luteforandroidv2.ui.books.BookSelectionInterface {
   public *;
}

# Preserve all classes that are used in JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Added missing rules to suppress warnings
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
