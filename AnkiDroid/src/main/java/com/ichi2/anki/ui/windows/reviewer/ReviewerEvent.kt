// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.common.destinations.Destination
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NoteId

/** Something the study screen should do once */
sealed interface ReviewerEvent {
    data class Finish(
        val result: Int,
    ) : ReviewerEvent

    data class Feedback(
        val message: String,
    ) : ReviewerEvent

    data class Navigate(
        val destination: Destination,
    ) : ReviewerEvent

    data class EditTags(
        val noteId: NoteId,
    ) : ReviewerEvent

    data class SetDueDate(
        val cardId: CardId,
    ) : ReviewerEvent

    data object ResetProgress : ReviewerEvent

    data class AnswerFeedback(
        val rating: Rating,
    ) : ReviewerEvent

    data class TimeboxReached(
        val timebox: Collection.TimeboxReached,
    ) : ReviewerEvent

    data object PageUp : ReviewerEvent

    data object PageDown : ReviewerEvent
}
