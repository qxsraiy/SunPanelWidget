package com.sunpanel.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.sunpanel.widget.data.PreferencesManager
import com.sunpanel.widget.data.WidgetDisplayItem
import com.sunpanel.widget.data.toWidgetDisplayList

/**
 * RemoteViewsService — 为 GridView 提供数据
 *
 * 关键设计（解决"该亮的不亮、该跳的不跳、乱跳"问题）：
 * - 三层布局：widgetItemRoot(fill-in) + widgetItemBg(着色层) + 内容层(水波)
 * - 着色只作用于 widgetItemBg → 永不覆盖水波
 * - Header 与无 URL 书签都显式清空 fillInIntent（清除 GridView 复用幽灵点击）
 * - fill-in 同时放 data URI + extras（双保险），只设在根视图 widgetItemRoot
 */
@Suppress("DEPRECATION")
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

        private var displayItems: List<WidgetDisplayItem> = emptyList()

        // 颜色缓存：onDataSetChanged 里读一次，避免每张卡片读 SharedPreferences 超时
        private var cachedBaseColor: Int = Color.WHITE
        private var cachedAlpha: Int = 38  // 15% → 38/255

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
                // ⭐ 颜色/透明度只读一次，缓存到成员变量
                cachedBaseColor = try {
                    Color.parseColor(prefs.cardColor)
                } catch (_: Exception) {
                    Color.WHITE
                }
                cachedAlpha = (prefs.cardOpacity * 255 / 100).coerceIn(0, 255)

                val data = prefs.cachedPanelData
                displayItems = data?.toWidgetDisplayList() ?: emptyList()
                Log.d(TAG, "onDataSetChanged: 加载了 ${displayItems.size} 项")
            } catch (e: Exception) {
                Log.e(TAG, "onDataSetChanged 失败", e)
                displayItems = emptyList()
            }
        }

        override fun getCount(): Int = displayItems.size

        // 单布局（与最初可点击版本一致）
        override fun getViewTypeCount(): Int = 1

        override fun getViewAt(position: Int): RemoteViews {
            try {
                if (position < 0 || position >= displayItems.size) {
                    return RemoteViews(context.packageName, R.layout.widget_item)
                }

                val item = displayItems[position]
                val views = RemoteViews(context.packageName, R.layout.widget_item)

                when (item) {
                    is WidgetDisplayItem.Header -> renderHeader(views, item)
                    is WidgetDisplayItem.Bookmark -> renderBookmark(views, item)
                }

                return views
            } catch (e: Exception) {
                Log.e(TAG, "getViewAt($position) 失败", e)
                return RemoteViews(context.packageName, R.layout.widget_item)
            }
        }

        /** 分组标题：纯文字，无卡片底、无水波、无点击 */
        private fun renderHeader(views: RemoteViews, header: WidgetDisplayItem.Header) {
            // ⭐ 着色层涂透明 → 不挡背景、不显示水波
            applyCardBackground(views, Color.TRANSPARENT)
            views.setTextViewText(R.id.widgetItemTitle, header.groupName)
            views.setViewVisibility(R.id.widgetItemIcon, View.GONE)
            views.setViewVisibility(R.id.widgetItemDesc, View.GONE)
            views.setTextViewTextSize(R.id.widgetItemTitle, android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            // ⭐ 清空 GridView 复用带来的旧点击事件，防止"幽灵点击"乱跳
            views.setOnClickFillInIntent(R.id.widgetItemRoot, Intent())
        }

        /** 书签卡片：色块图标 + 名称 + 备注 + fill-in 点击 */
        private fun renderBookmark(views: RemoteViews, bookmark: WidgetDisplayItem.Bookmark) {
            val info = bookmark.item

            views.setViewVisibility(R.id.widgetItemIcon, View.VISIBLE)
            views.setViewVisibility(R.id.widgetItemDesc, View.VISIBLE)

            // ⭐ 着色层应用用户自定义颜色+透明度（null = 用缓存值）
            applyCardBackground(views, null)

            // 名称
            val title = info.title.ifBlank { "未命名" }
            views.setTextViewText(R.id.widgetItemTitle, title)
            views.setTextViewTextSize(R.id.widgetItemTitle, android.util.TypedValue.COMPLEX_UNIT_SP, 15f)

            // 备注（description 优先，其次域名，再留空）
            val desc = when {
                !info.description.isNullOrBlank() -> info.description
                !info.url.isNullOrBlank() -> shortDomain(info.url)
                else -> ""
            }
            views.setTextViewText(R.id.widgetItemDesc, desc)

            // 色块图标（无 Bitmap）
            val letter = title.first().uppercaseChar().toString()
            val bgColor = tryParseColor(info.icon?.backgroundColor) ?: generateColor(title)
            views.setTextViewText(R.id.widgetItemIcon, letter)
            views.setInt(R.id.widgetItemIcon, "setBackgroundColor", bgColor)

            // ⭐ 真实 URL → fill-in（data URI + extras 双保险，只设在根视图）
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
                val fillInIntent = Intent().apply {
                    data = android.net.Uri.parse(httpUrl)   // FILL_IN_DATA 合并（更可靠）
                    putExtra("click_url", httpUrl)         // FILL_IN_EXTRAS 兜底
                }
                views.setOnClickFillInIntent(R.id.widgetItemRoot, fillInIntent)
            } else {
                // ⭐ 无 URL 的书签也清空复用旧事件，防止乱跳
                views.setOnClickFillInIntent(R.id.widgetItemRoot, Intent())
            }
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false

        // ========== 工具 ==========

        /**
         * 统一设置【背景色层 widgetItemBg】（不再作用于 root，不挡水波）
         * @param overrideColor 非 null 时用指定颜色（全透明等）；null 时用用户缓存设置
         */
        private fun applyCardBackground(views: RemoteViews, overrideColor: Int?) {
            try {
                val finalColor = if (overrideColor != null) {
                    overrideColor
                } else {
                    (cachedBaseColor and 0x00FFFFFF) or (cachedAlpha shl 24)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // API 31+：用 tint 给背景层着色，保留圆角/水波
                    views.setColorStateList(
                        R.id.widgetItemBg,
                        "setBackgroundTintList",
                        android.content.res.ColorStateList.valueOf(finalColor)
                    )
                } else {
                    // API 29-30 兜底
                    views.setInt(R.id.widgetItemBg, "setBackgroundColor", finalColor)
                }
            } catch (e: Exception) {
                Log.e(TAG, "设置卡片底色失败", e)
            }
        }

        /** 从 URL 提取域名（用于备注兜底显示） */
        private fun shortDomain(url: String): String {
            return try {
                val clean = url.trim()
                    .removePrefix("https://").removePrefix("http://")
                    .removePrefix("www.")
                clean.substringBefore("/")
            } catch (_: Exception) {
                url
            }
        }

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
