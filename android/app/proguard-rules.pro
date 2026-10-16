-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.dasein.poryadok.**$$serializer { *; }
-keepclassmembers class com.dasein.poryadok.** {
    *** Companion;
}
-keepclasseswithmembers class com.dasein.poryadok.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# pdfbox-android: шрифты и ресурсы грузятся по имени.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**

# Читалка: методы, которые вызывает JavaScript в WebView.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
