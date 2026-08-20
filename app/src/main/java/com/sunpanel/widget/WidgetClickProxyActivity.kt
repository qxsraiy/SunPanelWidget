package com.sunpanel.widget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * 透明代理 Activity
 * 专门用于接收小部件点击，再转发给系统浏览器
 * 解决 Android 14+ 广播无法在后台启动界面的问题
 */
class WidgetClickProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 获取 FillInIntent 传递过来的网址（三重保险读取）
        var url = intent.data?.toString()
        if (url.isNullOrBlank()) {
            url = intent.getStringExtra("click_url")
        }
        if (url.isNullOrBlank()) {
            val pos = intent.getIntExtra("position", -1)
            if (pos >= 0 && pos < SunPanelRemoteViewsService.sUrlCache.size) {
                url = SunPanelRemoteViewsService.sUrlCache[pos]
            }
        }

        Log.d("SunPanelWidget", "透明代理收到点击, URL: $url")

        // 2. 调起系统浏览器
        if (!url.isNullOrBlank() && url.startsWith("http")) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e("SunPanelWidget", "调起浏览器失败: $url", e)
            }
        }

        // 3. 瞬间关闭自己，实现无缝隐形中转
        finish()
    }
}