// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.getColorFromAttr
import com.ichi2.anki.utils.ext.usingStyledAttributes
import com.google.android.material.R as MaterialR
import com.ichi2.anki.common.android.R as CommonR

@Composable
internal fun AnswerArea(
    isAnswerShown: Boolean,
    nextTimes: AnswerButtonsNextTime?,
    hideHardAndEasy: Boolean,
    buttonSizePercentage: Int,
    onShowAnswer: () -> Unit,
    onRating: (Rating) -> Unit,
    useThemeColors: Boolean = false,
) {
    val context = LocalContext.current
    val themeColors = MaterialTheme.colorScheme.takeIf { useThemeColors }
    val buttonHeight = answerAreaHeight(buttonSizePercentage)

    Box(
        Modifier
            .fillMaxWidth()
            .height(buttonHeight),
    ) {
        if (isAnswerShown) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (button in RatingButton.entries) {
                    if (hideHardAndEasy && (button.rating == Rating.HARD || button.rating == Rating.EASY)) continue
                    ReviewerAnswerButton(
                        label = stringResource(button.label),
                        background = themeColors?.primaryContainer ?: Color(getColorFromAttr(context, button.background)),
                        foreground = themeColors?.onPrimaryContainer ?: Color(getColorFromAttr(context, button.foreground)),
                        onClick = { onRating(button.rating) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        nextTime = nextTimes?.let(button.nextTime),
                    )
                }
            }
        } else {
            ReviewerAnswerButton(
                label = stringResource(CommonR.string.show_answer),
                background = themeColors?.primaryContainer ?: Color(getColorFromAttr(context, CommonR.attr.showAnswerButtonBackground)),
                foreground = themeColors?.onPrimaryContainer ?: Color.White,
                onClick = onShowAnswer,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun answerAreaHeight(buttonSizePercentage: Int): Dp {
    val context = LocalContext.current
    val minHeight =
        with(LocalDensity.current) {
            context.usingStyledAttributes(null, intArrayOf(MaterialR.attr.minTouchTargetSize)) {
                getDimension(0, 48.dp.toPx()).toDp()
            }
        }
    return minHeight * (buttonSizePercentage.coerceAtLeast(100) / 100f)
}

@Composable
private fun ReviewerAnswerButton(
    label: String,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    nextTime: String? = null,
) {
    val context = LocalContext.current
    val fontSize = with(LocalDensity.current) { dimensionResource(R.dimen.reviewer_answer_button_textSize).toSp() }
    val text =
        buildAnnotatedString {
            if (nextTime != null) {
                withStyle(SpanStyle(fontSize = fontSize * 0.8f)) { append(nextTime) }
                append('\n')
            }
            append(label)
        }
    val (elevation, strokeWidth) =
        context.usingStyledAttributes(null, intArrayOf(CommonR.attr.studyScreenElevation, CommonR.attr.widgetStrokeWidth)) {
            getDimension(0, 0f) to getDimension(1, 0f)
        }
    ElevatedButton(
        onClick = onClick,
        modifier = modifier.padding(),
        shape = RoundedCornerShape(dimensionResource(R.dimen.answer_button_corner_radius)),
        colors = ButtonDefaults.elevatedButtonColors(containerColor = background, contentColor = foreground),
        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = with(LocalDensity.current) { elevation.toDp() }),
        border =
            if (strokeWidth > 0f) {
                BorderStroke(
                    width = with(LocalDensity.current) { strokeWidth.toDp() },
                    color = Color(getColorFromAttr(context, CommonR.attr.widgetStrokeColor)),
                )
            } else {
                null
            },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(text = text, fontSize = fontSize, textAlign = TextAlign.Center)
    }
}

private enum class RatingButton(
    val rating: Rating,
    val label: Int,
    val background: Int,
    val foreground: Int,
    val nextTime: (AnswerButtonsNextTime) -> String,
) {
    AGAIN(
        Rating.AGAIN,
        CommonR.string.ease_button_again,
        CommonR.attr.againBackgroundColor,
        CommonR.attr.againTextColor,
        AnswerButtonsNextTime::again,
    ),
    HARD(
        Rating.HARD,
        CommonR.string.ease_button_hard,
        CommonR.attr.hardBackgroundColor,
        CommonR.attr.hardTextColor,
        AnswerButtonsNextTime::hard,
    ),
    GOOD(
        Rating.GOOD,
        CommonR.string.ease_button_good,
        CommonR.attr.goodBackgroundColor,
        CommonR.attr.goodTextColor,
        AnswerButtonsNextTime::good,
    ),
    EASY(
        Rating.EASY,
        CommonR.string.ease_button_easy,
        CommonR.attr.easyBackgroundColor,
        CommonR.attr.easyTextColor,
        AnswerButtonsNextTime::easy,
    ),
}
