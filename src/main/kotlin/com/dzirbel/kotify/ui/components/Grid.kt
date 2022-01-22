package com.dzirbel.kotify.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dzirbel.kotify.ui.theme.Dimens
import com.dzirbel.kotify.ui.theme.LocalColors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A [Shape] consisting of a rounded rectangle with no line along the bottom and the bottom rounding flares outwards
 * instead of rounding in. May also have an optional extra [bottomPadding].
 */
private class FlaredBottomRoundedRect(val cornerSize: Dp, val bottomPadding: Dp = 0.dp) : Shape {
    @Suppress("MagicNumber")
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cornerSizePx = with(density) { cornerSize.toPx() }
        val height = size.height - with(density) { bottomPadding.toPx() }

        val path = Path().apply {
            moveTo(x = 0f, y = height)

            val bottomLeftRect = Rect(
                center = Offset(x = 0f, y = height - cornerSizePx),
                radius = cornerSizePx,
            )
            arcTo(rect = bottomLeftRect, startAngleDegrees = 90f, sweepAngleDegrees = -90f, forceMoveTo = false)

            lineTo(x = cornerSizePx, y = cornerSizePx)

            val topLeftRect = Rect(center = Offset(x = 2 * cornerSizePx, y = cornerSizePx), radius = cornerSizePx)
            arcTo(rect = topLeftRect, startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false)

            lineTo(x = size.width - cornerSizePx * 2, y = 0f)

            val topRightRect = Rect(
                center = Offset(x = size.width - 2 * cornerSizePx, y = cornerSizePx),
                radius = cornerSizePx,
            )
            arcTo(rect = topRightRect, startAngleDegrees = 270f, sweepAngleDegrees = 90f, forceMoveTo = false)

            lineTo(x = size.width - cornerSizePx, y = height - cornerSizePx)

            val bottomRightRect = Rect(
                center = Offset(x = size.width, y = height - cornerSizePx),
                radius = cornerSizePx,
            )
            arcTo(rect = bottomRightRect, startAngleDegrees = 180f, sweepAngleDegrees = -90f, forceMoveTo = false)
        }

        return Outline.Generic(path)
    }
}

/**
 * A simple two-dimensional grid layout, which arranges [cellContent] for each [elements] as a table.
 *
 * The layout always expands vertically to fit all the [elements]. Each column has the width of the widest
 * [cellContent]; each row the height of the tallest [cellContent] in that row. The number of columns will equal
 * [columns] if provided, otherwise it will be the maximum number of contents that can fit.
 *
 * [horizontalSpacing] and [verticalSpacing] will be added between columns and rows, respectively, including before the
 * first row/column and after the last row/column.
 *
 * A [selectedElement] may be provided along with [detailInsertContent] to display an insert below the row of the
 * [selectedElement] with some extra content for it.
 */
