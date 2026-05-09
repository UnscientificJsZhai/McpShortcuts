package com.unscientificjszhai.mcpshortcuts.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.unscientificjszhai.mcpshortcuts.R

/**
 * 自定义应用主题。
 * 支持动态色彩（Android 12+）以及深色/浅色模式切换。
 *
 * @param darkTheme 是否使用深色主题，默认跟随系统。
 * @param dynamicColor 是否启用动态色彩，默认开启。
 * @param content 要应用主题的 Compose 内容。
 */
@Composable
fun CustomAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme(
            primary = colorResource(R.color.sky_blue_80),
            secondary = colorResource(R.color.sky_blue_grey_80),
            tertiary = colorResource(R.color.sky_blue_pink_80),
            primaryContainer = colorResource(R.color.sky_blue_pink_40),
            onPrimaryContainer = colorResource(R.color.white)
        )

        else -> lightColorScheme(
            primary = colorResource(R.color.sky_blue_40),
            secondary = colorResource(R.color.sky_blue_grey_40),
            tertiary = colorResource(R.color.sky_blue_pink_40),
            primaryContainer = colorResource(R.color.sky_blue_80),
            onPrimaryContainer = colorResource(R.color.dark_gray)

            /* Other default colors to override
            background = Color(0xFFFFFBFE),
            surface = Color(0xFFFFFBFE),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F),
            */
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}