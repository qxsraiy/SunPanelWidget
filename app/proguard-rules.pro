# Add project specific ProGuard rules here.
# 保留 Gson 使用的数据类
-keep class com.sunpanel.widget.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**