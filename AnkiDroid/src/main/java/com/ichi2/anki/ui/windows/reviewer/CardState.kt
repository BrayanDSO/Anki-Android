// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import com.ichi2.anki.Flag
import com.ichi2.anki.previewer.TypeAnswer

/** What the study screen shows about the card being studied */
data class CardState(
    val isMarked: Boolean = false,
    val flag: Flag = Flag.NONE,
    val canBuryNote: Boolean = true,
    val canSuspendNote: Boolean = true,
    val counts: StudyCounts = StudyCounts(),
    val typeAnswer: TypeAnswer? = null,
    val nextTimes: AnswerButtonsNextTime? = null,
)
