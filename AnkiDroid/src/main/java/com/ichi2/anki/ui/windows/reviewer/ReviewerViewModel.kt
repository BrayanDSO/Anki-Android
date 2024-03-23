/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.text.style.RelativeSizeSpan
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.AGAIN
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.EASY
import com.ichi2.anki.Ease
import com.ichi2.anki.GOOD
import com.ichi2.anki.HARD
import com.ichi2.anki.asyncIO
import com.ichi2.anki.cardviewer.SoundPlayer
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.previewer.CardViewerViewModel
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.libanki.sched.CurrentQueueState
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class ReviewerViewModel(soundPlayer: SoundPlayer) : CardViewerViewModel(soundPlayer) {

    private var queueState: CurrentQueueState? = null
    var isQueueFinishedFlow = MutableSharedFlow<Boolean>()

    val againNextTime: MutableStateFlow<String?> = MutableStateFlow(null)
    val hardNextTime: MutableStateFlow<String?> = MutableStateFlow(null)
    val goodNextTime: MutableStateFlow<String?> = MutableStateFlow(null)
    val easyNextTime: MutableStateFlow<String?> = MutableStateFlow(null)

    private val shouldShowNextTimes: Deferred<Boolean> = asyncIO {
        withCol { config.get("estTimes") ?: true }
    }

    /* *********************************************************************************************
    ************************ Public methods: meant to be used by the View **************************
    ********************************************************************************************* */

    override fun onPageFinished(isAfterRecreation: Boolean) {
        if (isAfterRecreation) {
            launchCatchingIO {
                if (showingAnswer.value) showAnswerInternal() else showQuestion()
            }
        } else {
            launchCatchingIO {
                updateCurrentCard()
            }
        }
    }

    fun showAnswer() {
        launchCatchingIO {
            showAnswerInternal()
            loadAndPlaySounds(CardSide.ANSWER)
            updateNextTimes()
        }
    }

    fun answerAgain() = answerCard(AGAIN)
    fun answerHard() = answerCard(HARD)
    fun answerGood() = answerCard(GOOD)
    fun answerEasy() = answerCard(EASY)

    /* *********************************************************************************************
    *************************************** Internal methods ***************************************
    ********************************************************************************************* */

    private fun answerCard(@Ease ease: Int) {
        launchCatchingIO {
            queueState?.let {
                undoableOp { sched.answerCard(it, ease) }
                updateCurrentCard()
            }
        }
    }

    private suspend fun loadAndPlaySounds(side: CardSide) {
        soundPlayer.loadCardSounds(currentCard)
        soundPlayer.playAllSoundsForSide(side)
    }

    private suspend fun updateCurrentCard() {
        queueState = withCol {
            sched.currentQueueState()
        }
        queueState?.let {
            currentCard = it.topCard
            showQuestion()
            loadAndPlaySounds(CardSide.QUESTION)
        } ?: isQueueFinishedFlow.emit(true)
    }

    // TODO
    override suspend fun typeAnsFilter(text: String): String {
        return text
    }

    private suspend fun updateNextTimes() {
        val state = queueState
        if (!shouldShowNextTimes.await() || state == null) return

        val (again, hard, good, easy) = withCol { sched.describeNextStates(state.states) }

        againNextTime.emit(again)
        hardNextTime.emit(hard)
        goodNextTime.emit(good)
        easyNextTime.emit(easy)
    }

    companion object {
        fun factory(soundPlayer: SoundPlayer): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    ReviewerViewModel(soundPlayer)
                }
            }
        }

        fun getAnswerButtonText(title: String, nextTime: String?): CharSequence {
            return if (nextTime != null) {
                buildSpannedString {
                    inSpans(RelativeSizeSpan(0.8F)) {
                        append(nextTime)
                    }
                    append("\n")
                    append(title)
                }
            } else {
                title
            }
        }
    }
}
