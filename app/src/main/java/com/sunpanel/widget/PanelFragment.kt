package com.sunpanel.widget

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.sunpanel.widget.data.PreferencesManager
import com.sunpanel.widget.databinding.FragmentPanelBinding

/**
 * 面板页：内嵌 Sun-Panel 网页
 * 自动检测登录状态 → 用设置页保存的账号密码自动登录 → 注入 token → 刷新
 * 用户只需在设置页配一次，面板页自动登录，无需多次输入
 */
class PanelFragment : Fragment() {

    private var _binding: FragmentPanelBinding? = null
    private val binding get() = _binding!!

    private val prefs get() = PreferencesManager.getInstance(requireContext())
    private var autoLoginAttempted = false  // 避免重复尝试

    companion object {
        private const val TAG = "PanelFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        binding.btnGoSettings.setOnClickListener {
            (activity as? MainActivity)?.switchToSettings()
        }
        loadPanel()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportMultipleWindows(false) // 禁止 target=_blank 开新窗口

            // 暴露一个 Java 接口给 JS 调用，用于从 App 获取账号密码
            addJavascriptInterface(object {
                @JavascriptInterface
                fun getUsername(): String = prefs.username
                @JavascriptInterface
                fun getPassword(): String = prefs.password
                @JavascriptInterface
                fun getServerUrl(): String = prefs.serverUrl.trimEnd('/')
            }, "SunPanelApp")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): Boolean {
                    // 所有链接在 WebView 内部打开，保持历史栈，支持返回
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val b = _binding ?: return  // Fragment 已销毁，安全退出
                    b.loadingLayout.visibility = View.GONE
                    Log.d(TAG, "面板加载完成: $url")

                    // 自动登录：检查是否已登录，未登录则自动登录
                    if (!autoLoginAttempted && prefs.token.isNotBlank()) {
                        // 方式一：已有会话 token → 直接注入 localStorage
                        injectSessionToken()
                    } else if (!autoLoginAttempted &&
                        prefs.username.isNotBlank() && prefs.password.isNotBlank()
                    ) {
                        // 方式二：有账号密码但无 token → 通过 API 登录
                        autoLogin(view)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    val b = _binding ?: return  // Fragment 已销毁，安全退出
                    if (newProgress < 100) {
                        b.loadingLayout.visibility = View.VISIBLE
                    } else {
                        b.loadingLayout.visibility = View.GONE
                    }
                }
            }
        }
    }

    /** 方式一：已有会话 token → 直接注入 localStorage 的 AUTH_TOKEN */
    private fun injectSessionToken() {
        autoLoginAttempted = true
        val token = prefs.token
        if (token.isBlank()) return

        val js = """
            javascript:(function(){
                var key = 'AUTH_TOKEN';
                var existing = localStorage.getItem(key);
                if (existing) {
                    try {
                        var parsed = JSON.parse(existing);
                        if (parsed && parsed.data && parsed.data.token) {
                            console.log('【SunPanel】已登录，跳过');
                            return;
                        }
                    } catch(e) {}
                }
                var data = JSON.stringify({
                    data: { token: '$token', userInfo: null, visitMode: 'VISIT_MODE_LOGIN' },
                    expire: null
                });
                localStorage.setItem(key, data);
                console.log('【SunPanel】自动注入 token 成功');
                location.reload();
            })();
        """.trimIndent()
        binding.webView.evaluateJavascript(js, null)
    }

    /** 方式二：通过 API 登录获取 token → 注入 localStorage */
    private fun autoLogin(view: WebView?) {
        autoLoginAttempted = true
        val username = prefs.username
        val password = prefs.password
        if (username.isBlank() || password.isBlank()) return

        val js = """
            javascript:(function(){
                var u = '$username', p = '$password';
                var key = 'AUTH_TOKEN';
                var existing = localStorage.getItem(key);
                if (existing) {
                    try {
                        var parsed = JSON.parse(existing);
                        if (parsed && parsed.data && parsed.data.token) {
                            console.log('【SunPanel】已登录，跳过');
                            return;
                        }
                    } catch(e) {}
                }
                fetch('/api/login', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({username: u, password: p})
                }).then(function(r){ return r.json(); }).then(function(d){
                    if (d && d.code === 0 && d.data && d.data.token) {
                        var data = JSON.stringify({
                            data: { token: d.data.token, userInfo: null, visitMode: 'VISIT_MODE_LOGIN' },
                            expire: null
                        });
                        localStorage.setItem(key, data);
                        console.log('【SunPanel】自动登录成功');
                        location.reload();
                    } else {
                        console.log('【SunPanel】自动登录失败:', JSON.stringify(d));
                    }
                }).catch(function(e){
                    console.log('【SunPanel】自动登录异常:', e.message);
                });
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    /** 加载面板主页（服务器地址） */
    private fun loadPanel() {
        val serverUrl = prefs.serverUrl.trim()
        if (serverUrl.isBlank()) {
            binding.webView.visibility = View.GONE
            binding.loadingLayout.visibility = View.GONE
            binding.noConfigLayout.visibility = View.VISIBLE
            return
        }
        binding.webView.visibility = View.VISIBLE
        binding.noConfigLayout.visibility = View.GONE
        binding.loadingLayout.visibility = View.VISIBLE
        // 重置自动登录标记
        autoLoginAttempted = false

        val panelUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        Log.d(TAG, "加载面板: $panelUrl")
        binding.webView.loadUrl(panelUrl)
    }

    /** 外部触发刷新（配置变化后调用） */
    fun refresh() {
        loadPanel()
    }

    /** 处理返回键（WebView 页内返回） */
    fun onBackPressed(): Boolean {
        return if (binding.webView.canGoBack()) {
            binding.webView.goBack()
            true
        } else {
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 安全销毁 WebView：先从视图树移除，再销毁，避免回调触发 NPE
        try {
            binding.webView.apply {
                (parent as? ViewGroup)?.removeView(this)
                stopLoading()
                removeAllViews()
                destroy()
            }
        } catch (_: Exception) {}
        _binding = null
    }
}