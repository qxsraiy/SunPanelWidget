package com.sunpanel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.sunpanel.widget.data.PreferencesManager

/**
 * 桌面小部件 Provider
 *
 * 布局：GridView（可滑动网格，自动列数）
 * 点击：setPendingIntentTemplate(getActivity 指向透明代理) + setOnClickFillInIntent(data+extras+position)
 *       → 点击时直接启动 WidgetClickProxyActivity（前台合法交互，不受 Android 14+ 后台启动限制）
 *       → 透明代理调起浏览器 → 瞬间关闭，用户无感知
 * 图标：文字字母（无 Bitmap）
 */
@Suppress("DEPRECATION")
class SunPanelWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "SunPanelWidget"
        const val ACTION_REFRESH = "com.sunpanel.widget.action.REFRESH"
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

            // ⭐ 终极方案：模板 = getActivity 指向透明代理 Activity
            // 必须给模板加一个任意的 Action，否则底层合并时会丢弃子项的 url
            val templateIntent = Intent(context, WidgetClickProxyActivity::class.java).apply {
                // 随意指定一个 action，让系统知道这是一个"可以接纳填空"的意图
                action = Intent.ACTION_VIEW
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            // 显式指向透明代理 Activity，FLAG_MUTABLE 合法（非隐式 Intent）
            val templatePendingIntent = PendingIntent.getActivity(
                context, appWidgetId, templateIntent, flags
            )
            views.setPendingIntentTemplate(R.id.widgetGrid, templatePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "updateAppWidget: widgetId=$appWidgetId 已下发（模板=透明代理 getActivity）")
        } catch (e: Exception) {
            Log.e(TAG, "updateAppWidget 失败: widgetId=$appWidgetId", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // 仅处理刷新广播；点击已交给透明代理 WidgetClickProxyActivity 处理
        if (intent.action == ACTION_REFRESH) {
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
    }
}