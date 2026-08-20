package com.sunpanel.widget

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams

/**
 * 悬浮窗启动器
 * 创建 1px 不可见悬浮窗作为"前台载体"，使 startActivity 合法（Android 14+ 豁免）
 * 300ms 后自动移除，完全无感。
 */
object OverlayLauncher {

    private const val TAG = "SunPanelWidget"
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isRemoving = false

    /**
     * 通过悬浮窗启动浏览器
     * @param activity 来源 Activity（可能是 ProxyActivity）
     * @param url 要打开的 URL
     * @param customPackage 指定浏览器包名（null 则用默认）
     * @param onDone 移除完成后回调（可选，用于 finish Activity）
     */
    fun launchViaOverlay(
        activity: Activity,
        url: String,
        customPackage: String? = null,
        onDone: (() -> Unit)? = null
    ) {
        if (!url.startsWith("http")) {
            onDone?.invoke()
            return
        }

        try {
            val wm = activity.getSystemService(Activity.WINDOW_SERVICE) as WindowManager

            // 创建 1px 不可见悬浮窗
            val overlay = View(activity)
            overlay.setBackgroundColor(0x00000000) // 完全透明

            val params = LayoutParams(
                1, 1, // 1×1 像素，极小
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                LayoutParams.FLAG_NOT_TOUCHABLE or
                        LayoutParams.FLAG_NOT_FOCUSABLE or
                        LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.START or Gravity.TOP
            params.x = 0
            params.y = 0

            wm.addView(overlay, params)
            overlayView = overlay
            isRemoving = false

            Log.d(TAG, "悬浮窗已创建（1px），准备启动浏览器")

            // 构建浏览器 Intent
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (customPackage != null) {
                browserIntent.setPackage(customPackage)
                if (activity.packageManager.resolveActivity(
                        browserIntent,
                        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                    ) == null
                ) {
                    Log.d(TAG, "指定浏览器未安装，回退到默认")
                    browserIntent.setPackage(null)
                }
            }

            // 设置 Android 14+ 后台启动豁免
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                @Suppress("DEPRECATION")
                options.pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                activity.startActivity(browserIntent, options.toBundle())
            } else {
                activity.startActivity(browserIntent)
            }

            Log.d(TAG, "浏览器已启动，300ms 后移除悬浮窗")

            // 300ms 后自动移除悬浮窗
            handler.postDelayed({
                removeOverlay(wm)
                onDone?.invoke()
            }, 300)

        } catch (e: Exception) {
            Log.e(TAG, "悬浮窗启动失败，回退到直接启动", e)
            removeOverlay(null)
            // 回退：直接 startActivity
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (customPackage != null) fallback.setPackage(customPackage)
                activity.startActivity(fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "回退也失败", e2)
            }
            onDone?.invoke()
        }
    }

    private fun removeOverlay(wm: WindowManager?) {
        if (isRemoving) return
        isRemoving = true
        try {
            val view = overlayView
            if (view != null && wm != null) {
                wm.removeView(view)
                overlayView = null
                Log.d(TAG, "悬浮窗已移除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮窗失败", e)
            overlayView = null
        }
    }

    /** 清理（Activity 销毁时调用） */
    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        try {
            val view = overlayView
            if (view != null) {
                val ctx = view.context
                val wm = ctx.getSystemService(Activity.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
                overlayView = null
            }
        } catch (_: Exception) {
            overlayView = null
        }
        isRemoving = false
    }
}