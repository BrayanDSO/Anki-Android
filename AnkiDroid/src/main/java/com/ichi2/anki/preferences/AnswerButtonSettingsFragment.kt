// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import anki.i18n.FormatTimespanRequest
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.preferences.components.PreferenceSwitchRow
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.reviewer.AnswerArea
import com.ichi2.anki.ui.windows.reviewer.AnswerButtonsNextTime
import com.ichi2.anki.ui.windows.reviewer.answerAreaHeight
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.compose.theme.AnkiDroidTheme
import kotlin.math.roundToInt
import androidx.appcompat.R as AppCompatR

private const val MAX_BUTTON_SIZE = 200

class AnswerButtonSettingsFragment : Fragment() {
    private var buttonSize by mutableIntStateOf(100)
    private var useThemeColors by mutableStateOf(false)
    private var showButtons by mutableStateOf(true)
    private var hideHardAndEasy by mutableStateOf(false)
    private var showIntervals by mutableStateOf<Boolean?>(null)
    private var sampleIntervals by mutableStateOf<AnswerButtonsNextTime?>(null)
    private var savingIntervals by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buttonSize = Prefs.newStudyScreenAnswerButtonSize
        useThemeColors = Prefs.answerButtonsUseThemeColors
        showButtons = Prefs.showAnswerButtons
        hideHardAndEasy = Prefs.hideHardAndEasyButtons
        launchCatchingTask {
            sampleIntervals =
                withCol {
                    fun format(seconds: Float): String = backend.formatTimespan(seconds, FormatTimespanRequest.Context.ANSWER_BUTTONS)

                    AnswerButtonsNextTime(
                        again = format(60f),
                        hard = format(360f),
                        good = format(600f),
                        easy = format(345_600f),
                    )
                }
            showIntervals = CollectionPreferences.getShowIntervalOnButtons()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(CommonString.answer_buttons)) },
                                navigationIcon = {
                                    IconButton(onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_baseline_arrow_back_24),
                                            contentDescription = stringResource(AppCompatR.string.abc_action_bar_up_description),
                                        )
                                    }
                                },
                            )
                        },
                    ) { padding ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .consumeWindowInsets(padding)
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 16.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(answerAreaHeight(MAX_BUTTON_SIZE)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (showButtons) {
                                    AnswerArea(
                                        isAnswerShown = true,
                                        nextTimes = sampleIntervals.takeIf { showIntervals == true },
                                        hideHardAndEasy = hideHardAndEasy,
                                        buttonSizePercentage = buttonSize,
                                        useThemeColors = useThemeColors,
                                        onShowAnswer = {},
                                        onRating = {},
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(answerAreaHeight(buttonSize)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(painter = painterResource(R.drawable.ic_hide), contentDescription = null)
                                            Text(stringResource(CommonString.answer_buttons_hidden))
                                        }
                                    }
                                }
                            }
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, top = 8.dp, end = 16.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                PreferenceSwitchRow(
                                    title = stringResource(CommonString.show_answer_buttons),
                                    icon = R.drawable.ic_shelf_auto_hide,
                                    isChecked = showButtons,
                                    onCheckedChange = {
                                        Prefs.showAnswerButtons = it
                                        showButtons = it
                                    },
                                )
                                PreferenceSwitchRow(
                                    title = CollectionManager.TR.preferencesShowNextReviewTimeAboveAnswer(),
                                    icon = R.drawable.ic_running_clock,
                                    isChecked = showIntervals == true,
                                    enabled = showButtons && showIntervals != null && !savingIntervals,
                                    onCheckedChange = ::setIntervals,
                                )
                                PreferenceSwitchRow(
                                    title = stringResource(CommonString.hide_hard_and_easy),
                                    icon = R.drawable.ic_remove_done,
                                    isChecked = hideHardAndEasy,
                                    enabled = showButtons,
                                    onCheckedChange = {
                                        Prefs.hideHardAndEasyButtons = it
                                        hideHardAndEasy = it
                                    },
                                )
                                PreferenceSwitchRow(
                                    title = stringResource(CommonString.answer_buttons_use_theme_colors),
                                    icon = R.drawable.ic_palette,
                                    isChecked = useThemeColors,
                                    enabled = showButtons,
                                    onCheckedChange = {
                                        Prefs.answerButtonsUseThemeColors = it
                                        useThemeColors = it
                                    },
                                )
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                ) {
                                    val percentage = stringResource(CommonString.percentage, buttonSize)
                                    val interactionSource = remember { MutableInteractionSource() }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(stringResource(CommonString.button_size), modifier = Modifier.weight(1f))
                                        Text(percentage)
                                    }
                                    Slider(
                                        value = buttonSize.toFloat(),
                                        onValueChange = { buttonSize = it.roundToInt() },
                                        onValueChangeFinished = { Prefs.newStudyScreenAnswerButtonSize = buttonSize },
                                        enabled = showButtons,
                                        valueRange = 100f..MAX_BUTTON_SIZE.toFloat(),
                                        steps = 9,
                                        interactionSource = interactionSource,
                                        thumb = {
                                            Label(
                                                label = { PlainTooltip { Text(percentage) } },
                                                interactionSource = interactionSource,
                                            ) {
                                                SliderDefaults.Thumb(interactionSource = interactionSource, enabled = showButtons)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    private fun setIntervals(checked: Boolean) {
        if (savingIntervals) return
        val previous = showIntervals
        showIntervals = checked
        savingIntervals = true
        launchCatchingTask {
            var saved = false
            try {
                CollectionPreferences.setShowIntervalsOnButtons(checked)
                saved = true
            } finally {
                if (!saved) showIntervals = previous
                savingIntervals = false
            }
        }
    }
}
