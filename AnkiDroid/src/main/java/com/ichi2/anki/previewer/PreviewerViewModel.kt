// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import androidx.annotation.VisibleForTesting
import anki.collection.OpChanges
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.utils.ViewModelSavedStateHandle
import com.ichi2.anki.utils.ext.flag
import com.ichi2.anki.utils.ext.setUserFlagForCards
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import timber.log.Timber

class PreviewerViewModel(
    savedStateHandle: ViewModelSavedStateHandle,
    @VisibleForTesting val selectedCardIds: List<Long>,
    initialIndex: Int,
) : CardViewerViewModel(savedStateHandle),
    ChangeManager.Subscriber {
    val currentIndex =
        savedStateHandle.getMutableStateFlow(
            KEY_CURRENT_INDEX,
            initialValue = initialIndex,
        )
    val backSideOnly = savedStateHandle.getMutableStateFlow(KEY_BACKSIDE_ONLY, false)
    val isMarked = MutableStateFlow(false)
    val flag: MutableStateFlow<Flag> = MutableStateFlow(Flag.NONE)

    val isBackButtonEnabled =
        combine(currentIndex, showingAnswer, backSideOnly) { index, showingAnswer, isBackSideOnly ->
            index != 0 || (showingAnswer && !isBackSideOnly)
        }
    val isNextButtonEnabled =
        combine(currentIndex, showingAnswer) { index, showingAnswer ->
            index != selectedCardIds.lastIndex || !showingAnswer
        }

    private val showAnswerOnReload get() = showingAnswer.value || backSideOnly.value

    private val currentCardId get() = selectedCardIds[currentIndex.value]

    init {
        ChangeManager.subscribe(this)
    }

    /* *********************************************************************************************
     ************************ Public methods: meant to be used by the View **************************
     ********************************************************************************************* */

    /** Call this after the webView has finished loading the page */
    @NeedsTest("16302 - a sound-only card on the back/flipped with 'don't keep activities'")
    override fun onPageFinished(isAfterRecreation: Boolean) {
        enqueue {
            if (isAfterRecreation) {
                val card = currentCard()
                showCard(card, showAnswerOnReload)
                // isAfterRecreation can either mean:
                // * after config change (ViewModel exists)
                // * after recreation (ViewModel did not exist)
                // if the ViewModel existed, we want to continue playing audio
                // if not, we want to setup the sound player
                cardMediaPlayer.ensureAvTagsLoaded(card)
            } else {
                // re-render the current card
                showCardAt(currentIndex.value)
            }
        }
    }

    fun toggleBackSideOnly() {
        Timber.v("toggleBackSideOnly() %b", !backSideOnly.value)
        enqueue {
            backSideOnly.emit(!backSideOnly.value)
            if (!backSideOnly.value && showingAnswer.value) {
                cardMediaPlayer.stop()
                showQuestion(currentCard())
                cardMediaPlayer.autoplayAllForSide(CardSide.QUESTION)
            } else if (backSideOnly.value && !showingAnswer.value) {
                cardMediaPlayer.stop()
                showAnswer(currentCard())
                cardMediaPlayer.autoplayAllForSide(CardSide.ANSWER)
            }
        }
    }

    fun toggleMark() {
        enqueue {
            val card = currentCard()
            val note = withCol { card.note(this@withCol) }
            NoteService.toggleMark(note)
            isMarked.emit(NoteService.isMarked(note))
        }
    }

    fun setFlag(flag: Flag) {
        enqueue {
            undoableOp {
                setUserFlagForCards(cids = listOf(currentCardId), flag = flag)
            }
            this.flag.emit(flag)
        }
    }

    fun toggleFlag(flag: Flag) {
        if (this@PreviewerViewModel.flag.value == flag) {
            setFlag(Flag.NONE)
        } else {
            setFlag(flag)
        }
    }

    /**
     * Shows the current card's answer
     * or the next question if the answer is already being shown
     */
    fun onNextButtonClick() {
        enqueue {
            if (!showingAnswer.value && !backSideOnly.value) {
                cardMediaPlayer.stop()
                showAnswer(currentCard())
                cardMediaPlayer.autoplayAllForSide(CardSide.ANSWER)
            } else if (currentIndex.value < selectedCardIds.lastIndex) {
                showCardAt(currentIndex.value + 1)
            }
        }
    }

    /**
     * Shows the previous' card question
     * or hides the current answer if the first card is being shown
     */
    fun onPreviousButtonClick() {
        enqueue {
            if (currentIndex.value > 0) {
                showCardAt(currentIndex.value - 1)
            } else if (showingAnswer.value && !backSideOnly.value) {
                cardMediaPlayer.stop()
                showQuestion(currentCard())
            }
        }
    }

    fun getNoteEditorDestination() = NoteEditorDestination.EditNoteFromPreviewer(currentCardId)

    fun replayMedia() {
        launchCatchingIO {
            val side = if (showingAnswer.value) SingleCardSide.BACK else SingleCardSide.FRONT
            cardMediaPlayer.replayAll(side)
        }
    }

    fun cardsCount() = selectedCardIds.count()

    /**
     * @param sliderPosition the value of the slider (i.e. Slider::value). It's NOT the card index.
     */
    fun onSliderChange(sliderPosition: Int) {
        val index = sliderPosition - 1
        if (index !in selectedCardIds.indices) return
        enqueue { showCardAt(index) }
    }

    /* *********************************************************************************************
     *************************************** Internal methods ***************************************
     ********************************************************************************************* */

    private suspend fun currentCard(): Card = withCol { getCard(currentCardId) }

    /** Sets [currentIndex] to [index] and renders its card. */
    private suspend fun showCardAt(index: Int) {
        currentIndex.value = index
        val card = currentCard()
        showCard(card, showAnswer = backSideOnly.value)
        val side: CardSide =
            when {
                backSideOnly.value -> CardSide.BOTH
                showingAnswer.value -> CardSide.ANSWER
                else -> CardSide.QUESTION
            }
        loadAndPlayMedia(card, side)
    }

    private suspend fun showCard(
        card: Card,
        showAnswer: Boolean,
    ) {
        if (showAnswer) showAnswer(card) else showQuestion(card)
        flag.emit(card.flag)
        updateMarkIcon(card)
    }

    private suspend fun updateMarkIcon(card: Card) {
        val isMarkedValue = withCol { card.note(this@withCol).hasTag(this@withCol, MARKED_TAG) }
        isMarked.emit(isMarkedValue)
    }

    /** From the [desktop code](https://github.com/ankitects/anki/blob/1ff55475b93ac43748d513794bcaabd5d7df6d9d/qt/aqt/reviewer.py#L671) */
    override suspend fun typeAnsFilter(
        card: Card,
        text: String,
        side: SingleCardSide,
    ): String =
        if (side == SingleCardSide.BACK) {
            val typeAnswer = TypeAnswer.getInstance(card, text)
            typeAnswer?.answerFilter() ?: text
        } else {
            TypeAnswer.removeTags(text)
        }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        enqueue {
            when {
                changes.noteText -> {
                    val card = currentCard()
                    updateMarkIcon(card)
                    if (showingAnswer.value) {
                        showAnswer(card)
                    } else {
                        showQuestion(card)
                    }
                }
                changes.card -> flag.emit(currentCard().flag)
            }
        }
    }

    companion object {
        private const val KEY_BACKSIDE_ONLY = "backsideOnly"
        private const val KEY_CURRENT_INDEX = "currentIndex"
    }
}
