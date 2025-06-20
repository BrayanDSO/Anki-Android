/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.con>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.net.Uri
import android.webkit.WebView
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.TapGestureMode
import timber.log.Timber
import kotlin.math.abs

/**
 * Parses gestures like taps and swipes based on coordinate data passed within an [Uri].
 *
 * @see parse
 */
object GestureParser {
    private const val SWIPE_THRESHOLD_BASE = 18
    private val gestureGrid =
        listOf(
            listOf(Gesture.TAP_TOP_LEFT, Gesture.TAP_TOP, Gesture.TAP_TOP_RIGHT),
            listOf(Gesture.TAP_LEFT, Gesture.TAP_CENTER, Gesture.TAP_RIGHT),
            listOf(Gesture.TAP_BOTTOM_LEFT, Gesture.TAP_BOTTOM, Gesture.TAP_BOTTOM_RIGHT),
        )

    /**
     * Analyzes the given [Uri] and returns the corresponding [Gesture].
     *
     * @param uri The [Uri] containing gesture data.
     * @param isScrolling whether the WebView is being scrolled.
     * @param scale The current scale of the WebView.
     * @param scrollX The horizontal scroll offset of the WebView.
     * @param scrollY The vertical scroll offset of the WebView.
     * @param measuredWidth The measured width of the WebView.
     * @param measuredHeight The measured height of the WebView.
     * @return The parsed [Gesture], or `null` if invalid or should be ignored.
     */
    fun parse(
        uri: Uri,
        isScrolling: Boolean,
        scale: Float,
        scrollX: Int,
        scrollY: Int,
        measuredWidth: Int,
        measuredHeight: Int,
        gestureMode: TapGestureMode,
    ): Gesture? {
        if (isScrolling) return null
        if (uri.host == "doubleTap") return Gesture.DOUBLE_TAP

        val pageX = uri.getIntQuery("x") ?: return null
        val pageY = uri.getIntQuery("y") ?: return null
        val deltaX = uri.getIntQuery("deltaX") ?: return null
        val deltaY = uri.getIntQuery("deltaY") ?: return null
        val absDeltaX = abs(deltaX)
        val absDeltaY = abs(deltaY)

        val swipeThreshold = SWIPE_THRESHOLD_BASE / scale
        if (absDeltaX > swipeThreshold || absDeltaY > swipeThreshold) {
            val scrollDirection = uri.getQueryParameter("scrollDirection")
            return determineSwipeGesture(deltaX, deltaY, absDeltaX, absDeltaY, scrollDirection)
        }

        return if (gestureMode == TapGestureMode.FOUR_POINT) {
            getFourPointsTap(pageX, pageY, scrollX, scrollY, measuredWidth, measuredHeight, scale)
        } else {
            getNinePointsTap(pageX, pageY, scrollX, scrollY, measuredWidth, measuredHeight, scale)
        }
    }

    /**
     * Analyzes the given [Uri] and returns the corresponding [Gesture].
     *
     * @param uri The [Uri] containing gesture data.
     * @param isScrolling whether the WebView is being scrolled.
     * @param scale The current scale of the WebView.
     * @param webView The source WebView, used to access its current scroll and size properties.
     * @return The parsed [Gesture], or `null` if the gesture is invalid or should be ignored.
     */
    fun parse(
        uri: Uri,
        isScrolling: Boolean,
        scale: Float,
        webView: WebView,
        gestureMode: TapGestureMode,
    ): Gesture? =
        parse(
            uri = uri,
            isScrolling = isScrolling,
            scale = scale,
            scrollX = webView.scrollX,
            scrollY = webView.scrollY,
            measuredWidth = webView.measuredWidth,
            measuredHeight = webView.measuredHeight,
            gestureMode = gestureMode,
        )

