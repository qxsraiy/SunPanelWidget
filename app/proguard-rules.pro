# ============================================================
# SunPanel Widget - ProGuard/R8 规则
# release 混淆，debug 不混淆
# ============================================================

# ---------- 1. AppWidget 组件（系统通过反射/清单加载，不能混淆） ----------
-keep class com.sunpanel.widget.SunPanelWidgetProvider { *; }
-keep class com.sunpanel.widget.SunPanelRemoteViewsService { *; }
-keep class com.sunpanel.widget.SunPanelWidgetApp { *; }
-keep class com.sunpanel.widget.WidgetClickProxyActivity { *; }
-keep class com.sunpanel.widget.AddBookmarkActivity { *; }
-keep class com.sunpanel.widget.MainActivity { *; }
-keep class com.sunpanel.widget.PanelFragment { *; }
-keep class com.sunpanel.widget.SettingsFragment { *; }
-keep class com.sunpanel.widget.OverlayLauncher { *; }
-keep class com.sunpanel.widget.HsvColorPickerView {
    public <init>(...);
}

# ---------- 2. WebView JavaScript 接口 ----------
# ⭐ 关键修复：getUsername/getPassword/getServerUrl 直接定义在 PanelFragment 类本身，
#    必须保方法名，JS 才能通过名称调用
-keepclassmembers class com.sunpanel.widget.PanelFragment {
    @android.webkit.JavascriptInterface <methods>;
}

# 保底：任何类里带 @JavascriptInterface 注解的方法都保方法名
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------- 3. Gson 数据模型（JSON 反序列化，不能混淆字段） ----------
-keep class com.sunpanel.widget.data.** { *; }
-keepclassmembers class com.sunpanel.widget.data.** { *; }

# ---------- 4. Retrofit API 接口（反射代理创建，方法名不可混淆） ----------
-keep,allowobfuscation,allowshrinking interface com.sunpanel.widget.api.** { *; }
-keep class com.sunpanel.widget.api.** { *; }

# ---------- 5. 注解与泛型 ----------
-keepattributes Signature
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepattributes Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ---------- 6. Gson 注解字段 ----------
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * {
    @com.google.gson.annotations.Expose <fields>;
}

# ---------- 7. 第三方库（OkHttp/Retrofit/Gson/协程） ----------
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn javax.annotation.**
-dontwarn kotlin.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }

# ---------- 8. XML 布局中引用的自定义 View（R8 有时会误删） ----------
-keep class com.sunpanel.widget.HsvColorPickerView { *; }
-keep class com.sunpanel.widget.OverlayLauncher { *; }

# ---------- 9. AppWidget 官方 keep ----------
-keep public class * extends android.appwidget.AppWidgetProvider
-keep public class * extends android.widget.RemoteViewsService

# ---------- 10. WorkManager / Room（release 崩溃根因：WorkDatabase 通过类名反射实例化） ----------
-keep class androidx.work.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase$* { *; }
-keep class * extends androidx.room.paging.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * extends androidx.work.initializer.** { *; }
-dontwarn androidx.work.**
-dontwarn androidx.room.**

# ---------- 11. Activity（清单声明的组件保持类名） ----------
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Application { *; }
-keep public class * extends android.app.Fragment { *; }
-keep public class * extends androidx.fragment.app.Fragment { *; }

# ---------- 12. AndroidX Startup（WorkManager 自动初始化入口） ----------
-keep class androidx.startup.** { *; }
-keep class androidx.startup.InitializationProvider { *; }