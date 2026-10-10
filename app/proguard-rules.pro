# Apache POI, Log4j, XMLBeans & OSGi
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.osgi.framework.**
-dontwarn javax.xml.stream.**
-dontwarn java.awt.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.tukaani.xz.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class org.apache.poi.xwpf.usermodel.** { *; }
-keep class org.apache.poi.openxml4j.** { *; }
-keep class org.openxmlformats.schemas.wordprocessingml.x2006.main.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }

# PDFBox Android
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Kotlinx Serialization & Reflection
-keepattributes *Annotation*,EnclosingMethod
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class * {
    <fields>;
}
