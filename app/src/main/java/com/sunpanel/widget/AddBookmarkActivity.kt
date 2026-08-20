package com.sunpanel.widget

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sunpanel.widget.api.SunPanelApi
import com.sunpanel.widget.data.CachedPanelData
import com.sunpanel.widget.data.ItemIcon
import com.sunpanel.widget.data.ItemIconInfo
import com.sunpanel.widget.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 收藏到 SunPanel
 * 每次分享都弹出表单，让用户确认/修改分组、标题、链接后保存
 */
class AddBookmarkActivity : ComponentActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var groupSpinner: Spinner
    private var groupIds: MutableList<Int> = mutableListOf()
    private var extractedUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        prefs = PreferencesManager.getInstance(applicationContext)

        // 解析分享数据
        parseShareIntent()

        // 显示表单（透明主题下显式设置不透明背景）
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#F2F3F5"))
        )
        setContentView(R.layout.activity_add_bookmark)

        groupSpinner = findViewById(R.id.groupSpinner)

        // 预填充表单
        val title = intent?.getStringExtra(Intent.EXTRA_SUBJECT) ?: extractTitle(getShareText())
        findViewById<EditText>(R.id.etTitle).setText(title)
        findViewById<EditText>(R.id.etUrl).setText(extractedUrl)

        autoFetchIcon()
        loadGroups()
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveBookmark() }
    }

    /** 处理 singleTask 复用 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseShareIntent()
        // 刷新表单
        val title = intent?.getStringExtra(Intent.EXTRA_SUBJECT) ?: extractTitle(getShareText())
        findViewById<EditText>(R.id.etTitle).setText(title)
        findViewById<EditText>(R.id.etUrl).setText(extractedUrl)
        autoFetchIcon()
    }

    private fun getShareText(): String = when (intent?.action) {
        Intent.ACTION_SEND -> {
            intent?.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent?.getStringExtra(Intent.EXTRA_SUBJECT)
                ?: ""
        }
        Intent.ACTION_VIEW -> intent?.data?.toString() ?: ""
        else -> ""
    }

    /** 解析分享 Intent 中的 URL */
    private fun parseShareIntent() {
        extractedUrl = extractUrl(getShareText())
    }

    private fun extractUrl(text: String): String {
        val match = Regex("https?://[^\\s\"'<>]+").find(text)
        return match?.value?.trimEnd('.', ',', ')', '】', '」', '"') ?: text.trim()
    }

    private fun extractTitle(text: String): String {
        val urlIndex = Regex("https?://").find(text)?.range?.first ?: return ""
        val title = text.substring(0, urlIndex).trim()
        return title.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
    }

    /** 自动获取 favicon */
    private fun autoFetchIcon() {
        val url = extractedUrl
        if (url.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val iconUrl = try {
                val host = java.net.URI.create(url).host ?: ""
                "https://$host/favicon.ico"
            } catch (_: Exception) { "" }
            withContext(Dispatchers.Main) {
                findViewById<EditText>(R.id.etIcon).setText(iconUrl)
            }
        }
    }

    /** 加载分组列表 */
    private fun loadGroups() {
        val serverUrl = prefs.serverUrl
        val apiToken = prefs.apiToken
        if (serverUrl.isBlank() || apiToken.isBlank()) {
            Toast.makeText(this, "请先在设置中填写 API Token", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = SunPanelApi.getService(serverUrl, apiToken)
                val resp = api.getGroupsOpenApi()
                if (resp.code == 0) {
                    val groups = resp.data?.list ?: emptyList()
                    withContext(Dispatchers.Main) {
                        groupIds = groups.map { it.itemGroupID }.toMutableList()
                        val names = groups.map { it.title }
                        val adapter = ArrayAdapter(this@AddBookmarkActivity,
                            android.R.layout.simple_spinner_item, names)
                        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
                        groupSpinner.adapter = adapter
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /** 保存书签 */
    private fun saveBookmark() {
        val serverUrl = prefs.serverUrl
        val apiToken = prefs.apiToken
        val title = findViewById<EditText>(R.id.etTitle).text.toString().trim()
        val url = findViewById<EditText>(R.id.etUrl).text.toString().trim()
        val iconUrl = findViewById<EditText>(R.id.etIcon).text.toString().trim()
        val desc = findViewById<EditText>(R.id.etDesc).text.toString().trim()

        if (serverUrl.isBlank() || apiToken.isBlank()) {
            Toast.makeText(this, "请先在设置中填写 API Token", Toast.LENGTH_LONG).show()
            return
        }
        if (title.isBlank()) { Toast.makeText(this, "请输入标题", Toast.LENGTH_SHORT).show(); return }
        if (url.isBlank() || !url.startsWith("http")) {
            Toast.makeText(this, "请输入正确的网址", Toast.LENGTH_SHORT).show(); return
        }
        if (groupIds.isEmpty()) { Toast.makeText(this, "分组未加载，请稍后再试", Toast.LENGTH_SHORT).show(); return }

        val selectedGroupId = groupIds[groupSpinner.selectedItemPosition]

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = SunPanelApi.getService(serverUrl, apiToken)
                val body = mutableMapOf<String, Any>(
                    "title" to title,
                    "url" to url,
                    "itemGroupID" to selectedGroupId
                )
                if (iconUrl.isNotBlank() && iconUrl.startsWith("http")) {
                    body["iconUrl"] = iconUrl
                }
                if (desc.isNotBlank()) {
                    body["description"] = desc
                }

                val resp = api.createBookmark(body)
                if (resp.code == 0) {
                    // 直接更新本地缓存（零网络，瞬间完成）
                    val newBookmark = ItemIconInfo(
                        title = title,
                        url = url,
                        description = desc.ifBlank { null },
                        icon = null,
                        itemIconGroupId = selectedGroupId
                    )
                    addToCacheAndRefresh(newBookmark, selectedGroupId)
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddBookmarkActivity,
                            "保存失败: ${resp.msg}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SunPanelWidget", "收藏失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddBookmarkActivity,
                        "网络异常: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }

    /** 直接更新本地缓存 + 刷新小部件 */
    private fun addToCacheAndRefresh(bookmark: ItemIconInfo, groupId: Int) {
        try {
            val cached = prefs.cachedPanelData
            val groups = cached?.groups?.toMutableList() ?: mutableListOf()
            // 找到对应的分组插入
            val targetIndex = groups.indexOfFirst { it.group.id == groupId }
            if (targetIndex >= 0) {
                val target = groups[targetIndex]
                val newBookmarks = target.bookmarks.toMutableList()
                newBookmarks.add(bookmark)
                groups[targetIndex] = target.copy(bookmarks = newBookmarks)
            } else if (groups.isNotEmpty()) {
                // 找不到分组就追加到第一个分组
                val first = groups[0]
                val newBookmarks = first.bookmarks.toMutableList()
                newBookmarks.add(bookmark)
                groups[0] = first.copy(bookmarks = newBookmarks)
            }
            prefs.cachedPanelData = CachedPanelData(groups)

            // 发广播刷新小部件
            val refreshIntent = Intent("com.sunpanel.widget.action.REFRESH").apply {
                setPackage(packageName)
            }
            sendBroadcast(refreshIntent)
        } catch (e: Exception) {
            Log.e("SunPanelWidget", "缓存更新失败", e)
        }
    }

    override fun finish() {
        overridePendingTransition(0, 0)
        super.finish()
    }
}