# ============================================================
# SunPanel Widget - ProGuard/R8 规则
# release 混淆，debug 不混淆
# ============================================================

# ---------- 1. AppWidget 组件（系统通过反射/清单加载，不能混淆） ----------
-keep class com.sunpanel.widget.SunPanelWidgetProvider { *; }
-keep class com.sunpanel.widget.SunPanelRemoteViewsService { *; }
-keep class com.sunpanel.widget.WidgetClickProxyActivity { *; }
-keep class com.sunpanel.widget.MainActivity { *; }
-keep class com.sunpanel.widget.PanelFragment { *; }
-keep class com.sunpanel.widget.SettingsFragment { *; }
-keep class com.sunpanel.widget.AddBookmarkActivity { *; }

# ---------- 2. WebView JavaScript 接口（JS 调用，不能混淆） ----------
-keepclassmembers class com.sunpanel.widget.PanelFragment$* {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------- 3. Gson 数据模型（JSON 反序列化，不能混淆字段） ----------
-keep class com.sunpanel.widget.data.** { *; }
-keep class com.sunpanel.widget.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# ---------- 4. Retrofit API 接口（反射代理，不能混淆） ----------
-keep,allowobfuscation,allowshrinking interface com.sunpanel.widget.api.** { *; }
-keep,allowobfuscation,allowshrinking class com.sunpanel.widget.api.** { *; }
-keepattributes Exceptions, Signature, InnerClasses

# ---------- 5. 通用 keep ----------
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * {
    @com.google.gson.annotations.Expose <fields>;
}

# ---------- 6. 第三方库 ----------
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keep class kotlinx.coroutines.** { *; }

# ---------- 7. 依赖不再需要的可混淆 ----------
-dontnote com.sunpanel.widget.**