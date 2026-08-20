package com.sunpanel.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.sunpanel.widget.data.PreferencesManager
import com.sunpanel.widget.data.WidgetDisplayItem
import com.sunpanel.widget.data.toWidgetDisplayList
import java.io.File

/**
 * 远程视图服务（RemoteViewsService）
 *
 * 为 ListView 提供分组标题 + 书签的混合数据。
 * 使用 getViewTypeCount() = 2 区分两种行类型。
 * 图标全部使用文字首字母，不加载网络图片，保证滑动流畅。
 */
class SunPanelRemoteViewsService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return SunPanelViewsFactory(applicationContext, appWidgetId)
    }

    /**
     * 两种行类型
     */
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_BOOKMARK = 1
        private const val VIEW_TYPE_COUNT = 2
    }

    class SunPanelViewsFactory(
        private val context: Context,
        private val appWidgetId: Int
    ) : RemoteViewsService.RemoteViewsFactory {

        /** 展平的显示列表（分组标题 + 书签混合） */
        private val displayItems = mutableListOf<WidgetDisplayItem>()

        /** 图标内存缓存：首字母 -> Bitmap */
        private val iconMemoryCache = HashMap<String, Bitmap>()

        // ========== 生命周期 ==========

        override fun onCreate() {
            loadCachedData()
        }

        override fun onDataSetChanged() {
            try {
                iconMemoryCache.clear()
                loadCachedData()
            } catch (e: Exception) {
                android.util.Log.e("SunPanelWidget", "RemoteViewsFactory.onDataSetChanged 异常", e)
            }
        }

        override fun onDestroy() {
            iconMemoryCache.clear()
        }

        /**
         * 从 SharedPreferences 读取缓存数据，转换为展平列表
         */
        private fun loadCachedData() {
            displayItems.clear()
            val data = PreferencesManager.getInstance(context).cachedPanelData
            if (data != null) {
                displayItems.addAll(data.toWidgetDisplayList())
            }
        }

        // ========== 行类型 ==========

        override fun getViewTypeCount(): Int = VIEW_TYPE_COUNT

        // 注意：RemoteViewsFactory 没有 getItemViewType()！
        // 多视图类型通过 getViewAt() 返回不同布局实现，
        // Launcher 会根据 RemoteViews 的 layoutId 自动区分。

        // ========== 数据数量 ==========

        override fun getCount(): Int = displayItems.size

        // ========== 渲染每行 ==========

        override fun getViewAt(position: Int): RemoteViews {
            return try {
                val item = displayItems.getOrNull(position) ?: return RemoteViews(
                    context.packageName, R.layout.widget_bookmark_item
                )
                when (item) {
                    is WidgetDisplayItem.Header -> renderHeader(item)
                    is WidgetDisplayItem.Bookmark -> renderBookmark(item, position)
                }
            } catch (e: Exception) {
                // 异常保护：单个 item 渲染失败不拖垮整个列表
                android.util.Log.e("SunPanelWidget", "getViewAt($position) 异常", e)
                RemoteViews(context.packageName, R.layout.widget_bookmark_item).apply {
                    setTextViewText(R.id.widgetItemTitle, "加载失败")
                }
            }
        }

        /**
         * 渲染分组标题行
         *
         * ⚠️ 关键：不能设置 setOnClickFillInIntent！
         * 若设置了（即使是空 Intent），点击标题行也会触发 ListView 的
         * PendingIntent 模板，导致整个组件产生点击反馈。
         * 但 root 必须 clickable=true 来消费点击事件，
         * 防止点击穿透到 ListView 层。
         */
        private fun renderHeader(header: WidgetDisplayItem.Header): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_header_item)
            views.setTextViewText(R.id.widgetHeaderTitle, header.groupName)
            return views
        }

        /**
         * 渲染书签行
         *
         * ⚠️ 点击机制：必须用 setOnClickFillInIntent + setPendingIntentTemplate！
         *    Android 框架要求：ListView/GridView 子项只能通过 fill-in 机制响应点击，
         *    setOnClickPendingIntent 对集合子项会被框架直接忽略。
         *
         * ⚠️ 关键点：URL 双重携带
         *    1. fill-in 的 data URI ← Intent.fillIn(FILL_IN_DATA) 合并，最可靠
         *    2. fill-in 的 EXTRA_URL  ← extras 合并（部分 ROM 可能不稳）
         */
        private fun renderBookmark(bookmark: WidgetDisplayItem.Bookmark, position: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bookmark_item)
            val info = bookmark.item

            // 1. 标题
            views.setTextViewText(R.id.widgetItemTitle, info.title.ifBlank { "未命名" })

            // 2. 描述
            if (!info.description.isNullOrBlank()) {
                views.setTextViewText(R.id.widgetItemDesc, info.description)
                views.setViewVisibility(R.id.widgetItemDesc, View.VISIBLE)
            } else {
                // 无描述时显示 URL 作为辅助
                val displayUrl = if (info.url.isNotBlank()) {
                    info.url.removePrefix("https://").removePrefix("http://").take(40)
                } else ""
                if (displayUrl.isNotBlank()) {
                    views.setTextViewText(R.id.widgetItemDesc, displayUrl)
                    views.setViewVisibility(R.id.widgetItemDesc, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.widgetItemDesc, View.GONE)
                }
            }

            // 3. 图标 — 纯文字首字母，保证流畅
            val iconBitmap = createTextIcon(
                info.title,
                info.icon?.backgroundColor
            )
            if (iconBitmap != null) {
                views.setImageViewBitmap(R.id.widgetItemIcon, iconBitmap)
            } else {
                views.setImageViewResource(R.id.widgetItemIcon, R.drawable.ic_placeholder)
            }

            // 4. URL 兜底：url 为空时尝试 lanUrl
            val targetUrl = when {
                !info.url.isNullOrBlank() -> info.url
                !info.lanUrl.isNullOrBlank() -> info.lanUrl
                else -> ""
            }
            // 保证 URL 以 http(s):// 开头，作为合法 data URI
            val httpUrl = if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                targetUrl
            } else {
                "https://$targetUrl"
            }

            // 5. FillInIntent：URL 同时放 data URI + extras（双保险）
            val fillInIntent = Intent().apply {
                if (httpUrl.isNotBlank()) {
                    data = Uri.parse(httpUrl)
                    putExtra(SunPanelWidgetProvider.EXTRA_URL, httpUrl)
                }
            }
            views.setOnClickFillInIntent(R.id.widgetItemRoot, fillInIntent)
            views.setOnClickFillInIntent(R.id.widgetItemIcon, fillInIntent)
            views.setOnClickFillInIntent(R.id.widgetItemTitle, fillInIntent)

            android.util.Log.d("SunPanelWidget", "renderBookmark: title=${info.title} url=$httpUrl pos=$position")

            return views
        }

        // ========== 图标生成 ==========

        /**
         * 生成首字母文字图标 Bitmap（内存缓存，无网络请求）
         */
        private fun createTextIcon(title: String, backgroundColor: String?): Bitmap? {
            val letter = title.trim().take(1).ifBlank { "?" }.uppercase()
            val cacheKey = "$letter|$backgroundColor"

            // 内存缓存
            iconMemoryCache[cacheKey]?.let { return it }

            val bgColor = parseColor(backgroundColor) ?: getColorForLetter(letter)
            val size = 88

            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 圆角背景
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColor
            }
            canvas.drawRoundRect(
                RectF(0f, 0f, size.toFloat(), size.toFloat()),
                20f, 20f, paint
            )

            // 白色首字母
            paint.apply {
                color = Color.WHITE
                textSize = 40f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val baseline = size / 2f + (paint.descent() - paint.ascent()) / 2f - paint.descent()
            canvas.drawText(letter, size / 2f, baseline, paint)

            iconMemoryCache[cacheKey] = bitmap
            return bitmap
        }

        /** 根据首字母生成稳定颜色 */
        private fun getColorForLetter(letter: String): Int {
            val colors = listOf(
                0xFF5B86E5.toInt(), 0xFF36D1DC.toInt(), 0xFFFF6B6B.toInt(),
                0xFFF093FB.toInt(), 0xFF4FACFE.toInt(), 0xFF43E97B.toInt(),
                0xFFFA709A.toInt(), 0xFF30CFD0.toInt(), 0xFFA18CD1.toInt(),
                0xFFFBC2EB.toInt(), 0xFF84FAB0.toInt(), 0xFF8FD3F4.toInt(),
                0xFFD4FC79.toInt(), 0xFF96E6A1.toInt(), 0xFFDDA0DD.toInt(),
                0xFFA8E6CF.toInt(), 0xFFFFD3B6.toInt(), 0xFFAEC6CF.toInt()
            )
            val idx = kotlin.math.abs(letter.hashCode()) % colors.size
            return colors[idx]
        }

        private fun parseColor(colorStr: String?): Int? {
            if (colorStr.isNullOrBlank()) return null
            return try {
                Color.parseColor(colorStr)
            } catch (e: Exception) { null }
        }

        // ========== 其他必须实现的方法 ==========

        override fun getLoadingView(): RemoteViews? = null
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true
    }
}