package com.sunpanel.widget

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.sunpanel.widget.data.PreferencesManager

/**
 * 透明代理 Activity
 * 接收小部件点击，转发给浏览器
 * 支持默认浏览器/指定浏览器（回退默认）
 */
class WidgetClickProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 获取 URL（三重保险）
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

        // 2. 调起浏览器
        if (!url.isNullOrBlank() && url.startsWith("http")) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                // 根据设置选择浏览器
                val prefs = PreferencesManager.getInstance(this)
                if (prefs.browserMode == 1) {
                    val customPkg = prefs.customBrowserPackage.trim()
                    if (customPkg.isNotBlank()) {
                        browserIntent.setPackage(customPkg)
                        // 检查是否已安装，未安装则回退到默认
                        if (packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
                            Log.d("SunPanelWidget", "指定浏览器未安装，回退到默认")
                            browserIntent.setPackage(null)
                        }
                    }
                }

                startActivity(browserIntent)
            } catch (e: Exception) {
                Log.e("SunPanelWidget", "调起浏览器失败: $url", e)
                // 兜底
                try {
                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(fallback)
                } catch (e2: Exception) {
                    Log.e("SunPanelWidget", "兜底也失败", e2)
                }
            }
        }

        // 3. 关闭自己
        finish()
    }
}