@Composable
@Suppress("UnnecessaryParentheses")
fun <E> Grid(
    elements: List<E>,
    selectedElement: E? = null,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = Dimens.space2,
    verticalSpacing: Dp = Dimens.space3,
    cellAlignment: Alignment = Alignment.TopCenter,
    columns: Int? = null,
    detailInsertBackground: Color = LocalColors.current.surface2,
    detailInsertBorder: Color = LocalColors.current.dividerColor,
    detailInsertCornerSize: Dp = Dimens.cornerSize * 2,
    detailInsertAnimationDurationMs: Int = AnimationConstants.DefaultDurationMillis,
    detailInsertContent: @Composable ((E) -> Unit)? = null,
    cellContent: @Composable (element: E) -> Unit,
) {
    require(columns == null || columns > 0) { "columns must be positive; got $columns" }

    val layoutDirection = LocalLayoutDirection.current

    val insertAnimationState = remember { MutableTransitionState(false) }
    insertAnimationState.targetState = selectedElement != null

    // keeps track of the last selected element (or null if an element has never been selected) - this is used to
    // continue displaying it as it is being animated out
    val lastSelectedElement = remember { mutableStateOf(selectedElement) }
    if (selectedElement != null) {
        lastSelectedElement.value = selectedElement
    }

    val currentDetailElement = lastSelectedElement.value
    val currentDetailElementIndex = currentDetailElement?.let {
        remember(elements, currentDetailElement) {
            elements.indexOf(it).also { index ->
                require(index >= 0) { "could not find selected element $selectedElement in elements" }
            }
        }
    }

    Layout(
        content = {
            elements.forEach { element ->
                Box {
                    cellContent(element)
                }
            }

            if (detailInsertContent != null && currentDetailElement != null) {
                AnimatedVisibility(
                    visibleState = insertAnimationState,
                    enter = fadeIn(animationSpec = tween(durationMillis = detailInsertAnimationDurationMs)),
                    exit = fadeOut(animationSpec = tween(durationMillis = detailInsertAnimationDurationMs)),
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = detailInsertBackground,
                                shape = FlaredBottomRoundedRect(cornerSize = detailInsertCornerSize),
                            )
                            .border(
                                width = Dimens.divider,
                                color = detailInsertBorder,
                                shape = FlaredBottomRoundedRect(
                                    cornerSize = detailInsertCornerSize,
                                    bottomPadding = Dimens.divider,
                                ),
                            )
                            .fillMaxSize()
                    )
                }

                AnimatedVisibility(
                    visibleState = insertAnimationState,
                    enter = expandVertically(
                        animationSpec = tween(durationMillis = detailInsertAnimationDurationMs),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(animationSpec = tween(durationMillis = detailInsertAnimationDurationMs)),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = detailInsertAnimationDurationMs),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(animationSpec = tween(durationMillis = detailInsertAnimationDurationMs)),
                ) {
                    Box(
                        modifier = Modifier
                            .background(detailInsertBackground)
                            .border(width = Dimens.divider, color = detailInsertBorder)
                            .fillMaxWidth()
                    ) {
                        detailInsertContent(currentDetailElement)
                    }
                }
            }
        },
        modifier = modifier,
        measurePolicy = { measurables, constraints ->
            val cellMeasurables = measurables.take(elements.size)

            val horizontalSpacingPx: Float = horizontalSpacing.toPx()
            val verticalSpacingPx: Float = verticalSpacing.toPx()

            // max width for each column is the total column space (total width minus one horizontal spacing for the
            // spacing after the last column) divided by the minimum number of columns, minus the spacing for the column
            val minColumns = columns ?: 1
            val cellConstraints = Constraints(
                maxWidth = (((constraints.maxWidth - horizontalSpacingPx) / minColumns) - horizontalSpacingPx).toInt()
                    .coerceAtLeast(0)
            )

            var maxCellWidth = 0 // find max cell width while measuring to avoid an extra loop
            val cellPlaceables = cellMeasurables.map {
                it.measure(cellConstraints).also { placeable ->
                    maxCellWidth = max(maxCellWidth, placeable.width)
                }
            }

            // the total width of a column, including its spacing
            val columnWidthWithSpacing: Float = maxCellWidth + horizontalSpacingPx

            // number of columns is the total column space (total width minus one horizontal spacing for the spacing
            // after the last column) divided by the column width including its spacing; then taking the floor to
            // truncate any "fractional column"
            val cols: Int = columns
                ?: ((constraints.maxWidth - horizontalSpacingPx) / columnWidthWithSpacing).toInt().coerceAtLeast(1)
            // number of rows is the number of cells divided by number of columns, rounded up
            val rows: Int = ceil(elements.size.toFloat() / cols).toInt()

            // now we need to account for that "fractional column" by adding some "extra" to each column spacing,
            // distributed among each spacing (note: we cannot add this extra to the columns rather than the spacing
            // because the placeables have already been measured)
            // first: the total width used without the extra is the number of columns times the column width with
            // spacing plus an extra horizontal spacing to account for the trailing space
            // next: extra is the max width minus the used width, divided by the number of columns plus one (to include
            // the trailing space)
            // finally: create adjusted width variables including the extra
            val usedWidth: Float = (cols * columnWidthWithSpacing) + horizontalSpacingPx
            val extra: Float = (constraints.maxWidth - usedWidth) / (cols + 1)
            val horizontalSpacingPxWithExtra: Float = horizontalSpacingPx + extra
            val columnWidthWithSpacingAndExtra: Float = maxCellWidth + horizontalSpacingPxWithExtra

            // total used height is the sum of the row heights (each of which being the maximum element height in the
            // row) plus the total vertical spacing (the vertical spacing per row times the number of rows plus 1, to
            // include the trailing space)
            var totalHeight = (verticalSpacingPx * (rows + 1)).roundToInt()
            val rowHeights = Array(rows) { row ->
                cellPlaceables.subList(fromIndex = row * cols, toIndex = ((row + 1) * cols).coerceAtMost(elements.size))
                    .maxOf { it.height }
                    .also { totalHeight += it }
            }

            val selectedElementRowIndex: Int?
            val selectedElementColIndex: Int?
            val detailInsertPlaceable: Placeable?
            val selectedItemBackgroundPlaceable: Placeable?

            // ensure we have exactly 2 insert measurables; in rare cases if the animation is toggled on or off we can
            // just 1
            if (measurables.size - elements.size == 2) {
                val insertMeasurables = measurables.takeLast(2)

                requireNotNull(currentDetailElementIndex)
                selectedElementRowIndex = currentDetailElementIndex / cols
                selectedElementColIndex = currentDetailElementIndex % cols

                // background highlight on the selected item:
                // - has extra maxWidth since otherwise during the animation the flared base is clipped
                // - maxHeight increased by verticalSpacingPx to cover space between the row and the insert
                // - maxHeight increased by divider size to align better with insert top border
                selectedItemBackgroundPlaceable = insertMeasurables[0].measure(
                    constraints.copy(
                        maxWidth = maxCellWidth + detailInsertCornerSize.roundToPx() * 2,
                        maxHeight = rowHeights[selectedElementRowIndex] +
                            (verticalSpacingPx + Dimens.divider.toPx()).roundToInt(),
                    )
                )

                detailInsertPlaceable = insertMeasurables[1].measure(constraints)

                totalHeight += detailInsertPlaceable.height + verticalSpacingPx.roundToInt()
            } else {
                selectedElementRowIndex = null
                selectedElementColIndex = null
                detailInsertPlaceable = null
                selectedItemBackgroundPlaceable = null
            }

            layout(constraints.maxWidth, totalHeight) {
                // keep track of the y for each row; start at the spacing to include the top spacing
                var y = verticalSpacingPx
                for (rowIndex in 0 until rows) {
                    val rowHeight = rowHeights[rowIndex]
                    val roundedY = y.roundToInt()

                    // place the insert before the row so that the selected item background is drawn on top of it
                    if (rowIndex == selectedElementRowIndex) {
                        detailInsertPlaceable!!.place(
                            x = 0,
                            y = (y + rowHeight + verticalSpacingPx).roundToInt(),
                        )
                    }

                    for (colIndex in 0 until cols) {
                        cellPlaceables.getOrNull(colIndex + rowIndex * cols)?.let { placeable ->
                            val baseX = (horizontalSpacingPxWithExtra + (colIndex * columnWidthWithSpacingAndExtra))
                                .roundToInt()

                            if (rowIndex == selectedElementRowIndex && colIndex == selectedElementColIndex) {
                                // adjust x to account for flared base
                                selectedItemBackgroundPlaceable!!.place(
                                    x = baseX - detailInsertCornerSize.roundToPx(),
                                    y = y.roundToInt(),
                                )
                            }

                            // adjust the element based on its alignment and place it
                            val alignment = cellAlignment.align(
                                size = IntSize(width = placeable.width, height = placeable.height),
                                space = IntSize(width = maxCellWidth, height = rowHeight),
                                layoutDirection = layoutDirection,
                            )

                            placeable.place(x = baseX + alignment.x, y = roundedY + alignment.y)
                        }
                    }

                    y += rowHeight + verticalSpacingPx

                    if (rowIndex == selectedElementRowIndex) {
                        y += detailInsertPlaceable!!.height + verticalSpacingPx
                    }
                }
            }
        }
    )
}
