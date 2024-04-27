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
import androidx.activity.result.ActivityResult
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import anki.collection.OpChanges
import anki.frontend.SetSchedulingStatesRequest
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Ease
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.asyncIO
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.pages.CardInfoDestination
import com.ichi2.anki.pages.DeckOptionsDestination
import com.ichi2.anki.pages.PostRequestHandler
import com.ichi2.anki.previewer.CardViewerViewModel
import com.ichi2.anki.previewer.NoteEditorDestination
import com.ichi2.anki.previewer.UserAction
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.libanki.Card
import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.note
import com.ichi2.libanki.redo
import com.ichi2.libanki.sched.CurrentQueueState
import com.ichi2.libanki.undo
import com.ichi2.libanki.undoableOp
import com.ichi2.libanki.utils.TimeManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class ReviewerViewModel(cardMediaPlayer: CardMediaPlayer) :
    CardViewerViewModel(cardMediaPlayer),
    PostRequestHandler,
    ChangeManager.Subscriber {

    private var queueState: CurrentQueueState? = null
    var isQueueFinishedFlow = MutableSharedFlow<Boolean>()

    val againNextTime: MutableStateFlow<String?> = MutableStateFlow(null)
    val hardNextTime: MutableStateFlow<String?> = MutableStateFlow(null)
    val goodNextTime: MutableStateFlow<String?> = MutableStateFlow(null)
    val easyNextTime: MutableStateFlow<String?> = MutableStateFlow(null)

    private val shouldShowNextTimes: Deferred<Boolean> = asyncIO {
        withCol { config.get("estTimes") ?: true }
    }
    override var currentCard: Deferred<Card> = asyncIO {
        updateQueueState()
        queueState!!.topCard
    }

    private val server = AnkiServer(this).also { it.start() }
    private val stateMutationKey = TimeManager.time.intTimeMS().toString()
    val statesMutationEval = MutableSharedFlow<String>()

    /**
     * A flag that determines if the SchedulingStates in CurrentQueueState are
     * safe to persist in the database when answering a card. This is used to
     * ensure that the custom JS scheduler has persisted its SchedulingStates
     * back to the Reviewer before we save it to the database. If the custom
     * scheduler has not been configured, then it is safe to immediately set
     * this to true.
     *
     * This flag should be set to false when we show the front of the card
     * and only set to true once we know the custom scheduler has finished its
     * execution, or set to true immediately if the custom scheduler has not
     * been configured.
     */
    private var statesMutated = true

    val isMarked = MutableStateFlow(false)

    val undoTitleFlow = MutableStateFlow(TR.undoUndo())
    val isUndoAvailableFlow = MutableStateFlow(false)
    val redoTitleFlow = MutableStateFlow(TR.undoRedo())
    val isRedoAvailableFlow = MutableStateFlow(false)

    val snackbarMessageFlow = MutableSharedFlow<String>()

    init {
        ChangeManager.subscribe(this)

        launchCatchingIO {
            updateUndoRedo()
        }
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
                showQuestion()
                loadAndPlaySounds(CardSide.QUESTION)
            }
        }
    }

    fun showAnswer() {
        launchCatchingIO {
            while (!statesMutated) {
                delay(50)
            }
            showAnswerInternal()
            loadAndPlaySounds(CardSide.ANSWER)
            updateNextTimes()
        }
    }

    fun answerAgain() = answerCard(Ease.AGAIN)
    fun answerHard() = answerCard(Ease.HARD)
    fun answerGood() = answerCard(Ease.GOOD)
    fun answerEasy() = answerCard(Ease.EASY)

    fun onStateMutationCallback() {
        statesMutated = true
    }

    suspend fun getNoteEditorDestination(): NoteEditorDestination =
        NoteEditorDestination(currentCard.await().id)

    suspend fun getCardInfoDestination(): CardInfoDestination =
        CardInfoDestination(currentCard.await().id)

    suspend fun getDeckOptionsDestination(): DeckOptionsDestination {
        val deckId = withCol { decks.getCurrentId() }
        val isFiltered = withCol { decks.isFiltered(deckId) }
        return DeckOptionsDestination(deckId, isFiltered)
    }

    fun toggleMark() {
        launchCatchingIO {
            val card = currentCard.await()
            val note = withCol { card.note() }
            NoteService.toggleMark(note)
            isMarked.emit(NoteService.isMarked(note))
        }
    }

    fun undo() {
        viewModelScope.launch {
            val changes = undoableOp(ReviewerOp.UNDO) {
                undo()
            }
            val message = if (changes.operation.isEmpty()) {
                TR.actionsNothingToUndo()
            } else {
                TR.undoActionUndone(changes.operation)
            }
            snackbarMessageFlow.emit(message)
        }
    }

    fun redo() {
        viewModelScope.launch {
            val changes = undoableOp(ReviewerOp.REDO) {
                redo()
            }
            val message = if (changes.operation.isEmpty()) {
                TR.actionsNothingToRedo()
            } else {
                TR.undoRedoAction(changes.operation)
            }
            snackbarMessageFlow.emit(message)
        }
    }

    fun handleEditCardResult(result: ActivityResult) {
        if (result.data?.getBooleanExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, false) == true ||
            result.data?.getBooleanExtra(NoteEditor.NOTE_CHANGED_EXTRA_KEY, false) == true
        ) {
            Timber.v("handleEditCardResult()")
            launchCatchingIO {
                updateCurrentCard()
            }
        }
    }

    fun userAction(@UserAction number: Int) {
        launchCatchingIO {
            eval.emit("javascript: ankidroid.userAction($number);")
        }
    }

    // TODO dialogo de confirmação se quer deletar
    fun deleteNote() {
        launchCatchingIO {
            val card = currentCard.await()
            val noteCount = undoableOp {
                removeNotes(cids = listOf(card.id))
            }.count
            snackbarMessageFlow.emit(TR.browsingCardsDeleted(noteCount))
        }
    }

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
        cardMediaPlayer.loadCardSounds(currentCard.await())
        cardMediaPlayer.playAllSoundsForSide(side)
    }

    override suspend fun showQuestion() {
        super.showQuestion()
        runStateMutationHook()
    }

    private suspend fun updateQueueState() {
        queueState = withCol {
            sched.currentQueueState()
        }
    }

    private suspend fun updateCurrentCard() {
        updateQueueState()
        val state = queueState
        if (state == null) {
            isQueueFinishedFlow.emit(true)
        } else {
            currentCard = CompletableDeferred(state.topCard)
            showQuestion()
            loadAndPlaySounds(CardSide.QUESTION)
        }
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

    override suspend fun handlePostRequest(uri: String, bytes: ByteArray): ByteArray {
        return if (uri.startsWith(AnkiServer.ANKI_PREFIX)) {
            when (uri.substring(AnkiServer.ANKI_PREFIX.length)) {
                "getSchedulingStatesWithContext" -> getSchedulingStatesWithContext()
                "setSchedulingStates" -> setSchedulingStates(bytes)
                else -> byteArrayOf()
            }
        } else {
            byteArrayOf()
        }
    }

    private suspend fun runStateMutationHook() {
        val state = queueState ?: return
        val js = state.customSchedulingJs
        if (js.isEmpty()) {
            statesMutated = true
            return
        }
        statesMutated = false
        statesMutationEval.emit(
            "anki.mutateNextCardStates('$stateMutationKey', async (states, customData, ctx) => { $js });"
        )
    }

    private fun getSchedulingStatesWithContext(): ByteArray {
        val state = queueState ?: return ByteArray(0)
        return state.schedulingStatesWithContext().toBuilder()
            .mergeStates(
                state.states.toBuilder().mergeCurrent(
                    state.states.current.toBuilder()
                        .setCustomData(state.topCard.toBackendCard().customData).build()
                ).build()
            )
            .build()
            .toByteArray()
    }

    private fun setSchedulingStates(bytes: ByteArray): ByteArray {
        val state = queueState ?: return ByteArray(0)
        val req = SetSchedulingStatesRequest.parseFrom(bytes)
        if (req.key == stateMutationKey) {
            state.states = req.states
        }
        return ByteArray(0)
    }

    companion object {
        fun factory(cardMediaPlayer: CardMediaPlayer): ViewModelProvider.Factory {
            return viewModelFactory {
                initializer {
                    ReviewerViewModel(cardMediaPlayer)
                }
            }
        }

        fun getAnswerButtonText(title: String, nextTime: String?): CharSequence {
            return if (nextTime != null) {
                buildSpannedString {
                    inSpans(RelativeSizeSpan(0.7F)) {
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

    private suspend fun updateUndoRedo() {
        undoTitleFlow.emit(withCol { undoLabel() } ?: TR.undoUndo())
        redoTitleFlow.emit(withCol { redoLabel() } ?: TR.undoRedo())

        isUndoAvailableFlow.emit(withCol { undoAvailable() })
        isRedoAvailableFlow.emit(withCol { redoAvailable() })
    }

    override suspend fun opExecuted(changes: OpChanges, handler: Any?) {
        // update undo
        updateUndoRedo()
        if (changes.card || changes.note) {
            updateCurrentCard()
        }
    }
}

enum class ReviewerOp {
    UNDO,
    REDO,
    DELETE
}
