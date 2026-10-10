# Add project specific ProGuard rules here.
-dontwarn org.apache.poi.**
-dontwarn com.tom_roush.pdfbox.**
-keep class org.apache.poi.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
