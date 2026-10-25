// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import com.ichi2.anki.libanki.EpochMilliseconds
import kotlinx.serialization.Serializable

/**
 * Unparsed gesture data sent as JSON by `ankidroid-reviewer.js`.
 *
 * @see GestureParser
 */
sealed class RawGesture {
    /** A tap with more than one finger */
    @Serializable
    data class MultiTouch(
        val touchCount: Int,
    ) : RawGesture()

    /**
     * A tap or swipe with one finger.
     *
     * Positions and distances are in CSS pixels: [GestureParser] converts them to the view's pixels
     */
    @Serializable
    data class TapOrSwipe(
        /** Where the touch ended, relative to the page (`Touch.pageX`) */
        val x: Int,
        /** Where the touch ended, relative to the page (`Touch.pageY`) */
        val y: Int,
        /** Horizontal distance from where the touch started */
        val deltaX: Int,
        /** Vertical distance from where the touch started */
        val deltaY: Int,
        /** When the touch ended */
        val time: EpochMilliseconds,
        /** @see GestureParser.determineSwipeGesture */
        val scrollDirection: String?,
    ) : RawGesture()
}
