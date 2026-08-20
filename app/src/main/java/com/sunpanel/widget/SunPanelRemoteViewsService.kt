package com.sunpanel.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.sunpanel.widget.data.PreferencesManager
import com.sunpanel.widget.data.ItemIconInfo
import com.sunpanel.widget.data.CachedGroupData

/**
 * RemoteViewsService — 为 GridView 提供数据（可滑动网格，无分组标题）
 *
 * 按最原始设计文档：
 * - 每个 GridView item = 字母色块图标 + 标题
 * - fill-in data URI = 真实 URL（FILL_IN_DATA 合并，最可靠）
 * - 模板用 ACTION_VIEW，系统直接 startActivity 打开浏览器
 * - 无 Bitmap（纯文字图标，零超限风险）
 */
class SunPanelRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SunPanelRemoteViewsFactory(applicationContext, intent)
    }

    class SunPanelRemoteViewsFactory(
        private val context: Context,
        private val intent: Intent
    ) : RemoteViewsService.RemoteViewsFactory {

        private val TAG = "SunPanelWidget"
        private val appWidgetId: Int
            get() = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        private var allBookmarks: List<ItemIconInfo> = emptyList()

        private val colorPool = intArrayOf(
            0xFF4A90D9.toInt(), 0xFF50B86C.toInt(), 0xFFE67E22.toInt(),
            0xFF9B59B6.toInt(), 0xFF1ABC9C.toInt(), 0xFFE74C3C.toInt(),
            0xFF3498DB.toInt(), 0xFF2ECC71.toInt(), 0xFFF39C12.toInt(),
            0xFF8E44AD.toInt(), 0xFF16A085.toInt(), 0xFFD35400.toInt(),
            0xFF2980B9.toInt(), 0xFF27AE60.toInt(), 0xFFC0392B.toInt(),
            0xFF7F8C8D.toInt(), 0xFF5D6D7E.toInt(), 0xFF6C3483.toInt()
        )

        override fun onCreate() {}
        override fun onDestroy() {}

        override fun onDataSetChanged() {
            try {
                val prefs = PreferencesManager.getInstance(context)
                val data = prefs.cachedPanelData

                // 展开所有分组的书签为扁平列表
                allBookmarks = if (data != null) {
                    data.groups.flatMap { group -> group.bookmarks }
                } else {
                    emptyList()
                }
                Log.d(TAG, "onDataSetChanged: 加载了 ${allBookmarks.size} 个书签")
            } catch (e: Exception) {
                Log.e(TAG, "onDataSetChanged 失败", e)
                allBookmarks = emptyList()
            }
        }

        override fun getCount(): Int = allBookmarks.size

        override fun getViewTypeCount(): Int = 1

        override fun getViewAt(position: Int): RemoteViews {
            try {
                if (position < 0 || position >= allBookmarks.size) {
                    return RemoteViews(context.packageName, R.layout.widget_item)
                }

                val info = allBookmarks[position]
                val views = RemoteViews(context.packageName, R.layout.widget_item)

                // 标题
                val title = info.title.ifBlank { "未命名" }
                views.setTextViewText(R.id.widgetItemTitle, title)

                // 字母色块图标（无 Bitmap）
                val letter = title.first().uppercaseChar().toString()
                val bgColor = tryParseColor(info.icon?.backgroundColor) ?: generateColor(title)
                views.setTextViewText(R.id.widgetItemIcon, letter)
                views.setInt(R.id.widgetItemIcon, "setBackgroundColor", bgColor)

                // ⭐ 真实 URL → 塞入 fill-in 的 data URI
                // 系统合并时，FILL_IN_DATA 会覆盖模板的占位 URL，最终 startActivity(ACTION_VIEW + 真实URL)
                val targetUrl = when {
                    !info.url.isNullOrBlank() -> info.url
                    !info.lanUrl.isNullOrBlank() -> info.lanUrl
                    else -> ""
                }
                val httpUrl = if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                    targetUrl
                } else if (targetUrl.isNotBlank()) {
                    "https://$targetUrl"
                } else ""

                if (httpUrl.isNotBlank()) {
                    // ⭐ 终极方案：fill-in 只传 extras（click_url），不改 Action / Data
                    // 模板是显式广播（ACTION_CLICK），fill-in 合并后到达
                    // SunPanelWidgetProvider.onReceive → startActivity 打开浏览器
                    val fillInIntent = Intent().apply {
                        putExtra("click_url", httpUrl)
                    }
                    views.setOnClickFillInIntent(R.id.widgetItemRoot, fillInIntent)
                    views.setOnClickFillInIntent(R.id.widgetItemIcon, fillInIntent)
                    views.setOnClickFillInIntent(R.id.widgetItemTitle, fillInIntent)
                }

                return views
            } catch (e: Exception) {
                Log.e(TAG, "getViewAt($position) 失败", e)
                return RemoteViews(context.packageName, R.layout.widget_item)
            }
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false

        // ========== 颜色工具 ==========

        private fun tryParseColor(color: String?): Int? {
            if (color.isNullOrBlank()) return null
            return try { Color.parseColor(color) } catch (_: Exception) { null }
        }

        private fun generateColor(key: String): Int {
            val hash = key.hashCode()
            return colorPool[Math.abs(hash) % colorPool.size]
        }
    }
}