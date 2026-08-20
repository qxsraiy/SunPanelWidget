package com.sunpanel.widget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 图标缓存：内存 + 磁盘双层缓存
 *
 * - 内存缓存：LruCache，进程内复用，避免重复解码
 * - 磁盘缓存：app cacheDir/icons/，按 URL 的 MD5 文件名持久化
 *   "只要不更换登录账号"（跨次打开重用）
 * - 失败 URL 记录：negativeCache，本次会话内不再重试
 *
 * 线程安全：设计为 getViewAt 中同步调用
 */
class IconCache(private val context: Context) {

    companion object {
        private const val TAG = "SunPanelWidget"
        private const val DISK_CACHE_DIR = "icons"
        /** 内存缓存：最多 64 个图标，~64×70KB ≈ 4.5MB */
        private const val MEM_CACHE_SIZE = 64
        /** 目标图标尺寸（dp 转 px，按 3x 密度） */
        private const val TARGET_SIZE_PX = 132
        /** 下载超时 */
        private const val TIMEOUT_MS = 3000L

        /** 工厂实例（按 Application 生命周期，单例足够） */
        @Volatile
        private var instance: IconCache? = null

        fun getInstance(context: Context): IconCache {
            return instance ?: synchronized(this) {
                instance ?: IconCache(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext: Context = context.applicationContext

    /** 内存缓存：URL -> Bitmap */
    private val memCache = object : LruCache<String, Bitmap>(MEM_CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    /** 磁盘缓存目录 */
    private val diskDir: File = File(appContext.cacheDir, DISK_CACHE_DIR).also { it.mkdirs() }

    /** 本轮会话中下载失败的 URL（不再重试） */
    private val negativeCache = mutableSetOf<String>()

    /** 等待下载的 URL 队列（peek 未命中但可能之前 preload 失败/没跑到，供按需补下） */
    @Volatile
    var pendingUrls: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())
        private set

    /** 下载线程（单线程，避免并发爆炸） */
    private val downloadExecutor = java.util.concurrent.Executors.newSingleThreadExecutor {
        Thread(it, "icon-download").apply { isDaemon = true }
    }

    /** 所有下载完成后回调（供 Service 通知小部件刷新） */
    @Volatile
    var onAllDownloadsDone: (() -> Unit)? = null

    /** 守护线程池下载用的 OkHttpClient（短超时、不跟踪重定向302） */
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * 快速查看图标（只查内存+磁盘缓存，不做网络请求）
     * 供 getViewAt 渲染时使用 —— 不阻塞 binder 线程
     * 真正的下载由后台 preload 线程完成
     */
    fun peekIcon(iconUrl: String?): Bitmap? {
        if (iconUrl.isNullOrBlank()) return null
        memCache.get(iconUrl)?.let { return it }
        if (iconUrl in negativeCache) return null

        val diskFile = diskFile(iconUrl)
        if (diskFile.exists()) {
            try {
                val bm = decodeBitmap(diskFile)
                if (bm != null) memCache.put(iconUrl, bm)
                return bm
            } catch (e: Exception) {
                Log.w(TAG, "读取磁盘缓存失败: $iconUrl", e)
            }
        }
        return null
    }

    /**
     * 获取图标 Bitmap（内存→磁盘→网络，全链路）
     * 供后台 preload 线程使用
     * @param iconUrl 图标 URL，若为 null/blank 直接返回 null
     * @return Bitmap 或 null（失败/无图标）
     */
    fun getIcon(iconUrl: String?): Bitmap? {
        if (iconUrl.isNullOrBlank()) return null

        // 1. 内存缓存
        memCache.get(iconUrl)?.let { return it }

        // 2. 负面缓存（之前下载失败过）
        if (iconUrl in negativeCache) return null

        // 3. 磁盘缓存
        val diskFile = diskFile(iconUrl)
        if (diskFile.exists()) {
            try {
                val bm = decodeBitmap(diskFile)
                if (bm != null) {
                    memCache.put(iconUrl, bm)
                    return bm
                }
            } catch (e: Exception) {
                Log.w(TAG, "读取磁盘缓存失败: $iconUrl", e)
            }
        }

        // 4. 网络下载
        try {
            val bm = downloadBitmap(iconUrl)
            if (bm != null) {
                // 写磁盘
                try {
                    FileOutputStream(diskFile).use { out ->
                        bm.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "写入磁盘缓存失败: $iconUrl", e)
                }
                // 写内存
                memCache.put(iconUrl, bm)
                return bm
            }
        } catch (e: Exception) {
            Log.w(TAG, "下载图标失败: $iconUrl", e)
        }

        // 5. 全部失败 → 加入负面缓存
        negativeCache.add(iconUrl)
        return null
    }

    /**
     * 按需调度下载（renderBookmark 中 peek 未命中时调用）
     * - 幂等：同一个 URL 只调度一次
     * - 不阻塞，立即返回
     * - 下载完成后自动写入缓存
     * - 所有调度任务完成后调用 onAllDownloadsDone
     */
    fun scheduleLoad(iconUrl: String?) {
        if (iconUrl.isNullOrBlank()) return
        if (iconUrl in negativeCache) return
        if (peekIcon(iconUrl) != null) return
        if (!pendingUrls.add(iconUrl)) return // 已在队列中

        downloadExecutor.execute {
            try {
                getIcon(iconUrl) // 同步下载+写缓存
            } catch (e: Exception) {
                Log.w(TAG, "scheduleLoad 下载失败: $iconUrl", e)
            }
            pendingUrls.remove(iconUrl)
            // 全部完成后触发回调
            if (pendingUrls.isEmpty()) {
                onAllDownloadsDone?.invoke()
            }
        }
    }

    /**
     * 清空所有缓存（用户切换账号时调用）
     */
    fun clearAll() {
        memCache.evictAll()
        negativeCache.clear()
        diskDir.listFiles()?.forEach { it.delete() }
    }

    // ========== 内部实现 ==========

    private fun diskFile(url: String): File {
        val hash = md5(url)
        return File(diskDir, "$hash.png")
    }

    private fun downloadBitmap(url: String): Bitmap? {
        // 尝试下载原始 URL
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        val body = response.body ?: run {
            response.close()
            return null
        }
        val bytes = body.bytes()
        response.close()

        // 解码并缩放到目标尺寸
        return decodeBitmap(bytes, TARGET_SIZE_PX)
    }

    /** 从文件解码，自动缩放到目标尺寸 */
    private fun decodeBitmap(file: File, targetSize: Int = TARGET_SIZE_PX): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val sampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, targetSize)

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (e: Exception) {
            null
        }
    }

    /** 从字节数组解码，缩放到目标尺寸 */
    private fun decodeBitmap(bytes: ByteArray, targetSize: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            val sampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, targetSize)

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateSampleSize(origW: Int, origH: Int, targetSize: Int): Int {
        if (origW <= 0 || origH <= 0) return 1
        var sample = 1
        while (origW / sample > targetSize && origH / sample > targetSize) {
            sample *= 2
        }
        return sample
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}