package com.sunpanel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.sunpanel.widget.data.PreferencesManager
import com.sunpanel.widget.data.WidgetDisplayItem
import com.sunpanel.widget.data.toWidgetDisplayList

/**
 * 桌面小部件 Provider
 *
 * 职责：
 * 1. 管理小部件生命周期（添加、更新、删除）
 * 2. 接收刷新广播：强制重建整个小部件布局
 * 3. 接收书签点击广播：打开浏览器
 *
 * ⚠️ 点击机制：完全静态布局，每个书签行使用独立 PendingIntent。
 *  不依赖 ListView/GridView + fill-in 机制，彻底避免 Android 15/16 上
 *  桌面启动器不兼容 fill-in 的问题。兼容所有 Android 版本和所有桌面。
 */
class SunPanelWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "SunPanelWidget"

        /** 手动刷新广播 Action */
        const val ACTION_REFRESH = "com.sunpanel.widget.action.REFRESH"

        /** 点击书签后触发的网页跳转 Action */
        const val ACTION_OPEN_URL = "com.sunpanel.widget.action.OPEN_URL"

        /** Intent 中携带书签 URL 的 Extra Key */
        const val EXTRA_URL = "com.sunpanel.widget.extra.URL"
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
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // 用户调整小部件大小时重新构建布局
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    /**
     * 构建并下发完整的小部件布局
     *
     * 直接从缓存数据构建静态 LinearLayout：
     *   - 每个分组 → 一个标题行 (Header)
     *   - 每个书签 → 一个卡片行 (Bookmark + 独立 PendingIntent)
     *   - 使用 views.addView() 动态添加行
     */
    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val prefs = PreferencesManager.getInstance(context)
            val cachedData = prefs.cachedPanelData
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (cachedData == null || cachedData.groups.isEmpty()) {
                // 无数据 → 显示空状态提示
                views.setViewVisibility(R.id.widgetEmptyView, android.view.View.VISIBLE)
                views.removeAllViews(R.id.widgetRowContainer)
                Log.d(TAG, "updateAppWidget: widgetId=$appWidgetId 无缓存数据")
            } else {
                // 有数据 → 隐藏空状态，构建行
                views.setViewVisibility(R.id.widgetEmptyView, android.view.View.GONE)
                views.removeAllViews(R.id.widgetRowContainer)

                val displayList = cachedData.toWidgetDisplayList()
                var rowIndex = 0

                // 从 widget 尺寸估算最大行数（避免 RemoteViews 过大）
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200)
                // 估算：每行 ≈ 68dp（header 约 40dp, bookmark 约 64dp+8marjin）
                val maxRows = (minHeightDp / 56).coerceIn(3, 40)

                for (item in displayList) {
                    if (rowIndex >= maxRows) break

                    val rowViews = when (item) {
                        is WidgetDisplayItem.Header -> {
                            val headerViews = RemoteViews(context.packageName, R.layout.widget_header_item)
                            headerViews.setTextViewText(R.id.widgetHeaderTitle, item.groupName)
                            headerViews
                        }
                        is WidgetDisplayItem.Bookmark -> {
                            buildBookmarkRow(context, item, appWidgetId, rowIndex)
                        }
                    }

                    views.addView(R.id.widgetRowContainer, rowViews)
                    rowIndex++
                }

                Log.d(TAG, "updateAppWidget: widgetId=$appWidgetId 构建了 $rowIndex 行")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "updateAppWidget: widgetId=$appWidgetId 已下发")
        } catch (e: Exception) {
            Log.e(TAG, "updateAppWidget 失败: widgetId=$appWidgetId", e)
        }
    }

    /**
     * 构建书签行 RemoteViews
     *
     * 每个书签行包含：
     *   - 文字图标（首字母彩色圆角）
     *   - 标题
     *   - 描述/URL
     *   - 独立 PendingIntent（点击跳转浏览器）
     */
    private fun buildBookmarkRow(
        context: Context,
        bookmark: WidgetDisplayItem.Bookmark,
        appWidgetId: Int,
        rowIndex: Int
    ): RemoteViews {
        val info = bookmark.item
        val views = RemoteViews(context.packageName, R.layout.widget_bookmark_item)

        // 1. 标题
        views.setTextViewText(R.id.widgetItemTitle, info.title.ifBlank { "未命名" })

        // 2. 描述
        if (!info.description.isNullOrBlank()) {
            views.setTextViewText(R.id.widgetItemDesc, info.description)
            views.setViewVisibility(R.id.widgetItemDesc, android.view.View.VISIBLE)
        } else {
            // 无描述时显示 URL 简化版
            val displayUrl = if (info.url.isNotBlank()) {
                info.url.removePrefix("https://").removePrefix("http://").take(40)
            } else ""
            if (displayUrl.isNotBlank()) {
                views.setTextViewText(R.id.widgetItemDesc, displayUrl)
                views.setViewVisibility(R.id.widgetItemDesc, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widgetItemDesc, android.view.View.GONE)
            }
        }

        // 3. 图标 — 纯文字首字母
        val iconBitmap = createTextIcon(
            info.title,
            info.icon?.backgroundColor
        )
        if (iconBitmap != null) {
            views.setImageViewBitmap(R.id.widgetItemIcon, iconBitmap)
        } else {
            views.setImageViewResource(R.id.widgetItemIcon, R.drawable.ic_placeholder)
        }

        // 4. 目标 URL 兜底
        val targetUrl = when {
            !info.url.isNullOrBlank() -> info.url
            !info.lanUrl.isNullOrBlank() -> info.lanUrl
            else -> ""
        }
        val httpUrl = if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            targetUrl
        } else if (targetUrl.isNotBlank()) {
            "https://$targetUrl"
        } else {
            ""
        }

        // 5. 独立 PendingIntent
        //   使用 FLAG_IMMUTABLE（不需要 fill-in，Intent 完全自包含）
        val requestCode = appWidgetId * 100000 + rowIndex
        val clickIntent = Intent(context, SunPanelWidgetProvider::class.java).apply {
            action = ACTION_OPEN_URL
            putExtra(EXTRA_URL, httpUrl)
            data = Uri.parse("sunpanel://click/$appWidgetId/$rowIndex")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            clickIntent,
            flags
        )

        // 设置独立点击 PendingIntent
        views.setOnClickPendingIntent(R.id.widgetItemRoot, pendingIntent)
        views.setOnClickPendingIntent(R.id.widgetItemIcon, pendingIntent)
        views.setOnClickPendingIntent(R.id.widgetItemTitle, pendingIntent)

        Log.d(TAG, "buildBookmark: title=${info.title} url=$httpUrl row=$rowIndex")
        return views
    }

    // ========== 图标生成工具 ==========

    /**
     * 根据标题首字母生成彩色文字图标
     */
    private fun createTextIcon(title: String, bgColor: String?): Bitmap? {
        try {
            val text = if (title.isNotBlank()) title.first().uppercase() else "?"
            val size = 88 // 44dp * 2 for better quality
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 背景色
            val color = parseColor(bgColor) ?: generateColor(title)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
            // 圆角矩形
            val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat())
            val radius = 12f * 2
            canvas.drawRoundRect(rectF, radius, radius, paint)

            // 文字
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.WHITE
                textSize = 36f * 2
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            val x = size / 2f
            val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, x, y, textPaint)

            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "createTextIcon 失败", e)
            return null
        }
    }

    private fun parseColor(color: String?): Int? {
        if (color.isNullOrBlank()) return null
        return try {
            android.graphics.Color.parseColor(color)
        } catch (e: Exception) {
            null
        }
    }

    private val colorPool = intArrayOf(
        0xFF4A90D9.toInt(), 0xFF50B86C.toInt(), 0xFFE67E22.toInt(),
        0xFF9B59B6.toInt(), 0xFF1ABC9C.toInt(), 0xFFE74C3C.toInt(),
        0xFF3498DB.toInt(), 0xFF2ECC71.toInt(), 0xFFF39C12.toInt(),
        0xFF8E44AD.toInt(), 0xFF16A085.toInt(), 0xFFD35400.toInt(),
        0xFF2980B9.toInt(), 0xFF27AE60.toInt(), 0xFFC0392B.toInt(),
        0xFF7F8C8D.toInt(), 0xFF5D6D7E.toInt(), 0xFF6C3483.toInt()
    )

    private fun generateColor(key: String): Int {
        val hash = key.hashCode()
        return colorPool[Math.abs(hash) % colorPool.size]
    }

    // ========== 广播处理 ==========

    /**
     * 处理广播
     * - ACTION_REFRESH：强制重建所有小部件布局
     * - ACTION_OPEN_URL：打开浏览器
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            // 手动刷新：重建所有小部件布局
            ACTION_REFRESH -> {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, SunPanelWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                    if (appWidgetIds.isEmpty()) {
                        Log.d(TAG, "刷新广播：当前没有已添加的小部件")
                        return
                    }

                    for (id in appWidgetIds) {
                        updateAppWidget(context, appWidgetManager, id)
                    }
                    Log.d(TAG, "刷新完成：${appWidgetIds.size} 个小部件")
                } catch (e: Exception) {
                    Log.e(TAG, "刷新广播处理失败", e)
                }
            }

            // 点击书签 -> 打开浏览器
            ACTION_OPEN_URL -> {
                // 直接读取 EXTRA_URL（独立 PendingIntent 自带完整 URL）
                var url = intent.getStringExtra(EXTRA_URL)
                // 兜底：尝试从 data URI 取
                if (url.isNullOrBlank()) {
                    url = intent.dataString
                }
                Log.d(TAG, "ACTION_OPEN_URL: url=$url intent=$intent")

                if (!url.isNullOrBlank()) {
                    val openUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                        url
                    } else {
                        "https://$url"
                    }
                    Log.d(TAG, "打开浏览器: $openUrl")
                    openInBrowser(context, openUrl)
                } else {
                    Log.w(TAG, "ACTION_OPEN_URL 但 URL 为空，无法跳转")
                }
            }
        }
    }

    /**
     * 根据设置打开浏览器
     * - useChrome=true：优先使用 Chrome
     * - useChrome=false：使用系统默认浏览器
     */
    private fun openInBrowser(context: Context, url: String) {
        val prefs = PreferencesManager.getInstance(context)
        val useChrome = prefs.useChrome

        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            if (useChrome) {
                browserIntent.setPackage("com.android.chrome")
                if (context.packageManager.resolveActivity(browserIntent, 0) != null) {
                    context.startActivity(browserIntent)
                } else {
                    browserIntent.setPackage(null)
                    browserIntent.addCategory(Intent.CATEGORY_BROWSABLE)
                    context.startActivity(browserIntent)
                }
            } else {
                browserIntent.addCategory(Intent.CATEGORY_BROWSABLE)
                context.startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开浏览器失败: $url", e)
        }
    }
}