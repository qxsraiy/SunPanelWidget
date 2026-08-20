package com.sunpanel.widget

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sunpanel.widget.api.SunPanelApi
import com.sunpanel.widget.data.*
import com.sunpanel.widget.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

/**
 * 设置页 Fragment
 * 四个设置项卡片 → 点击弹出详细配置对话框
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
        updateColorSummary()
        binding.itemLogin.setOnClickListener { showLoginDialog() }
        binding.itemBrowser.setOnClickListener { showBrowserDialog() }
        binding.itemColor.setOnClickListener { showColorDialog() }
        binding.itemRefresh.setOnClickListener { showRefreshDialog() }
    }

    // ========== ① 账号登录弹框 ==========

    private fun showLoginDialog() {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
        }

        // 状态
        val tvStatus = TextView(context).apply {
            text = if (prefs.isConfigured) "已配置 ✅" else "未配置"
            setTextColor(Color.parseColor("#6B7280"))
            textSize = 13f
        }
        root.addView(tvStatus)

        // 服务器地址
        root.addView(createLabel(context, "服务器地址"))
        val etServer = TextInputEditText(context).apply {
            setText(prefs.serverUrl)
            hint = "http://192.168.1.100:3002"
            setSingleLine()
        }
        root.addView(wrapInput(context, etServer))

        // 账号
        root.addView(createLabel(context, "账号"))
        val etUser = TextInputEditText(context).apply {
            setText(prefs.username)
            setSingleLine()
        }
        root.addView(wrapInput(context, etUser))

        // 密码
        root.addView(createLabel(context, "密码"))
        val etPass = TextInputEditText(context).apply {
            setText(prefs.password)
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(wrapInput(context, etPass))

        // API Token
        root.addView(createLabel(context, "API Token（可选，用于后续新增书签）"))
        val etApi = TextInputEditText(context).apply {
            setText(prefs.apiToken)
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(wrapInput(context, etApi))

        // 登录并同步按钮
        val btnLogin = MaterialButton(context).apply {
            text = "登录并同步"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 48.dpToPx(context)
            ).apply { topMargin = 16.dpToPx(context) }
            setOnClickListener {
                val server = etServer.text.toString().trim()
                val user = etUser.text.toString().trim()
                val pass = etPass.text.toString().trim()
                val api = etApi.text.toString().trim()
                if (server.isBlank()) { toast("请填写服务器地址"); return@setOnClickListener }
                prefs.serverUrl = server; prefs.username = user; prefs.password = pass; prefs.apiToken = api
                if (user.isNotBlank() && pass.isNotBlank()) {
                    performLogin(server, user, pass, api, tvStatus)
                } else if (api.isNotBlank()) {
                    performSyncWithApiTokenOnly(server, api, tvStatus)
                } else { toast("请填写账号密码或 API Token") }
            }
        }
        root.addView(btnLogin)

        // 刷新状态
        val tvHint = TextView(context).apply {
            text = "点击「登录并同步」后自动保存配置并刷新小部件"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dpToPx(context) }
        }
        root.addView(tvHint)

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog)
            .setTitle("账号登录")
            .setView(root)
            .setPositiveButton("关闭", null)
            .create()
        dialog.show()
        styleDialog(dialog)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // ========== ② 打开方式弹框 ==========

    private fun showBrowserDialog() {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 8)
        }

        val radioGroup = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
        val radioDefault = RadioButton(context).apply {
            text = "默认浏览器"
            id = 1
            isChecked = prefs.browserMode == 0
        }
        val radioCustom = RadioButton(context).apply {
            text = "指定浏览器"
            id = 2
            isChecked = prefs.browserMode == 1
        }
        radioGroup.addView(radioDefault)
        radioGroup.addView(radioCustom)
        root.addView(radioGroup)

        val layoutCustom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (prefs.browserMode == 1) View.VISIBLE else View.GONE
        }
        root.addView(layoutCustom)

        val etPkg = TextInputEditText(context).apply {
            setText(prefs.customBrowserPackage)
            hint = "com.android.chrome"
            setSingleLine()
        }
        layoutCustom.addView(createLabel(context, "浏览器包名"))
        layoutCustom.addView(wrapInput(context, etPkg))

        val tvHint = TextView(context).apply {
            text = "未安装指定浏览器时自动回退到默认浏览器"
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8.dpToPx(context) }
        }
        root.addView(tvHint)

        radioGroup.setOnCheckedChangeListener { _, id ->
            layoutCustom.visibility = if (id == 2) View.VISIBLE else View.GONE
        }

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog)
            .setTitle("打开方式")
            .setView(root)
            .setPositiveButton("保存") { _, _ ->
                prefs.browserMode = if (radioCustom.isChecked) 1 else 0
                prefs.customBrowserPackage = etPkg.text.toString().trim()
                toast("打开方式已保存")
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        styleDialog(dialog)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // ========== ③ 颜色配置弹框（专业取色器） ==========

    private fun showColorDialog() {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }

        // 取色器 View
        val picker = HsvColorPickerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 320.dpToPx(context)
            )
        }
        // 设置当前颜色
        try { picker.setColor(Color.parseColor(prefs.cardColor)) } catch (_: Exception) {}
        root.addView(picker)

        // 预览行
        val previewRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dpToPx(context) }
        }
        // 预览色块
        val preview = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(context), 48.dpToPx(context))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8.dpToPx(context).toFloat()
                try { setColor(Color.parseColor(prefs.cardColor)) } catch (_: Exception) { setColor(Color.WHITE) }
            }
        }
        previewRow.addView(preview)
        // 十六进制文字
        val tvHex = TextView(context).apply {
            text = prefs.cardColor
            setTextColor(Color.parseColor("#1F2937"))
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 12.dpToPx(context) }
        }
        previewRow.addView(tvHex)
        root.addView(previewRow)

        // 透明度
        root.addView(createLabel(context, "卡片不透明度"))
        val opacityRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val opacitySlider = Slider(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            valueFrom = 0f; valueTo = 100f; stepSize = 5f
            value = prefs.cardOpacity.toFloat()
        }
        val tvOpacity = TextView(context).apply {
            text = "${prefs.cardOpacity}%"
            setTextColor(Color.parseColor("#1F2937"))
            textSize = 14f
            minWidth = 44.dpToPx(context)
            gravity = android.view.Gravity.CENTER
        }
        opacityRow.addView(opacitySlider)
        opacityRow.addView(tvOpacity)
        root.addView(opacityRow)

        // 实时更新
        picker.onColorChanged = { color ->
            val hex = String.format("#%06X", 0xFFFFFF and color)
            tvHex.text = hex
            (preview.background as? GradientDrawable)?.setColor(color)
        }
        opacitySlider.addOnChangeListener { _, value, _ ->
            tvOpacity.text = "${value.toInt()}%"
        }

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog)
            .setTitle("颜色配置")
            .setView(root)
            .setPositiveButton("保存") { _, _ ->
                prefs.cardColor = tvHex.text.toString()
                prefs.cardOpacity = opacitySlider.value.toInt()
                updateColorSummary()
                toast("颜色已保存，请刷新桌面小部件")
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        styleDialog(dialog)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // ========== ④ 刷新小组件弹框 ==========

    private fun showRefreshDialog() {
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Material_Light_Dialog)
            .setTitle("刷新小组件")
            .setMessage("将使用最新配置刷新桌面小组件，\n如果刚刚修改了颜色/透明度，请先保存再刷新。")
            .setPositiveButton("立即刷新") { _, _ ->
                refreshWidget()
                toast("已发送刷新指令")
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        styleDialog(dialog)
    }

    // ========== 登录/同步逻辑 ==========

    private fun performLogin(serverUrl: String, username: String, password: String, apiToken: String, tvStatus: TextView) {
        tvStatus.text = "正在登录..."
        lifecycleScope.launch {
            try {
                SunPanelApi.reset()
                val apiService = SunPanelApi.getService(serverUrl, "")
                val loginResponse = apiService.login(LoginRequest(username, password))
                if (loginResponse.code != 0 || loginResponse.data == null) {
                    tvStatus.text = "❌ 登录失败: ${loginResponse.msg}"
                    return@launch
                }
                prefs.token = loginResponse.data.token
                tvStatus.text = "✅ 登录成功，获取书签中..."
                SunPanelApi.reset()
                val authApi = SunPanelApi.getService(serverUrl, prefs.token)
                syncPanelData(authApi, tvStatus)
            } catch (e: Exception) {
                tvStatus.text = "❌ 网络错误: ${e.localizedMessage ?: "未知错误"}"
            }
        }
    }

    private fun performSyncWithApiTokenOnly(serverUrl: String, apiToken: String, tvStatus: TextView) {
        tvStatus.text = "正在同步..."
        lifecycleScope.launch {
            try {
                SunPanelApi.reset()
                val authApi = SunPanelApi.getService(serverUrl, apiToken)
                val resp = authApi.getGroupsOpenApi()
                if (resp.code == 0 && resp.data != null) {
                    tvStatus.text = "✅ 获取到 ${resp.data.list.size} 个分组\n⚠️ 书签需账号密码"
                } else {
                    tvStatus.text = "❌ API Token 验证失败"
                }
            } catch (e: Exception) {
                tvStatus.text = "❌ 网络错误"
            }
        }
    }

    private suspend fun syncPanelData(authApi: com.sunpanel.widget.api.SunPanelApiService, tvStatus: TextView) {
        var groups = emptyList<com.sunpanel.widget.data.ItemIconGroup>()
        try {
            val grp = authApi.getGroups()
            if (grp.code == 0 && grp.data != null) groups = grp.data.list
            else {
                val oa = authApi.getGroupsOpenApi()
                if (oa.code == 0 && oa.data != null) groups = oa.data.list.map { it.toItemIconGroup() }
            }
        } catch (e: Exception) {
            tvStatus.text = "❌ 获取分组失败"
            return
        }
        if (groups.isEmpty()) { tvStatus.text = "⚠️ 未获取到分组"; return }

        tvStatus.text = "正在拉取 ${groups.size} 个分组书签..."
        val cachedGroups = mutableListOf<CachedGroupData>()
        var fail = 0
        for (group in groups) {
            try {
                val bm = authApi.getBookmarksByGroup(GetListByGroupIdRequest(group.id))
                val list = if (bm.code == 0 && bm.data != null) bm.data.list else { fail++; emptyList() }
                cachedGroups.add(CachedGroupData(group, list))
            } catch (_: Exception) { fail++; cachedGroups.add(CachedGroupData(group, emptyList())) }
        }
        val total = cachedGroups.sumOf { it.bookmarks.size }
        tvStatus.text = if (fail > 0) "⚠️ $fail 个分组失败，共 $total 个书签"
        else "✅ 同步完成！$total 个书签"
        prefs.cachedPanelData = CachedPanelData(cachedGroups)
        refreshWidget()
        toast("配置已保存，请刷新桌面小部件")
        (activity as? MainActivity)?.refreshPanel()
    }

    private fun refreshWidget() {
        requireContext().sendBroadcast(
            android.content.Intent(requireContext(), SunPanelWidgetProvider::class.java).apply {
                action = SunPanelWidgetProvider.ACTION_REFRESH
            }
        )
    }

    private fun updateColorSummary() {
        binding.tvColorSummary.text = "卡片底色 / ${prefs.cardOpacity}%"
    }

    /** 统一弹框样式：圆角 + 磨砂半透明背景 + 遮罩淡入 */
    private fun styleDialog(dialog: AlertDialog) {
        dialog.window?.apply {
            setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
            setDimAmount(0.3f)
        }
        // 按钮文字颜色（Dialog 默认可能白色，此处显式设置为深色）
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#1F2937"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#6B7280"))
        }
    }

    // ========== UI 工具 ==========

    private fun createLabel(context: Context, text: String) = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor("#6B7280"))
        textSize = 12f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12.dpToPx(context); bottomMargin = 4.dpToPx(context) }
    }

    private fun wrapInput(context: Context, editText: TextInputEditText): TextInputLayout {
        val layout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(editText)
        return layout
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun Int.dpToPx(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}