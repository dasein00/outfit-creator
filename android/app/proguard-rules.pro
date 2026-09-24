-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.dasein.poryadok.**$$serializer { *; }
-keepclassmembers class com.dasein.poryadok.** {
    *** Companion;
}
-keepclasseswithmembers class com.dasein.poryadok.** {
    kotlinx.serialization.KSerializer serializer(...);
}
