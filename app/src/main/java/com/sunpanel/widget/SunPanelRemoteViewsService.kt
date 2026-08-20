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
 * 实现方式与最初可点击版本完全一致：
 * - 单布局（widget_item.xml），viewTypeCount=1
 * - fill-in 设置在 widgetItemRoot/widgetItemIcon/widgetItemTitle（三个位置）
 * - fill-in 只传 extras（click_url）→ 显式广播 ACTION_CLICK → Provider 打开浏览器
 * - 分组标题也使用同一布局，仅隐藏图标和备注，不设 fill-in
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

        private var displayItems: List<WidgetDisplayItem> = emptyList()

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
                displayItems = data?.toWidgetDisplayList() ?: emptyList()
                Log.d(TAG, "onDataSetChanged: 加载了 ${displayItems.size} 项")
            } catch (e: Exception) {
                Log.e(TAG, "onDataSetChanged 失败", e)
                displayItems = emptyList()
            }
        }

        override fun getCount(): Int = displayItems.size

        // ⭐ 单布局（与最初可点击版本一致）
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

        /** 分组标题：纯文字，无卡片底、无水波 */
        private fun renderHeader(views: RemoteViews, header: WidgetDisplayItem.Header) {
            // ⭐ 统一用 tint 机制（透明色），与书签行一致，避免 GridView 复用串色
            applyCardBackground(views, android.graphics.Color.TRANSPARENT)
            views.setTextViewText(R.id.widgetItemTitle, header.groupName)
            // 隐藏图标和备注
            views.setViewVisibility(R.id.widgetItemIcon, View.GONE)
            views.setViewVisibility(R.id.widgetItemDesc, View.GONE)
            // 标题加粗加大，留出分组间距
            views.setTextViewTextSize(R.id.widgetItemTitle, android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            // 注意：不设 fill-in，点击分组标题什么都不做
        }

        /** 书签卡片：色块图标 + 名称 + 备注 + fill-in 点击 */
        private fun renderBookmark(views: RemoteViews, bookmark: WidgetDisplayItem.Bookmark) {
            val info = bookmark.item

            // 显示图标和备注
            views.setViewVisibility(R.id.widgetItemIcon, View.VISIBLE)
            views.setViewVisibility(R.id.widgetItemDesc, View.VISIBLE)

            // ⭐⭐ 卡片底色：应用用户自定义颜色 + 透明度（统一半透明）⭐⭐
            applyCardBackground(views, null)  // null = 使用用户设置

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

            // ⭐ 真实 URL → fill-in extras（与最初可点击版本完全一致）
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
                    putExtra("click_url", httpUrl)
                }
                // ⭐ 三个位置都设 fill-in（与最初可点击版本完全一致）
                views.setOnClickFillInIntent(R.id.widgetItemRoot, fillInIntent)
                views.setOnClickFillInIntent(R.id.widgetItemIcon, fillInIntent)
                views.setOnClickFillInIntent(R.id.widgetItemTitle, fillInIntent)
                // 新增：备注区域也设 fill-in，点击描述也能打开
                views.setOnClickFillInIntent(R.id.widgetItemDesc, fillInIntent)
            }
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false

        // ========== 工具 ==========

        /**
         * 统一设置卡片背景（tint 机制，所有行一致，避免 GridView 复用串色）
         * @param overrideColor 非 null 时用指定颜色（全透明等）；null 时用用户设置（cardColor + cardOpacity）
         */
        private fun applyCardBackground(views: RemoteViews, overrideColor: Int?) {
            try {
                val finalColor: Int
                if (overrideColor != null) {
                    finalColor = overrideColor
                } else {
                    val prefs = PreferencesManager.getInstance(context)
                    val baseColor = try {
                        Color.parseColor(prefs.cardColor)
                    } catch (_: Exception) {
                        Color.WHITE
                    }
                    // cardOpacity: 0-100（值越小越透明）→ alpha = opacity*255/100
                    val alpha = (prefs.cardOpacity * 255 / 100).coerceIn(0, 255)
                    finalColor = (baseColor and 0x00FFFFFF) or (alpha shl 24)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // setColorStateList 是 API 31+ 方法
                    views.setColorStateList(
                        R.id.widgetItemRoot,
                        "setBackgroundTintList",
                        android.content.res.ColorStateList.valueOf(finalColor)
                    )
                } else {
                    // 低版本(API 29-30)兜底：直接设半透明纯色（无圆角）
                    views.setInt(R.id.widgetItemRoot, "setBackgroundColor", finalColor)
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