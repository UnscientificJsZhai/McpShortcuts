package com.unscientificjszhai.mcpshortcuts.ui.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.color.DynamicColors
import com.unscientificjszhai.mcpshortcuts.R

/**
 * 应用设置界面。
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.app_bar)) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings)) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navigationBars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
