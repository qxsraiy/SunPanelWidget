package com.sunpanel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.sunpanel.widget.data.PreferencesManager
import com.sunpanel.widget.data.WidgetDisplayItem
import com.sunpanel.widget.data.toWidgetDisplayList

/**
 * 桌面小部件 Provider
 *
 * 点击机制：纯静态布局 + 独立 PendingIntent（每行一个），
 * 不依赖任何 addView/removeAllViews/ListView/fill-in 机制。
 * 兼容所有 Android 版本和所有桌面。
 *
 * 图标策略：纯文字字母（TextView + 动态背景色），无 Bitmap，
 * 避免 binder 事务超出 1MB 限制。
 */
class SunPanelWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "SunPanelWidget"

        const val ACTION_REFRESH = "com.sunpanel.widget.action.REFRESH"
        const val ACTION_OPEN_URL = "com.sunpanel.widget.action.OPEN_URL"
        const val EXTRA_URL = "com.sunpanel.widget.extra.URL"

        // ===== 静态槽位 ID（与 widget_layout.xml 中 22 个槽位一一对应）=====
        private const val SLOT_COUNT = 22

        private val slotRoot = intArrayOf(
            R.id.widgetSlot0, R.id.widgetSlot1, R.id.widgetSlot2,
            R.id.widgetSlot3, R.id.widgetSlot4, R.id.widgetSlot5,
            R.id.widgetSlot6, R.id.widgetSlot7, R.id.widgetSlot8,
            R.id.widgetSlot9, R.id.widgetSlot10, R.id.widgetSlot11,
            R.id.widgetSlot12, R.id.widgetSlot13, R.id.widgetSlot14,
            R.id.widgetSlot15, R.id.widgetSlot16, R.id.widgetSlot17,
            R.id.widgetSlot18, R.id.widgetSlot19, R.id.widgetSlot20,
            R.id.widgetSlot21
        )
        private val slotHeader = intArrayOf(
            R.id.widgetSlot0Header, R.id.widgetSlot1Header,
            R.id.widgetSlot2Header, R.id.widgetSlot3Header,
            R.id.widgetSlot4Header, R.id.widgetSlot5Header,
            R.id.widgetSlot6Header, R.id.widgetSlot7Header,
            R.id.widgetSlot8Header, R.id.widgetSlot9Header,
            R.id.widgetSlot10Header, R.id.widgetSlot11Header,
            R.id.widgetSlot12Header, R.id.widgetSlot13Header,
            R.id.widgetSlot14Header, R.id.widgetSlot15Header,
            R.id.widgetSlot16Header, R.id.widgetSlot17Header,
            R.id.widgetSlot18Header, R.id.widgetSlot19Header,
            R.id.widgetSlot20Header, R.id.widgetSlot21Header
        )
        private val slotHeaderTitle = intArrayOf(
            R.id.widgetSlot0HeaderTitle, R.id.widgetSlot1HeaderTitle,
            R.id.widgetSlot2HeaderTitle, R.id.widgetSlot3HeaderTitle,
            R.id.widgetSlot4HeaderTitle, R.id.widgetSlot5HeaderTitle,
            R.id.widgetSlot6HeaderTitle, R.id.widgetSlot7HeaderTitle,
            R.id.widgetSlot8HeaderTitle, R.id.widgetSlot9HeaderTitle,
            R.id.widgetSlot10HeaderTitle, R.id.widgetSlot11HeaderTitle,
            R.id.widgetSlot12HeaderTitle, R.id.widgetSlot13HeaderTitle,
            R.id.widgetSlot14HeaderTitle, R.id.widgetSlot15HeaderTitle,
            R.id.widgetSlot16HeaderTitle, R.id.widgetSlot17HeaderTitle,
            R.id.widgetSlot18HeaderTitle, R.id.widgetSlot19HeaderTitle,
            R.id.widgetSlot20HeaderTitle, R.id.widgetSlot21HeaderTitle
        )
        private val slotBody = intArrayOf(
            R.id.widgetSlot0Body, R.id.widgetSlot1Body,
            R.id.widgetSlot2Body, R.id.widgetSlot3Body,
            R.id.widgetSlot4Body, R.id.widgetSlot5Body,
            R.id.widgetSlot6Body, R.id.widgetSlot7Body,
            R.id.widgetSlot8Body, R.id.widgetSlot9Body,
            R.id.widgetSlot10Body, R.id.widgetSlot11Body,
            R.id.widgetSlot12Body, R.id.widgetSlot13Body,
            R.id.widgetSlot14Body, R.id.widgetSlot15Body,
            R.id.widgetSlot16Body, R.id.widgetSlot17Body,
            R.id.widgetSlot18Body, R.id.widgetSlot19Body,
            R.id.widgetSlot20Body, R.id.widgetSlot21Body
        )
        private val slotIcon = intArrayOf(
            R.id.widgetSlot0Icon, R.id.widgetSlot1Icon,
            R.id.widgetSlot2Icon, R.id.widgetSlot3Icon,
            R.id.widgetSlot4Icon, R.id.widgetSlot5Icon,
            R.id.widgetSlot6Icon, R.id.widgetSlot7Icon,
            R.id.widgetSlot8Icon, R.id.widgetSlot9Icon,
            R.id.widgetSlot10Icon, R.id.widgetSlot11Icon,
            R.id.widgetSlot12Icon, R.id.widgetSlot13Icon,
            R.id.widgetSlot14Icon, R.id.widgetSlot15Icon,
            R.id.widgetSlot16Icon, R.id.widgetSlot17Icon,
            R.id.widgetSlot18Icon, R.id.widgetSlot19Icon,
            R.id.widgetSlot20Icon, R.id.widgetSlot21Icon
        )
        private val slotTitle = intArrayOf(
            R.id.widgetSlot0Title, R.id.widgetSlot1Title,
            R.id.widgetSlot2Title, R.id.widgetSlot3Title,
            R.id.widgetSlot4Title, R.id.widgetSlot5Title,
            R.id.widgetSlot6Title, R.id.widgetSlot7Title,
            R.id.widgetSlot8Title, R.id.widgetSlot9Title,
            R.id.widgetSlot10Title, R.id.widgetSlot11Title,
            R.id.widgetSlot12Title, R.id.widgetSlot13Title,
            R.id.widgetSlot14Title, R.id.widgetSlot15Title,
            R.id.widgetSlot16Title, R.id.widgetSlot17Title,
            R.id.widgetSlot18Title, R.id.widgetSlot19Title,
            R.id.widgetSlot20Title, R.id.widgetSlot21Title
        )
        private val slotDesc = intArrayOf(
            R.id.widgetSlot0Desc, R.id.widgetSlot1Desc,
            R.id.widgetSlot2Desc, R.id.widgetSlot3Desc,
            R.id.widgetSlot4Desc, R.id.widgetSlot5Desc,
            R.id.widgetSlot6Desc, R.id.widgetSlot7Desc,
            R.id.widgetSlot8Desc, R.id.widgetSlot9Desc,
            R.id.widgetSlot10Desc, R.id.widgetSlot11Desc,
            R.id.widgetSlot12Desc, R.id.widgetSlot13Desc,
            R.id.widgetSlot14Desc, R.id.widgetSlot15Desc,
            R.id.widgetSlot16Desc, R.id.widgetSlot17Desc,
            R.id.widgetSlot18Desc, R.id.widgetSlot19Desc,
            R.id.widgetSlot20Desc, R.id.widgetSlot21Desc
        )
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
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    /**
     * 构建并下发小部件布局
     *
     * 使用静态槽位机制：22 个预定义的槽位，每个可渲染为"标题行"或"书签行"。
     * 全部通过 setViewVisibility 控制显示/隐藏，无 addView/removeAllViews。
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

            // 先隐藏所有槽位
            for (i in 0 until SLOT_COUNT) {
                views.setViewVisibility(slotRoot[i], View.GONE)
            }

            if (cachedData == null || cachedData.groups.isEmpty()) {
                views.setViewVisibility(R.id.widgetEmptyView, View.VISIBLE)
                Log.d(TAG, "updateAppWidget: widgetId=$appWidgetId 无缓存数据")
            } else {
                views.setViewVisibility(R.id.widgetEmptyView, View.GONE)

                val displayList = cachedData.toWidgetDisplayList()
                val usedSlots = minOf(displayList.size, SLOT_COUNT)

                for (i in 0 until usedSlots) {
                    val item = displayList[i]
                    views.setViewVisibility(slotRoot[i], View.VISIBLE)

                    when (item) {
                        is WidgetDisplayItem.Header -> {
                            views.setViewVisibility(slotHeader[i], View.VISIBLE)
                            views.setViewVisibility(slotBody[i], View.GONE)
                            views.setTextViewText(slotHeaderTitle[i], item.groupName)
                        }
                        is WidgetDisplayItem.Bookmark -> {
                            views.setViewVisibility(slotHeader[i], View.GONE)
                            views.setViewVisibility(slotBody[i], View.VISIBLE)
                            fillBookmarkSlot(context, views, item, appWidgetId, i)
                        }
                    }
                }

                Log.d(TAG, "updateAppWidget: widgetId=$appWidgetId 填充了 $usedSlots 个槽位")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG, "updateAppWidget: widgetId=$appWidgetId 已下发")
        } catch (e: Exception) {
            Log.e(TAG, "updateAppWidget 失败: widgetId=$appWidgetId", e)
            try {
                val fallback = RemoteViews(context.packageName, R.layout.widget_layout)
                fallback.setViewVisibility(R.id.widgetEmptyView, View.VISIBLE)
                fallback.setTextViewText(R.id.widgetEmptyView, "加载失败，请刷新")
                for (i in 0 until SLOT_COUNT) {
                    fallback.setViewVisibility(slotRoot[i], View.GONE)
                }
                appWidgetManager.updateAppWidget(appWidgetId, fallback)
            } catch (_: Exception) {}
        }
    }

    /**
     * 填充单个书签槽位
     */
    private fun fillBookmarkSlot(
        context: Context,
        views: RemoteViews,
        bookmark: WidgetDisplayItem.Bookmark,
        appWidgetId: Int,
        slotIndex: Int
    ) {
        val info = bookmark.item

        // 标题
        views.setTextViewText(slotTitle[slotIndex], info.title.ifBlank { "未命名" })

        // 描述
        if (!info.description.isNullOrBlank()) {
            views.setTextViewText(slotDesc[slotIndex], info.description)
            views.setViewVisibility(slotDesc[slotIndex], View.VISIBLE)
        } else {
            val displayUrl = if (info.url.isNotBlank()) {
                info.url.removePrefix("https://").removePrefix("http://").removePrefix("www.").take(40)
            } else ""
            if (displayUrl.isNotBlank()) {
                views.setTextViewText(slotDesc[slotIndex], displayUrl)
                views.setViewVisibility(slotDesc[slotIndex], View.VISIBLE)
            } else {
                views.setViewVisibility(slotDesc[slotIndex], View.GONE)
            }
        }

        // 字母图标
        val letter = if (info.title.isNotBlank()) {
            info.title.trim().first().uppercaseChar().toString()
        } else "?"
        val iconBgColor = parseColor(info.icon?.backgroundColor) ?: generateColor(info.title)
        views.setTextViewText(slotIcon[slotIndex], letter)
        views.setInt(slotIcon[slotIndex], "setBackgroundColor", iconBgColor)

        // 目标 URL
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

        // 独立 PendingIntent（绑定到 Body 容器）
        val requestCode = appWidgetId * 100000 + slotIndex
        val clickIntent = Intent(context, SunPanelWidgetProvider::class.java).apply {
            action = ACTION_OPEN_URL
            putExtra(EXTRA_URL, httpUrl)
            data = Uri.parse("sunpanel://click/$appWidgetId/$slotIndex")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, clickIntent, flags)
        views.setOnClickPendingIntent(slotBody[slotIndex], pendingIntent)

        Log.d(TAG, "fillBookmarkSlot[$slotIndex]: title=${info.title} url=$httpUrl")
    }

    // ========== 颜色工具 ==========

    private fun parseColor(color: String?): Int? {
        if (color.isNullOrBlank()) return null
        return try { Color.parseColor(color) } catch (_: Exception) { null }
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
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

            ACTION_OPEN_URL -> {
                var url = intent.getStringExtra(EXTRA_URL)
                if (url.isNullOrBlank()) {
                    url = intent.dataString
                }
                Log.d(TAG, "ACTION_OPEN_URL: url=$url intent=$intent")

                if (!url.isNullOrBlank()) {
                    val openUrl = if (url.startsWith("http://") || url.startsWith("https://")) url
                    else "https://$url"
                    Log.d(TAG, "打开浏览器: $openUrl")
                    openInBrowser(context, openUrl)
                } else {
                    Log.w(TAG, "ACTION_OPEN_URL 但 URL 为空，无法跳转")
                }
            }
        }
    }

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