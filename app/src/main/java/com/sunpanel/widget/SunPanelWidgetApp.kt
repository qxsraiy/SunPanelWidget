package com.sunpanel.widget

import android.app.Application
import com.sunpanel.widget.data.PreferencesManager

/**
 * Application 类
 * 初始化全局单例
 */
class SunPanelWidgetApp : Application() {

    lateinit var prefsManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefsManager = PreferencesManager.getInstance(this)
    }

    companion object {
        @Volatile
        private var instance: SunPanelWidgetApp? = null

        fun getInstance(): SunPanelWidgetApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
}