    /**
     * Determines the swipe gesture based on deltas and scroll direction.
     *
     * @param scrollDirection Indicates whether the underlying web content at the gesture's origin
     * is scrollable. This value is determined by the `getScrollDirection`
     * function in `ankidroid.js` and is used to prevent custom swipe gestures
     * from overriding the browser's native scrolling behavior. It can contain:
     * - 'h': The content is horizontally scrollable.
     * - 'v': The content is vertically scrollable.
     * - "hv": The content is scrollable in both directions.
     * - `null`: The content is not scrollable.
     * @return The swipe [Gesture], or `null` if the swipe is in a direction that is scrollable
     * by the underlying web content.
     */
    private fun determineSwipeGesture(
        deltaX: Int,
        deltaY: Int,
        absDeltaX: Int,
        absDeltaY: Int,
        scrollDirection: String?,
    ): Gesture? =
        if (absDeltaX > absDeltaY) { // horizontal
            when {
                scrollDirection?.contains('h') == true -> null
                deltaX > 0 -> Gesture.SWIPE_RIGHT
                else -> Gesture.SWIPE_LEFT
            }
        } else { // vertical
            when {
                scrollDirection?.contains('v') == true -> null
                deltaY > 0 -> Gesture.SWIPE_DOWN
                else -> Gesture.SWIPE_UP
            }
        }

    private fun getAdjustedTapPosition(
        tapPosition: Int,
        scrolledDistance: Int,
        scale: Float,
    ): Float {
        val scaledTap = tapPosition * scale
        return scaledTap - scrolledDistance
    }

    /**
     * Calculates the grid index (row or column) for a tap coordinate.
     *
     * This function translates a raw screen tap coordinate into an index (0, 1, or 2) for one
     * dimension of the 3x3 gesture grid. It accounts for the WebView's current scroll position
     * and zoom level.
     *
     * @param tapPosition The raw client coordinate (X or Y) of the tap.
     * @param scrolledDistance The distance the WebView is scrolled along that axis.
     * @param dimensionSize The measured size (width or height) of the WebView.
     * @return The calculated grid index, constrained to be between 0 and 2.
     */
    private fun getGridIndex(
        tapPosition: Int,
        scrolledDistance: Int,
        dimensionSize: Int,
        scale: Float,
    ): Int {
        if (dimensionSize == 0) return 0 // avoids dividing by 0
        val adjustedTapPosition = getAdjustedTapPosition(tapPosition, scrolledDistance, scale)
        val relativePosition = (adjustedTapPosition / (dimensionSize / 3))
        val index = relativePosition.toInt()
        // Temporary timber warning to solve #18559
        Timber.w("adjustedTapPosition $adjustedTapPosition - relativePosition $relativePosition - index $index")
        return index
    }

    private fun getFourPointsTap(
        tapX: Int,
        tapY: Int,
        scrollX: Int,
        scrollY: Int,
        measuredWidth: Int,
        measuredHeight: Int,
        scale: Float,
    ): Gesture {
        val adjustedX = getAdjustedTapPosition(tapX, scrollX, scale)
        val adjustedY = getAdjustedTapPosition(tapY, scrollY, scale)
        val normalizedX = adjustedX / measuredWidth
        val normalizedY = adjustedY / measuredHeight

        // The "main" diagonal runs from top-left (0,0) to bottom-right (width,height).
        val isRightOfMainDiagonal = normalizedX > normalizedY
        // The "anti" diagonal runs from top-right (width,0) to bottom-left (0,height).
        val isBelowAntiDiagonal = normalizedX + normalizedY > 1

        return when {
            isRightOfMainDiagonal && isBelowAntiDiagonal -> Gesture.TAP_RIGHT
            isRightOfMainDiagonal && !isBelowAntiDiagonal -> Gesture.TAP_TOP
            !isRightOfMainDiagonal && isBelowAntiDiagonal -> Gesture.TAP_BOTTOM
            else -> Gesture.TAP_LEFT // !isRightOfMainDiagonal && !isBelowAntiDiagonal
        }
    }

    private fun getNinePointsTap(
        tapX: Int,
        tapY: Int,
        scrollX: Int,
        scrollY: Int,
        measuredWidth: Int,
        measuredHeight: Int,
        scale: Float,
    ): Gesture {
        val row = getGridIndex(tapY, scrollY, measuredHeight, scale)
        val column = getGridIndex(tapX, scrollX, measuredWidth, scale)
        // FIXME fix the source of values that result in an invalid index
        if (row !in 0..2 || column !in 0..2) {
            throw IllegalArgumentException(
                "Gesture parsing error: row $row - column $column - scale $scale - tapX $tapX - tapY $tapY - scrollX $scrollX - scrollY $scrollY - measuredWidth $measuredWidth - measuredHeight $measuredHeight",
            )
        }
        return gestureGrid[row][column]
    }

    private fun Uri.getIntQuery(key: String) = getQueryParameter(key)?.toIntOrNull()
}
