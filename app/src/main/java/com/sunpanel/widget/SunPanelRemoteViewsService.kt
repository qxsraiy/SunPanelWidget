package com.sunpanel.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import java.net.URL
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.sunpanel.widget.data.IconCache
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

    // ⭐⭐ 静态缓存：透明代理按 position 查 URL（第三重兜底）⭐⭐
    companion object {
        /** 由 RemoteViewsFactory.onDataSetChanged() 填充 */
        @JvmStatic var sUrlCache: List<String> = emptyList()
    }

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

                // ⭐ 填充 URL 静态缓存（透明代理第三重兑底按 position 查）
                sUrlCache = displayItems.mapNotNull { item ->
                    if (item is WidgetDisplayItem.Bookmark) {
                        val info = item.item
                        val target = when {
                            !info.url.isNullOrBlank() -> info.url
                            !info.lanUrl.isNullOrBlank() -> info.lanUrl
                            else -> ""
                        }
                        val httpUrl = if (target.startsWith("http://") || target.startsWith("https://")) target
                        else if (target.isNotBlank()) "https://$target" else ""
                        httpUrl.ifBlank { null }
                    } else null
                }

                // ⭐ 后台预加载所有图标（内存→磁盘→网络），完成后通知小部件重绘
                preloadIconsAsync()

                Log.d(TAG, "onDataSetChanged: 加载了 ${displayItems.size} 项, URL缓存 ${sUrlCache.size} 条")
            } catch (e: Exception) {
                Log.e(TAG, "onDataSetChanged 失败", e)
                displayItems = emptyList()
            }
        }

        /** 收集全部图标 URL（数据里有就用，没有就用域名 favicon.ico 兜底） */
        private fun collectIconUrls(): List<String> {
            val urls = LinkedHashSet<String>()
            for (item in displayItems) {
                if (item !is WidgetDisplayItem.Bookmark) continue
                val info = item.item
                resolveIconUrl(info)?.let { urls.add(it) }
            }
            return urls.toList()
        }

        /**
         * 解析书签的最终图标 URL：
         * 1. 数据里的 icon.src（处理相对路径 → 拼接服务器地址）
         * 2. 都没有 → 域名 favicon.ico 兜底
         */
        private fun resolveIconUrl(info: com.sunpanel.widget.data.ItemIconInfo): String? {
            val iconUrl = info.icon?.src?.takeIf { it.isNotBlank() }
            if (iconUrl != null) {
                if (iconUrl.startsWith("http://") || iconUrl.startsWith("https://")) {
                    return iconUrl
                }
                // 相对路径（如 /uploads/xxx.png）→ 拼接服务器地址
                val server = PreferencesManager.getInstance(context).serverUrl
                    .trimEnd('/')
                if (server.isNotBlank()) {
                    return server + (if (iconUrl.startsWith("/")) iconUrl else "/$iconUrl")
                }
            }
            // 兜底：域名 favicon.ico
            return buildFaviconUrl(info.url)
        }

        /** 后台加载图标（全部走 scheduleLoad 队列，下载完成后自动通知小部件刷新） */
        private fun preloadIconsAsync() {
            val urls = collectIconUrls()
            if (urls.isEmpty()) return

            val cache = IconCache.getInstance(context)
            // 设置全部下载完成回调（只设一次）
            if (cache.onAllDownloadsDone == null) {
                cache.onAllDownloadsDone = {
                    cache.onAllDownloadsDone = null
                    Log.d("SunPanelWidget", "所有图标下载完成，通知小部件刷新")
                    runCatching {
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val componentName = android.content.ComponentName(
                            context, SunPanelWidgetProvider::class.java
                        )
                        val ids = appWidgetManager.getAppWidgetIds(componentName)
                        if (ids.isNotEmpty()) {
                            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widgetGrid)
                        }
                    }
                }
            }
            // 全部丢到下载队列
            for (url in urls) {
                cache.scheduleLoad(url)
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
                    is WidgetDisplayItem.Bookmark -> renderBookmark(views, item, position)
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
            views.setViewVisibility(R.id.widgetItemIconBox, View.GONE)
            views.setViewVisibility(R.id.widgetItemIconImg, View.GONE)
            views.setViewVisibility(R.id.widgetItemIcon, View.GONE)
            views.setViewVisibility(R.id.widgetItemDesc, View.GONE)
            views.setTextViewTextSize(R.id.widgetItemTitle, android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            // ⭐ 清空 GridView 复用带来的旧点击事件，防止"幽灵点击"乱跳
            views.setOnClickFillInIntent(R.id.widgetItemRoot, Intent())
        }

        /** 书签卡片：真实图标(缓存) + 名称 + 备注 + fill-in 点击 */
        private fun renderBookmark(views: RemoteViews, bookmark: WidgetDisplayItem.Bookmark, position: Int) {
            val info = bookmark.item

            views.setViewVisibility(R.id.widgetItemIconBox, View.VISIBLE)
            views.setViewVisibility(R.id.widgetItemDesc, View.VISIBLE)
            views.setInt(R.id.widgetItemIconBox, "setBackgroundColor", 0x00000000)

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

            // ⭐ 真实图标（仅查缓存，不阻塞 binder 线程；后台 preload 线程已预下载）
            val iconUrl = resolveIconUrl(info)
            var gotIcon = false
            if (iconUrl != null) {
                try {
                    val cache = IconCache.getInstance(context)
                    val bmp = cache.peekIcon(iconUrl)
                    if (bmp != null) {
                        gotIcon = true
                        views.setViewVisibility(R.id.widgetItemIconImg, View.VISIBLE)
                        views.setViewVisibility(R.id.widgetItemIcon, View.GONE)
                        views.setImageViewBitmap(R.id.widgetItemIconImg, bmp)
                        views.setInt(R.id.widgetItemIconBox, "setBackgroundColor", 0x00FFFFFF)
                    } else {
                        // ⭐ peek 未命中 → 加入后台下载队列，下次刷新就有图标了
                        cache.scheduleLoad(iconUrl)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "加载图标失败($iconUrl): ${e.message}")
                }
            }

            // 字母兜底（无图标或下载失败）
            if (!gotIcon) {
                val letter = title.first().uppercaseChar().toString()
                val bgColor = tryParseColor(info.icon?.backgroundColor) ?: generateColor(title)
                views.setViewVisibility(R.id.widgetItemIconImg, View.GONE)
                views.setViewVisibility(R.id.widgetItemIcon, View.VISIBLE)
                views.setTextViewText(R.id.widgetItemIcon, letter)
                views.setInt(R.id.widgetItemIcon, "setBackgroundColor", bgColor)
            }

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
                    putExtra("position", position)          // 第三重兜底：透明代理按 position 从 sUrlCache 查
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

        /**
         * 构建 favicon 图标 URL（当数据里没有 icon.src 时兜底尝试）
         * 优先取网站根路径的 /favicon.ico；没有 http 前缀则解析域名
         */
        private fun buildFaviconUrl(url: String): String? {
            if (url.isBlank()) return null
            return try {
                val javaUrl = URL(url)
                val host = javaUrl.host
                if (host.isBlank()) {
                    null
                } else {
                    "https://$host/favicon.ico"
                }
            } catch (_: Exception) {
                null
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
