// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import com.ichi2.anki.common.android.R.string as CommonString

@RunWith(AndroidJUnit4::class)
class AnswerAreaTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `show answer switches to rating buttons and each button reports its rating`() {
        val isAnswerShown = mutableStateOf(false)
        val ratings = mutableListOf<Rating>()
        val ratingLabels =
            mapOf(
                Rating.AGAIN to CommonString.ease_button_again,
                Rating.HARD to CommonString.ease_button_hard,
                Rating.GOOD to CommonString.ease_button_good,
                Rating.EASY to CommonString.ease_button_easy,
            )
        composeRule.setContent {
            AnkiDroidTheme {
                AnswerArea(
                    isAnswerShown = isAnswerShown.value,
                    nextTimes = null,
                    hideHardAndEasy = false,
                    buttonSizePercentage = 200,
                    onShowAnswer = { isAnswerShown.value = true },
                    onRating = { ratings += it },
                )
            }
        }

        val showAnswer = composeRule.onNodeWithText(targetContext.getString(CommonString.show_answer))
        for (label in ratingLabels.values) {
            composeRule.onNodeWithText(targetContext.getString(label)).assertDoesNotExist()
        }
        showAnswer.assertHeightIsAtLeast(88.dp)
        val buttonHeight = showAnswer.getUnclippedBoundsInRoot().let { it.bottom - it.top }
        showAnswer.performClick()
        showAnswer.assertDoesNotExist()

        for (label in ratingLabels.values) {
            val button = composeRule.onNodeWithText(targetContext.getString(label))
            assertEquals(buttonHeight, button.getUnclippedBoundsInRoot().let { it.bottom - it.top })
            button.performClick()
        }
        assertEquals(ratingLabels.keys.toList(), ratings)
    }

    @Test
    fun `two button mode shows only again and good with their scheduling intervals`() {
        composeRule.setContent {
            AnkiDroidTheme {
                AnswerArea(
                    isAnswerShown = true,
                    nextTimes = AnswerButtonsNextTime(again = "1m", hard = "2m", good = "3m", easy = "4m"),
                    hideHardAndEasy = true,
                    buttonSizePercentage = 100,
                    onShowAnswer = {},
                    onRating = {},
                )
            }
        }

        composeRule.onNodeWithText("1m\n" + targetContext.getString(CommonString.ease_button_again)).assertIsDisplayed()
        composeRule.onNodeWithText("3m\n" + targetContext.getString(CommonString.ease_button_good)).assertIsDisplayed()
        composeRule.onNodeWithText(targetContext.getString(CommonString.ease_button_hard), substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(targetContext.getString(CommonString.ease_button_easy), substring = true).assertDoesNotExist()
    }
}
