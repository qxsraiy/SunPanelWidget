package com.sunpanel.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Window
import com.sunpanel.widget.data.PreferencesManager

/**
 * 透明代理 Activity（无感启动浏览器）
 *
 * 两种模式：
 *  1. 悬浮窗模式（推荐）：已授权 SYSTEM_ALERT_WINDOW 时，
 *     创建 1px 悬浮窗作为"前台载体"，直接 startActivity，300ms 后移除，完全无感
 *  2. 透明 Activity 模式（兜底）：无权限时，用零动画透明 Activity 启动，然后立即自杀
 */
class WidgetClickProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ========== 窗口完全透明 + 零动画 ==========
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setBackgroundDrawable(ColorDrawable(0x00000000))
        @Suppress("DEPRECATION")
        window.setDimAmount(0f)
        overridePendingTransition(0, 0)

        try {
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

            Log.d("SunPanelWidget", "透明代理收到点击, URL: $url, overlay=${hasOverlayPermission(this)}")

            if (url.isNullOrBlank() || !url.startsWith("http")) {
                finish()
                return
            }

            // 2. 获取浏览器设置
            val prefs = PreferencesManager.getInstance(this)
            val customPkg = if (prefs.browserMode == 1) {
                prefs.customBrowserPackage.trim().takeIf { it.isNotBlank() }
            } else null

            // 3. 优先用悬浮窗模式（无感）
            if (hasOverlayPermission(this)) {
                OverlayLauncher.launchViaOverlay(
                    activity = this,
                    url = url,
                    customPackage = customPkg,
                    onDone = {
                        // 移除后关闭自身（无动画）
                        finishAndRemoveTask()
                    }
                )
            } else {
                // 4. 透明 Activity 兜底模式
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (customPkg != null) {
                        browserIntent.setPackage(customPkg)
                        if (packageManager.resolveActivity(
                                browserIntent,
                                PackageManager.MATCH_DEFAULT_ONLY
                            ) == null
                        ) {
                            Log.d("SunPanelWidget", "指定浏览器未安装，回退到默认")
                            browserIntent.setPackage(null)
                        }
                    }
                    // Android 14+ 豁免（虽然此刻前台，保险起见）
                    if (Build.VERSION.SDK_INT >= 34) {
                        val options = android.app.ActivityOptions.makeBasic()
                        @Suppress("DEPRECATION")
                        options.pendingIntentBackgroundActivityStartMode =
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        startActivity(browserIntent, options.toBundle())
                    } else {
                        startActivity(browserIntent)
                    }
                } catch (e: Exception) {
                    Log.e("SunPanelWidget", "调起浏览器失败: $url", e)
                    try {
                        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(fallback)
                    } catch (e2: Exception) {
                        Log.e("SunPanelWidget", "兜底也失败", e2)
                    }
                }
                finishAndRemoveTask()
            }

        } catch (e: Exception) {
            Log.e("SunPanelWidget", "代理处理异常", e)
            finishAndRemoveTask()
        }
    }

    override fun onDestroy() {
        OverlayLauncher.cleanup()
        super.onDestroy()
    }

    override fun finish() {
        overridePendingTransition(0, 0)
        super.finish()
    }

    companion object {
        /** 是否有悬浮窗权限 */
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        }

        /** 打开悬浮窗授权页 */
        fun openOverlaySettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}