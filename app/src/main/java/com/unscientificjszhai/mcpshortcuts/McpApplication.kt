package com.unscientificjszhai.mcpshortcuts

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.unscientificjszhai.mcpshortcuts.mcp.McpLifecycleObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class McpApplication : Application() {

    @Inject
    lateinit var lifecycleObserver: McpLifecycleObserver

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }
}
