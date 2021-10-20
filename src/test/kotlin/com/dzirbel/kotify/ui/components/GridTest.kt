package com.dzirbel.kotify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dzirbel.kotify.ui.screenshotTest
import com.dzirbel.kotify.ui.theme.Colors
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class GridTest {
    data class ScreenshotTestCase(val filename: String, val alignment: Alignment, val columns: Int?)

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun test() {
        val columns = 2
        val totalWidth = 1024.dp
        val horizontalSpacing = 10.dp
        val verticalSpacing = 5.dp
        val elementHeight = 20.dp
        val elements = listOf(1, 2, 3)

        rule.setContent {
            Grid(
                elements = elements,
                columns = columns,
                horizontalSpacing = horizontalSpacing,
                verticalSpacing = verticalSpacing,
            ) { element ->
                Text(
                    text = element.toString(),
                    modifier = Modifier.fillMaxWidth().height(elementHeight)
                )
            }
        }

        val expectedElementWidth = (totalWidth - horizontalSpacing * 3) / columns
        elements.forEach { element ->
            rule.onNode(hasText(element.toString()))
                .assertExists()
                .assertWidthIsEqualTo(expectedElementWidth)
                .assertHeightIsEqualTo(elementHeight)
        }

        rule.onNode(hasText(elements[0].toString()))
            .assertLeftPositionInRootIsEqualTo(horizontalSpacing)
            .assertTopPositionInRootIsEqualTo(verticalSpacing)

        rule.onNode(hasText(elements[1].toString()))
            .assertLeftPositionInRootIsEqualTo(horizontalSpacing * 2 + expectedElementWidth)
            .assertTopPositionInRootIsEqualTo(verticalSpacing)

        rule.onNode(hasText(elements[2].toString()))
            .assertLeftPositionInRootIsEqualTo(horizontalSpacing)
            .assertTopPositionInRootIsEqualTo(verticalSpacing * 2 + elementHeight)

        val unexpectedElement = elements.maxOrNull()!! + 1
        rule.onNode(hasText(unexpectedElement.toString())).assertDoesNotExist()
    }

    @ParameterizedTest
    @MethodSource("screenshotTestCases")
    fun testScreenshot(case: ScreenshotTestCase) {
        // no need to test multiple color sets since all the colors are hardcoded anyway
        screenshotTest(filename = case.filename, colorsSet = setOf(Colors.LIGHT)) {
            Grid(
                modifier = Modifier.background(Color.Green),
                elements = List(50) { it },
                columns = case.columns,
                horizontalSpacing = 10.dp,
                verticalSpacing = 5.dp,
                cellAlignment = case.alignment,
            ) { index ->
                Box(
                    modifier = Modifier
                        .background(Color.Black)
                        .size((20 + index * 2).dp)
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun screenshotTestCases(): List<ScreenshotTestCase> {
            return listOf(
                ScreenshotTestCase(filename = "center", alignment = Alignment.Center, columns = 6),
                ScreenshotTestCase(filename = "bottom-end", alignment = Alignment.BottomEnd, columns = 6),
                ScreenshotTestCase(filename = "dynamic-cols", alignment = Alignment.Center, columns = null),
            )
        }
    }
}