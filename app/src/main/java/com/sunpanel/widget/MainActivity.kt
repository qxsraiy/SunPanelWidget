package com.sunpanel.widget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.sunpanel.widget.databinding.ActivityMainBinding

/**
 * 主界面：底部导航容器
 * - 面板：内嵌 Sun-Panel 网页，可直接操作
 * - 设置：服务器配置 + 数据同步 + 外观自定义
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var panelFragment: PanelFragment? = null
    private var settingsFragment: SettingsFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 底部导航切换
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_panel -> {
                    showPanelFragment()
                    true
                }
                R.id.nav_settings -> {
                    showSettingsFragment()
                    true
                }
                else -> false
            }
        }

        // 默认显示面板页
        binding.bottomNav.selectedItemId = R.id.nav_panel
    }

    /** 显示面板页 */
    fun showPanelFragment() {
        if (panelFragment == null) {
            panelFragment = PanelFragment()
        }
        switchTo(panelFragment!!)
    }

    /** 显示设置页 */
    fun showSettingsFragment() {
        if (settingsFragment == null) {
            settingsFragment = SettingsFragment()
        }
        switchTo(settingsFragment!!)
    }

    private fun switchTo(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /** 从面板页"去设置"按钮跳转 */
    fun switchToSettings() {
        binding.bottomNav.selectedItemId = R.id.nav_settings
    }

    /** 配置变化后刷新面板页（在设置页保存成功后调用） */
    fun refreshPanel() {
        panelFragment?.refresh()
    }

    /** 返回键：面板 WebView 页内返回优先 */
    private val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            if (current is PanelFragment) {
                if (panelFragment?.onBackPressed() == true) {
                    return  // WebView 页内返回
                }
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    init {
        onBackPressedDispatcher.addCallback(this, backCallback)
    }
}