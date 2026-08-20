package com.sunpanel.widget

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.sunpanel.widget.data.PreferencesManager
import com.sunpanel.widget.databinding.FragmentPanelBinding

/**
 * 面板页：直接内嵌 Sun-Panel 网页
 * 可在这里直接浏览/操作面板（登录后即可增删改书签）
 */
class PanelFragment : Fragment() {

    private var _binding: FragmentPanelBinding? = null
    private val binding get() = _binding!!

    private val prefs get() = PreferencesManager.getInstance(requireContext())

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
            settings.mediaPlaybackRequiresUserGesture = false

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.loadingLayout.visibility = View.GONE
                    Log.d(TAG, "面板加载完成: $url")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress < 100) {
                        binding.loadingLayout.visibility = View.VISIBLE
                    } else {
                        binding.loadingLayout.visibility = View.GONE
                    }
                }
            }
        }
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
        // 拼接面板主页路径
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
        _binding = null
    }
}