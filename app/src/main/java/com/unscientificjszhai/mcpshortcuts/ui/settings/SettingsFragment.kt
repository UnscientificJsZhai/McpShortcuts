package com.unscientificjszhai.mcpshortcuts.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.unscientificjszhai.mcpshortcuts.R

/**
 * 设置界面的 Fragment。
 * 承载具体的设置项配置。
 */
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val urlPref = findPreference<EditTextPreference>("openai_api_url")
        urlPref?.setOnBindEditTextListener { editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val url = s?.toString() ?: ""
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        editText.error = getString(R.string.error_invalid_url)
                    } else {
                        editText.error = null
                    }
                }
            })
        }

        urlPref?.setOnPreferenceChangeListener { preference, newValue ->
            val url = newValue.toString()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@setOnPreferenceChangeListener false
            }
            if (!url.endsWith("/")) {
                val newUrl = "$url/"
                (preference as EditTextPreference).text = newUrl
                return@setOnPreferenceChangeListener false
            }
            true
        }

        val systemPromptPref = findPreference<EditTextPreference>("openai_system_prompt")
        systemPromptPref?.setOnBindEditTextListener { editText ->
            editText.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            editText.isSingleLine = false
            editText.setHorizontallyScrolling(false)
            editText.minLines = 6
            editText.maxLines = 16
        }
        systemPromptPref?.summaryProvider =
            Preference.SummaryProvider<EditTextPreference> { preference ->
                val text = preference.text
                if (TextUtils.isEmpty(text)) {
                    getString(R.string.pref_summary_openai_system_prompt_empty)
                } else {
                    if (text!!.length > 80) {
                        "${text.take(80)}..."
                    } else {
                        text
                    }
                }
            }
    }
}