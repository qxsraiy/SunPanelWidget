package com.sunpanel.widget.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

/**
 * 本地持久化存储
 * 保存服务器配置、Token、缓存的书签数据
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // === 服务器配置 ===

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    // === API Token（OpenAPI 应用生成的 token，可选）===

    var apiToken: String
        get() = prefs.getString(KEY_API_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_TOKEN, value).apply()

    // === Token（登录后返回的 token）===

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    // === 是否已配置 ===

    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && (token.isNotBlank() || apiToken.isNotBlank())

    // === 缓存的书签数据 (JSON) ===

    private var cachedDataJson: String
        get() = prefs.getString(KEY_CACHED_DATA, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CACHED_DATA, value).apply()

    var cachedPanelData: CachedPanelData?
        get() {
            val json = cachedDataJson
            if (json.isBlank()) return null
            return try {
                gson.fromJson(json, CachedPanelData::class.java)
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            cachedDataJson = if (value != null) gson.toJson(value) else ""
        }

    // === 浏览器选择：默认浏览器 vs 指定浏览器 ===

    /**
     * 打开方式：
     * 0 = 默认浏览器
     * 1 = 指定浏览器（customBrowserPackage）
     */
    var browserMode: Int
        get() = prefs.getInt(KEY_BROWSER_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_BROWSER_MODE, value.coerceIn(0, 1)).apply()

    var customBrowserPackage: String
        get() = prefs.getString(KEY_CUSTOM_BROWSER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_BROWSER, value).apply()

    // ⚠️ 兼容旧版：字段名保留，逻辑迁移到 browserMode
    var useChrome: Boolean
        get() = browserMode == 1 && customBrowserPackage == "com.android.chrome"
        set(value) {
            if (value) {
                browserMode = 1
                customBrowserPackage = "com.android.chrome"
            } else {
                browserMode = 0
            }
        }

    // === 卡片外观自定义 ===

    /**
     * 卡片底色（十六进制，如 "#808080" 灰色）
     * 默认：白色（#FFFFFF）
     */
    var cardColor: String
        get() = prefs.getString(KEY_CARD_COLOR, "#FFFFFF") ?: "#FFFFFF"
        set(value) = prefs.edit().putString(KEY_CARD_COLOR, value).apply()

    /**
     * 卡片透明度（0-100，值越小越透明）
     * 默认：15（约 15% 不透明度）
     */
    var cardOpacity: Int
        get() = prefs.getInt(KEY_CARD_OPACITY, 15)
            .coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_CARD_OPACITY, value.coerceIn(0, 100)).apply()

    // === 分享收藏：默认分组（上次使用的分组） ===

    var lastUsedGroupId: Int
        get() = prefs.getInt(KEY_LAST_GROUP_ID, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_GROUP_ID, value).apply()

    var lastUsedGroupName: String
        get() = prefs.getString(KEY_LAST_GROUP_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_GROUP_NAME, value).apply()

    // 缓存的分组列表 JSON（分享收藏时免网络加载）
    private var cachedGroupsJson: String
        get() = prefs.getString(KEY_CACHED_GROUPS, "[]") ?: "[]"
        set(value) = prefs.edit().putString(KEY_CACHED_GROUPS, value).apply()

    data class CachedGroupItem(val id: Int, val name: String)

    var cachedGroupList: List<CachedGroupItem>
        get() {
            val json = cachedGroupsJson
            return try {
                gson.fromJson(json, Array<CachedGroupItem>::class.java).toList()
            } catch (_: Exception) { emptyList() }
        }
        set(value) {
            cachedGroupsJson = gson.toJson(value)
        }

    fun getWidgetPage(widgetId: Int): Int {
        return prefs.getInt("widget_page_$widgetId", 0)
    }

    fun setWidgetPage(widgetId: Int, page: Int) {
        prefs.edit().putInt("widget_page_$widgetId", page).apply()
    }

    // === 缓存的书签图标Bitmap (以Base64形式存储) ===
    // 为了性能，图标建议存为文件，这里用Map管理路径
    // 实际运行时图标缓存到内部缓存目录

    companion object {
        private const val PREFS_NAME = "sunpanel_widget_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_TOKEN = "token"
        private const val KEY_CACHED_DATA = "cached_data"
        private const val KEY_BROWSER_MODE = "browser_mode"
        private const val KEY_CUSTOM_BROWSER = "custom_browser"
        private const val KEY_USE_CHROME = "use_chrome"
        private const val KEY_CARD_COLOR = "card_color"
        private const val KEY_CARD_OPACITY = "card_opacity"
        private const val KEY_LAST_GROUP_ID = "last_group_id"
        private const val KEY_LAST_GROUP_NAME = "last_group_name"
        private const val KEY_CACHED_GROUPS = "cached_groups"

        // 单例
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}