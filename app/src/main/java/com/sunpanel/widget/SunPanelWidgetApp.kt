package com.sunpanel.widget

import android.app.Application
import android.os.Build
import android.util.Log
import com.sunpanel.widget.data.PreferencesManager
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application 类
 * 初始化全局单例 + 全局崩溃捕获（便于排查设备兼容性闪退）
 */
class SunPanelWidgetApp : Application() {

    lateinit var prefsManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefsManager = PreferencesManager.getInstance(this)
        installCrashHandler()
    }

    /**
     * 安装全局未捕获异常处理器：
     * 闪退时把完整堆栈写入外部私有目录 crash_log.txt（无需权限），同时记录到 Logcat
     */
    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "写崩溃日志失败", e)
            }
            // 交给默认处理器（系统弹"XXX已停止运行"并退出）
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(thread: Thread, t: Throwable) {
        val sb = StringBuilder()
        sb.append("========== 崩溃时间: ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            .append(" ==========\n")
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" (Android ").append(Build.VERSION.RELEASE).append(", API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("线程: ").append(thread.name).append('\n')
        sb.append("异常: ").append(t.javaClass.name).append(": ").append(t.message).append("\n\n")
        sb.append("堆栈:\n")
        val sw = java.io.StringWriter()
        t.printStackTrace(java.io.PrintWriter(sw))
        sb.append(sw.toString())
        sb.append("\n链条 cause:\n")
        var cause: Throwable? = t.cause
        var depth = 0
        while (cause != null && depth < 10) {
            sb.append("Caused by: ").append(cause.javaClass.name).append(": ").append(cause.message).append('\n')
            val cw = java.io.StringWriter()
            cause.printStackTrace(java.io.PrintWriter(cw))
            sb.append(cw.toString()).append('\n')
            cause = cause.cause
            depth++
        }

        // 写入外部私有目录（无需权限）：/sdcard/Android/data/com.sunpanel.widget/files/crash_log.txt
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, "crash_log.txt")
        file.parentFile?.mkdirs()
        FileWriter(file, false).use { it.write(sb.toString()) }
        // 同时追加到 Logcat，便于 adb logcat 直接查看
        Log.e(TAG, "================================================")
        Log.e(TAG, sb.toString())
        Log.e(TAG, "崩溃日志已写入: ${file.absolutePath}")
        Log.e(TAG, "================================================")
    }

    companion object {
        private const val TAG = "SunPanelCrash"

        @Volatile
        private var instance: SunPanelWidgetApp? = null

        fun getInstance(): SunPanelWidgetApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
}