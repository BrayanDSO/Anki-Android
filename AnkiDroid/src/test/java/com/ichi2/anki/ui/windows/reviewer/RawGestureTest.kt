// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The JSON is in the format sent by `ankidroid-reviewer.js` */
class RawGestureTest {
    @Test
    fun `multi touch is parsed`() {
        val json = """{"touchCount": 3}"""
        assertEquals(RawGesture.MultiTouch(touchCount = 3), Json.decodeFromString<RawGesture.MultiTouch>(json))
    }

    @Test
    fun `tap or swipe is parsed`() {
        val json = """{"x": 10, "y": 20, "deltaX": -30, "deltaY": 40, "time": 1700000000000, "scrollDirection": "hv"}"""
        val expected =
            RawGesture.TapOrSwipe(x = 10, y = 20, deltaX = -30, deltaY = 40, time = 1700000000000, scrollDirection = "hv")
        assertEquals(expected, Json.decodeFromString<RawGesture.TapOrSwipe>(json))
    }

    @Test
    fun `null scroll direction is parsed`() {
        val json = """{"x": 0, "y": 0, "deltaX": 0, "deltaY": 0, "time": 0, "scrollDirection": null}"""
        assertNull(Json.decodeFromString<RawGesture.TapOrSwipe>(json).scrollDirection)
    }

    @Test
    fun `missing required parameters throw`() {
        val json = """{"x": 100, "time": 0}"""
        assertFailsWith<SerializationException> { Json.decodeFromString<RawGesture.TapOrSwipe>(json) }
    }

    @Test
    fun `null required parameters throw`() {
        val json = """{"x": null, "y": 0, "deltaX": 0, "deltaY": 0, "time": 0, "scrollDirection": null}"""
        assertFailsWith<SerializationException> { Json.decodeFromString<RawGesture.TapOrSwipe>(json) }
    }

    @Test
    fun `fractional positions throw`() {
        val json = """{"x": 1.5, "y": 0, "deltaX": 0, "deltaY": 0, "time": 0, "scrollDirection": null}"""
        assertFailsWith<SerializationException> { Json.decodeFromString<RawGesture.TapOrSwipe>(json) }
    }

    @Test
    fun `missing touch count throws`() {
        assertFailsWith<SerializationException> { Json.decodeFromString<RawGesture.MultiTouch>("{}") }
    }
}
