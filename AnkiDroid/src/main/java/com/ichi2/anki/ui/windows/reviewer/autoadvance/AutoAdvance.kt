// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.autoadvance

import com.ichi2.anki.asyncIO
import com.ichi2.anki.libanki.Card
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Implementation of the `Auto Advance` deck options
 *
 * A timer (in seconds) can be set to automatically trigger an action after it runs out,
 * either in the question side ([QuestionAction]) or in the answer side ([AnswerAction]).
 *
 * If a timer is set to 0, the corresponding action is not triggered.
 *
 * @see AutoAdvanceSettings
 */
class AutoAdvance(
    private val scope: CoroutineScope,
    private val listener: ActionListener,
    initialCard: Deferred<Card>,
    private val awaitMediaIdle: suspend () -> Unit,
) {
    /**
     * Listens to the `Auto Advance` actions set in Deck options,
     * which can be either a [QuestionAction] or a [AnswerAction].
     */
    fun interface ActionListener {
        suspend fun onAutoAdvanceAction(action: AutoAdvanceAction)
    }

    var isEnabled = false
        @Synchronized get

        @Synchronized set(value) {
            field = value
            if (!value) {
                cancelPendingAction()
            }
        }
    private var pendingAction: Job? = null

    @Volatile
    private var settings =
        scope.asyncIO {
            AutoAdvanceSettings.createInstance(initialCard.await().currentDeckId())
        }

    @Synchronized
    fun cancelPendingAction() {
        pendingAction?.cancel()
        pendingAction = null
    }

    @Synchronized
    fun onCardChange(card: Card) {
        cancelPendingAction()
        settings =
            scope.asyncIO {
                AutoAdvanceSettings.createInstance(card.currentDeckId())
            }
    }

    suspend fun onShowQuestion() = schedule { durationToShowQuestionFor to questionAction }

    suspend fun onShowAnswer() = schedule { durationToShowAnswerFor to answerAction }

    private suspend fun schedule(select: AutoAdvanceSettings.() -> Pair<Duration, AutoAdvanceAction>) {
        val settings = settings
        val waitForAudio = settings.await().waitForAudio
        val (duration, action) = settings.await().select()
        synchronized(this) {
            cancelPendingAction()
            // the card changed while its settings loaded: the new card schedules its own action
            if (settings !== this.settings || !duration.isPositive() || !isEnabled) return

            pendingAction =
                scope.launch {
                    // the timer starts once the media of the side being shown ends
                    if (waitForAudio) awaitMediaIdle()
                    delay(duration)
                    listener.onAutoAdvanceAction(action)
                }
        }
    }
}
