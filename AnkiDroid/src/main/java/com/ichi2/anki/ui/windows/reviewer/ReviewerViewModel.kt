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

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Ease
import com.ichi2.anki.cardviewer.SoundPlayer
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.previewer.CardViewerViewModel
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.libanki.sched.CurrentQueueState
import com.ichi2.libanki.undoableOp
import kotlinx.coroutines.flow.MutableSharedFlow

class ReviewerViewModel(soundPlayer: SoundPlayer) : CardViewerViewModel(soundPlayer) {

    private var queueState: CurrentQueueState? = null
    var isQueueFinishedFlow = MutableSharedFlow<Boolean>()

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
        }
    }

    fun answerAgain() = answerCard(Ease.AGAIN)
    fun answerHard() = answerCard(Ease.HARD)
    fun answerGood() = answerCard(Ease.GOOD)
    fun answerEasy() = answerCard(Ease.EASY)

    /* *********************************************************************************************
    *************************************** Internal methods ***************************************
    ********************************************************************************************* */

    private fun answerCard(ease: Ease) {
        launchCatchingIO {
            queueState?.let {
                undoableOp { sched.answerCard(it, ease.value) }
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

    companion object {
        fun factory(soundPlayer: SoundPlayer): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    ReviewerViewModel(soundPlayer)
                }
            }
        }
    }
}
