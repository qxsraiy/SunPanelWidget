package com.sunpanel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.sunpanel.widget.data.PreferencesManager

/**
 * 桌面小部件 Provider
 *
 * 布局：GridView（可滑动网格，自动列数）
 * 点击：setPendingIntentTemplate(显式广播 ACTION_CLICK) + setOnClickFillInIntent(data+extras 双保险)
 *       → 系统合并后广播给自己 Provider → onReceive 中 startActivity 打开浏览器
 *       （Android 14+ 禁止 FLAG_MUTABLE + 隐式 Intent，因此用显式广播中转）
 *       （Android 14+ 后台启动 Activity 受限，onReceive 里用 ActivityOptions 豁免）
 * 图标：文字字母（无 Bitmap）
 */
@Suppress("DEPRECATION")
class SunPanelWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "SunPanelWidget"
        const val ACTION_REFRESH = "com.sunpanel.widget.action.REFRESH"
        // 自定义点击广播 Action（显式广播，绕过 Android 14+ 隐式 Intent 安全限制）
        const val ACTION_CLICK = "com.sunpanel.widget.action.CLICK"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // 空状态提示
            val prefs = PreferencesManager.getInstance(context)
            val hasData = prefs.cachedPanelData != null
            views.setViewVisibility(R.id.widgetEmptyView, if (hasData) View.GONE else View.VISIBLE)

            // ⭐ 绑定 RemoteViewsService（数据源，GridView 可滑动）
            val serviceIntent = Intent(context, SunPanelRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("sunpanel://adapter/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.widgetGrid, serviceIntent)
            views.setEmptyView(R.id.widgetGrid, R.id.widgetEmptyView)

            // ⭐ 终极方案（Android 14+ 兼容）：模板 = 发给自己 App 的【显式广播】
            // 日志实证：Targeting U+ (version 34+) disallows creating a PendingIntent
            // with FLAG_MUTABLE + implicit Intent。因此不能用隐式 ACTION_VIEW。
            // 改为显式 Intent(本 Provider 类) + getBroadcast，完美绕过限制。
            val templateIntent = Intent(context, SunPanelWidgetProvider::class.java).apply {
                action = ACTION_CLICK
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            // 显式指向自己的 Provider，FLAG_MUTABLE 合法（非隐式 Intent）
            val templatePendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, templateIntent, flags
            )
            views.setPendingIntentTemplate(R.id.widgetGrid, templatePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "updateAppWidget: widgetId=$appWidgetId 已下发（模板=显式广播 ACTION_CLICK）")
        } catch (e: Exception) {
            Log.e(TAG, "updateAppWidget 失败: widgetId=$appWidgetId", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH -> {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, SunPanelWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    if (appWidgetIds.isEmpty()) {
                        Log.d(TAG, "刷新广播：没有已添加的小部件")
                        return
                    }
                    for (id in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, id)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widgetGrid)
                    } else {
                        @Suppress("DEPRECATION")
                        for (id in appWidgetIds) {
                            appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widgetGrid)
                        }
                    }
                    Log.d(TAG, "刷新完成：${appWidgetIds.size} 个小部件")
                } catch (e: Exception) {
                    Log.e(TAG, "刷新广播处理失败", e)
                }
            }

            // ⭐ 处理点击广播：从 fill-in 的 data URI 或 extras 中读取 URL，打开浏览器
            ACTION_CLICK -> {
                // 优先从 data URI 取（FILL_IN_DATA 合并更可靠）
                var url = intent.data?.toString()
                // 备选从 extras 取（FILL_IN_EXTRAS 兜底）
                if (url.isNullOrBlank()) {
                    url = intent.getStringExtra("click_url")
                }
                Log.d(TAG, "ACTION_CLICK: url=$url")
                if (!url.isNullOrBlank() && url.startsWith("http")) {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        // ⭐ Android 14+ 后台启动 Activity 豁免参数
                        if (Build.VERSION.SDK_INT >= 34) {
                            val options = android.app.ActivityOptions.makeBasic()
                            options.pendingIntentBackgroundActivityStartMode =
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            context.startActivity(browserIntent, options.toBundle())
                        } else {
                            context.startActivity(browserIntent)
                        }
                        Log.d(TAG, "成功调起浏览器访问: $url")
                    } catch (e: Exception) {
                        Log.e(TAG, "调起浏览器失败: $url", e)
                    }
                } else {
                    Log.e(TAG, "ACTION_CLICK: URL 无效或为空")
                }
            }
        }
    }
}
