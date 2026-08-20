package com.sunpanel.widget

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sunpanel.widget.api.SunPanelApi
import com.sunpanel.widget.data.CachedGroupData
import com.sunpanel.widget.data.CachedPanelData
import com.sunpanel.widget.data.GetListByGroupIdRequest
import com.sunpanel.widget.data.LoginRequest
import com.sunpanel.widget.data.PreferencesManager
import com.sunpanel.widget.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

/**
 * 设置页 Fragment
 * 配置 Sun-Panel 服务器地址 + 获取书签数据 + 外观自定义
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PreferencesManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        prefs = PreferencesManager.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 如果已有配置，回填到输入框
        if (prefs.isConfigured) {
            binding.etServerUrl.setText(prefs.serverUrl)
            binding.etUsername.setText(prefs.username)
            binding.etPassword.setText(prefs.password)
            binding.etApiToken.setText(prefs.apiToken)
            binding.switchUseChrome.isChecked = prefs.useChrome
            binding.tvStatus.text = "✅ 已配置，点击「保存并同步」可重新拉取数据"
        }

        initColorPalette()
        initOpacitySlider()

        // Chrome 开关变更时保存
        binding.switchUseChrome.setOnCheckedChangeListener { _, isChecked ->
            prefs.useChrome = isChecked
        }

        // 保存并同步按钮
        binding.btnSave.setOnClickListener {
            val serverUrl = binding.etServerUrl.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val apiToken = binding.etApiToken.text.toString().trim()

            if (serverUrl.isBlank()) {
                Toast.makeText(requireContext(), "请填写服务器地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (username.isBlank() && password.isBlank() && apiToken.isBlank()) {
                Toast.makeText(requireContext(), "请填写账号密码，或粘贴 API Token（二选一）", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.serverUrl = serverUrl
            prefs.username = username
            prefs.password = password
            prefs.apiToken = apiToken

            val panelTab = activity as? MainActivity
            if (username.isNotBlank() && password.isNotBlank()) {
                performLogin(serverUrl, username, password, apiToken)
            } else if (apiToken.isNotBlank()) {
                performSyncWithApiTokenOnly(serverUrl, apiToken)
            } else {
                Toast.makeText(requireContext(), "请填写账号密码，或粘贴 API Token", Toast.LENGTH_SHORT).show()
            }
        }

        // 手动刷新小部件按钮
        binding.btnRefreshWidget.setOnClickListener {
            refreshWidget()
            Toast.makeText(requireContext(), "已发送刷新指令", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== 卡片外观自定义 ==========

    private val colorPaletteColors = listOf(
        "#FFFFFF",  // 白
        "#E8E8E8",  // 极浅灰
        "#CCCCCC",  // 浅灰
        "#AAAAAA",  // 中灰
        "#808080",  // 标准灰
        "#666666",  // 深灰
        "#3D3D3D",  // 深色灰
        "#1A1A2E",  // 深蓝黑
        "#F5F0E8",  // 米白
        "#E8F4FD",  // 浅蓝
        "#E8F5E9",  // 浅绿
        "#FFF3E0",  // 浅橙
    )

    private var selectedColorIndex = 0

    private fun initColorPalette() {
        val palette = binding.colorPalette
        palette.removeAllViews()

        val savedColor = prefs.cardColor.uppercase()
        selectedColorIndex = colorPaletteColors.indexOfFirst { it.uppercase() == savedColor }
            .coerceAtLeast(0)

        val row1 = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        val row2 = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        colorPaletteColors.forEachIndexed { index, colorHex ->
            val chip = createColorChip(colorHex, index == selectedColorIndex)
            chip.setOnClickListener {
                selectedColorIndex = index
                prefs.cardColor = colorHex
                initColorPalette()
            }
            if (index < 6) row1.addView(chip) else row2.addView(chip)
        }

        palette.addView(row1)
        palette.addView(row2)
    }

    private fun createColorChip(colorHex: String, isSelected: Boolean): View {
        val size = 44
        val margin = 6
        val params = LinearLayout.LayoutParams(dpToPx(size), dpToPx(size)).apply {
            setMargins(dpToPx(margin), dpToPx(margin), dpToPx(margin), dpToPx(margin))
        }
        val view = View(requireContext()).apply { layoutParams = params }
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(8).toFloat()
            try {
                setColor(Color.parseColor(colorHex))
            } catch (_: Exception) {
                setColor(Color.WHITE)
            }
            setStroke(if (isSelected) dpToPx(3) else dpToPx(1), Color.parseColor("#666666"))
        }
        view.background = bg
        return view
    }

    private fun initOpacitySlider() {
        val savedOpacity = prefs.cardOpacity.toFloat()
        binding.opacitySlider.value = savedOpacity
        binding.tvOpacityValue.text = "${savedOpacity.toInt()}%"
        binding.opacitySlider.addOnChangeListener { _, value, _ ->
            val intVal = value.toInt()
            binding.tvOpacityValue.text = "${intVal}%"
            prefs.cardOpacity = intVal
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * requireContext().resources.displayMetrics.density).toInt()
    }

    // ========== 登录 / 同步 ==========

    private fun performLogin(serverUrl: String, username: String, password: String, apiToken: String = "") {
        binding.btnSave.isEnabled = false
        binding.tvStatus.text = "⏳ 正在登录..."

        lifecycleScope.launch {
            try {
                SunPanelApi.reset()
                val apiService = SunPanelApi.getService(serverUrl, "")

                val loginResponse = apiService.login(LoginRequest(username, password))
                if (loginResponse.code != 0 || loginResponse.data == null) {
                    val errMsg = "登录失败: ${loginResponse.msg} (code=${loginResponse.code})"
                    binding.tvStatus.text = "❌ $errMsg"
                    Toast.makeText(requireContext(), errMsg, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val sessionToken = loginResponse.data.token
                prefs.token = sessionToken
                prefs.apiToken = apiToken

                val extraInfo = if (apiToken.isNotBlank()) "（API Token 已保留，用于后续添加书签）" else ""
                binding.tvStatus.text = "✅ 登录成功，正在获取书签数据...$extraInfo"

                SunPanelApi.reset()
                val authApi = SunPanelApi.getService(serverUrl, sessionToken)
                syncPanelData(authApi)

            } catch (e: Exception) {
                binding.tvStatus.text = "❌ 网络错误: ${e.localizedMessage ?: "未知错误"}"
                Toast.makeText(requireContext(), "网络错误: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }

    private fun performSyncWithApiTokenOnly(serverUrl: String, apiToken: String) {
        binding.btnSave.isEnabled = false
        binding.tvStatus.text = "⏳ 正在使用 API Token 同步分组..."

        lifecycleScope.launch {
            try {
                SunPanelApi.reset()
                val authApi = SunPanelApi.getService(serverUrl, apiToken)

                val openApiResponse = authApi.getGroupsOpenApi()
                if (openApiResponse.code == 0 && openApiResponse.data != null) {
                    val groups = openApiResponse.data.list.map { it.toItemIconGroup() }
                    val statusMsg = "" +
                        "✅ 获取到 ${groups.size} 个分组\n" +
                        "⚠️ 书签读取需要登录会话 token，API Token 不适用。\n" +
                        "请在下方「账号」「密码」字段填写管理账号，再点保存。"
                    binding.tvStatus.text = statusMsg
                    Toast.makeText(requireContext(),
                        "分组已获取，但书签读取需要账号密码登录，请一并填写账号密码",
                        Toast.LENGTH_LONG).show()
                } else {
                    binding.tvStatus.text = "❌ API Token 验证失败: ${openApiResponse.msg}"
                }
            } catch (e: Exception) {
                binding.tvStatus.text = "❌ 网络错误: ${e.localizedMessage ?: "未知错误"}"
            } finally {
                binding.btnSave.isEnabled = true
            }
        }
    }

    private suspend fun syncPanelData(authApi: com.sunpanel.widget.api.SunPanelApiService) {
        var groups = emptyList<com.sunpanel.widget.data.ItemIconGroup>()

        // === 第一步：获取分组列表 ===
        try {
            val groupsResponse = authApi.getGroups()
            if (groupsResponse.code == 0 && groupsResponse.data != null) {
                groups = groupsResponse.data.list
                binding.tvStatus.text = "✅ 获取到 ${groups.size} 个分组，正在拉取书签..."
            } else {
                binding.tvStatus.text = "⚠️ 内部接口失败(code=${groupsResponse.code})，尝试 OpenAPI..."
                val openApiResponse = authApi.getGroupsOpenApi()
                if (openApiResponse.code == 0 && openApiResponse.data != null) {
                    groups = openApiResponse.data.list.map { it.toItemIconGroup() }
                    binding.tvStatus.text = "✅ OpenAPI 获取到 ${groups.size} 个分组，正在拉取书签..."
                } else {
                    val errMsg = "获取分组失败\n" +
                        "内部接口: ${groupsResponse.msg} (code=${groupsResponse.code})\n" +
                        "OpenAPI: ${openApiResponse.msg} (code=${openApiResponse.code})"
                    binding.tvStatus.text = "❌ $errMsg"
                    Toast.makeText(requireContext(), "获取分组失败，请查看底部详细错误", Toast.LENGTH_LONG).show()
                    return
                }
            }
        } catch (e: Exception) {
            binding.tvStatus.text = "❌ 获取分组异常: ${e.localizedMessage}"
            return
        }

        // === 第二步：遍历每个分组获取书签 ===
        val cachedGroups = mutableListOf<CachedGroupData>()
        var failCount = 0
        val errorMessages = mutableListOf<String>()
        var isTokenError = false

        for (group in groups) {
            try {
                val bookmarksResponse = authApi.getBookmarksByGroup(
                    GetListByGroupIdRequest(group.id)
                )
                val bookmarks = if (bookmarksResponse.code == 0 && bookmarksResponse.data != null) {
                    bookmarksResponse.data.list
                } else {
                    failCount++
                    if (bookmarksResponse.code != 0) {
                        errorMessages.add("分组[${group.title}]: code=${bookmarksResponse.code} msg=${bookmarksResponse.msg}")
                        if (bookmarksResponse.code in listOf(1000, 1001, 1100)) {
                            isTokenError = true
                        }
                    }
                    emptyList()
                }
                cachedGroups.add(CachedGroupData(group, bookmarks))
            } catch (e: Exception) {
                failCount++
                errorMessages.add("分组[${group.title}]: 异常 ${e.localizedMessage}")
                cachedGroups.add(CachedGroupData(group, emptyList()))
            }
        }

        val statusMsg = if (failCount > 0) {
            val errorDetail = if (errorMessages.isNotEmpty()) {
                "\n" + errorMessages.take(3).joinToString("\n")
            } else ""
            val tokenHint = if (isTokenError) {
                "\n\n⚠️ 书签读取需要用「账号+密码」登录（读取的会话token），\n" +
                "API Token 保留用于后续的添加/编辑书签（写操作）"
            } else ""
            "⚠️ ${failCount}/${groups.size} 个分组获取书签失败$errorDetail$tokenHint"
        } else {
            "✅ 同步完成！${groups.size} 个分组，${cachedGroups.sumOf { it.bookmarks.size }} 个书签"
        }
        binding.tvStatus.text = statusMsg

        prefs.cachedPanelData = CachedPanelData(cachedGroups)

        Toast.makeText(
            requireContext(),
            "配置成功！请到桌面长按空白处添加小部件",
            Toast.LENGTH_LONG
        ).show()
        refreshWidget()
    }

    /** 发送广播通知桌面小部件刷新数据 */
    private fun refreshWidget() {
        val intent = Intent(requireContext(), SunPanelWidgetProvider::class.java).apply {
            action = SunPanelWidgetProvider.ACTION_REFRESH
        }
        requireContext().sendBroadcast(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}