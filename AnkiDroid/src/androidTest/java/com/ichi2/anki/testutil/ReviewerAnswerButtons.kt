// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/** Finds the visible label, which can be a separate accessibility node inside the button. */
fun awaitReviewerAnswerButton(
    @StringRes labelId: Int,
): UiObject2 {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val label = instrumentation.targetContext.getString(labelId)
    val selector = By.text(Pattern.compile("(?:[^\\n]*\\n)?" + Pattern.quote(label)))
    return assertNotNull(
        UiDevice.getInstance(instrumentation).wait(Until.findObject(selector), 30.seconds.inWholeMilliseconds),
        "Reviewer button '$label' did not appear",
    )
}
