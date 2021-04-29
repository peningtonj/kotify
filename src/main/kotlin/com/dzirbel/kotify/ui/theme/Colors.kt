package com.dzirbel.kotify.ui.theme

import androidx.compose.foundation.ScrollbarStyleAmbient
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.dzirbel.kotify.Settings

@Suppress("MagicNumber", "LongParameterList")
enum class Colors(
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val dividerColor: Color,
    val text: Color,
    val textOnSurface: Color,
    val error: Color,
    private val scrollBarHover: Color,
    private val scrollBarUnhover: Color,
    private val materialColors: androidx.compose.material.Colors
) {
    DARK(
        surface1 = Color(0x42, 0x42, 0x42),
        surface2 = Color(0x21, 0x21, 0x21),
        surface3 = Color(0x10, 0x10, 0x10),
        dividerColor = Color(0x30, 0x30, 0x30),
        text = Color(0xFA, 0xFA, 0xFA),
        textOnSurface = Color(0x08, 0x08, 0x08),
        error = Color.Red,
        scrollBarHover = Color(0x60, 0x60, 0x60),
        scrollBarUnhover = Color(0x50, 0x50, 0x50),
        materialColors = darkColors()
    ),

    LIGHT(
        surface1 = Color(0xE0, 0xE0, 0xE0),
        surface2 = Color(0xEF, 0xEF, 0xEF),
        surface3 = Color(0xFD, 0xFD, 0xFD),
        dividerColor = Color(0x18, 0x18, 0x18),
        text = Color(0x08, 0x08, 0x08),
        textOnSurface = Color(0xFA, 0xFA, 0xFA),
        error = Color.Red,
        scrollBarHover = Color(0x90, 0x90, 0x90),
        scrollBarUnhover = Color(0x78, 0x78, 0x78),
        materialColors = lightColors()
    );

    val primary: Color
        get() = materialColors.primary

    /**
     * Gets a highlight-aware color, i.e. [primary] if [highlight] is true and [otherwise] if not.
     *
     * Also applies the alpha from [LocalContentAlpha].
     */
    @Composable
    fun highlighted(highlight: Boolean, otherwise: Color = LocalContentColor.current): Color {
        return (if (highlight) primary else otherwise).copy(alpha = LocalContentAlpha.current)
    }

    /**
     * Applies this set of [Colors] to the given [content].
     */
    @Composable
    fun applyColors(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalContentColor provides text,
            ScrollbarStyleAmbient provides defaultScrollbarStyle().copy(
                hoverColor = scrollBarHover,
                unhoverColor = scrollBarUnhover
            )
        ) {
            MaterialTheme(colors = materialColors, content = content)
        }
    }

    companion object {
        var current: Colors
            get() = Settings.current.colors
            set(value) {
                Settings.mutate { copy(colors = value) }
            }
    }
}