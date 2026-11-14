// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import anki.collection.OpChanges
import anki.frontend.SetSchedulingStatesRequest
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_NO_MORE_CARDS
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.Flag
import com.ichi2.anki.Reviewer
import com.ichi2.anki.asyncIO
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.cardviewer.appendNoSuggestAttributes
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.destinations.BrowserDestination
import com.ichi2.anki.common.destinations.CardInfoDestination
import com.ichi2.anki.common.destinations.CardInfoDestination.EntryPoint
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.common.destinations.DeckOptionsEntry
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.StatisticsDestination
import com.ichi2.anki.launchCatchingIO
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.redoLabel
import com.ichi2.anki.libanki.sched.CurrentQueueState
import com.ichi2.anki.libanki.undoLabel
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.pages.PostRequestUri
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.CardViewerViewModel
import com.ichi2.anki.previewer.TypeAnswer
import com.ichi2.anki.previewer.typeAnsRe
import com.ichi2.anki.reviewer.BindingProcessor
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.ReviewerBinding
import com.ichi2.anki.servicelayer.MARKED_TAG
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.servicelayer.isBuryNoteAvailable
import com.ichi2.anki.servicelayer.isSuspendNoteAvailable
import com.ichi2.anki.tryRedo
import com.ichi2.anki.tryUndo
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AnswerAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AutoAdvance
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AutoAdvanceAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.QuestionAction
import com.ichi2.anki.utils.ext.answerCard
import com.ichi2.anki.utils.ext.cardStatsNoCardClean
import com.ichi2.anki.utils.ext.flag
import com.ichi2.anki.utils.ext.getLongOrNull
import com.ichi2.anki.utils.ext.setUserFlagForCards
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.intellij.lang.annotations.Language
import timber.log.Timber

class ReviewerViewModel(
    savedStateHandle: SavedStateHandle,
) : CardViewerViewModel(savedStateHandle),
    ChangeManager.Subscriber,
    BindingProcessor<ReviewerBinding, ViewerAction>,
    AutoAdvance.ActionListener {
    /** The card being studied. `null` if there is none: no more cards, or the timebox was reached */
    @Volatile
    private var session: Deferred<CardSession?> =
        asyncIO {
            withCol { sched.currentQueueState() }?.let { CardSession(it) }
        }
    private val repository = StudyScreenRepository()
    private var isInputFocused = false
    val undoLabelFlow = MutableStateFlow<String?>(null)
    val redoLabelFlow = MutableStateFlow<String?>(null)
    val cardState: StateFlow<CardState>
        field = MutableStateFlow(CardState(counts = savedStateHandle[KEY_COUNTS] ?: StudyCounts()))
    private val eventChannel = Channel<ReviewerEvent>(Channel.UNLIMITED)

    /** Kept while the screen isn't collecting them. To be collected by the screen only */
    val events = eventChannel.receiveAsFlow()
    val onCardUpdatedFlow = MutableSharedFlow<Unit>()
    val voiceRecorderEnabledFlow = MutableStateFlow(repository.isRecordVoiceEnabled)
    val whiteboardEnabledFlow = MutableStateFlow(repository.isWhiteboardEnabled)
    val replayVoiceFlow = MutableSharedFlow<Unit>()
    val statesMutationEvalFlow = MutableSharedFlow<String>()

    private val stateMutationKey = repository.generateStateMutationKey()
    private val stateMutationJs: Deferred<String> = asyncIO { repository.getCustomSchedulingJs() }

    private val autoAdvance =
        AutoAdvance(
            viewModelScope,
            this,
            asyncIO { session.await()?.card ?: Card(anki.cards.Card.getDefaultInstance()) },
            cardMediaPlayer::awaitIdle,
        )
    private val isHtmlTypeAnswerEnabled = repository.isHtmlTypeAnswerEnabled
    val answerTimer = AnswerTimer()

    /** What is only valid for the card being studied, so it is replaced along with it */
    private class CardSession(
        val state: CurrentQueueState,
    ) {
        val card get() = state.topCard

        /**
         * A flag that determines if the SchedulingStates in [state] are
         * safe to persist in the database when answering a card. This is used to
         * ensure that the custom JS scheduler has persisted its SchedulingStates
         * back to the Reviewer before we save it to the database.
         *
         * This flag is reset before we show the front of the card
         * and only completes once we know the custom scheduler has finished its
         * execution, or completes immediately if the custom scheduler has not
         * been configured.
         */
        var statesMutated = false

        /** Actions requested before [statesMutated], to run once it is set */
        val deferredActions = mutableListOf<ViewerAction>()
        var typedAnswer = ""
    }

    /** The card being studied, or `null` if there is none */
    private suspend fun currentCard(): Card? = session.await()?.card

    /**
     * @return the card being studied, if the custom scheduler has mutated its states.
     * If it hasn't yet, [action] is deferred until then and `null` is returned.
     * `null` is also returned if there is no card
     */
    private suspend fun mutatedSessionOrDefer(action: ViewerAction): CardSession? {
        val current = session.await() ?: return null
        if (!current.statesMutated) {
            current.deferredActions += action
            return null
        }
        return current
    }

    private suspend fun onStatesMutated() {
        val current = session.await() ?: return
        current.statesMutated = true
        val actions = current.deferredActions.toList()
        current.deferredActions.clear()
        for (action in actions) {
            // an action may replace the card, and the remaining ones were meant for it
            if (session.await() !== current) break
            performAction(action)
        }
    }

    val isAutoAdvanceEnabledFlow = MutableStateFlow(autoAdvance.isEnabled)
    private val shouldShowNextTimes = asyncIO { repository.getShouldShowNextTimes() }

    init {
        ChangeManager.subscribe(this)
        launchCatchingIO {
            withCol { startTimebox() }
            updateUndoAndRedoLabels()
            // The height of the answer buttons may increase if `Show button time` is enabled.
            // To ensure consistent height, load the times to match the height of the `Show answer`
            // button with the answer buttons.
            updateNextTimes()
        }
    }

    override fun onPageFinished(isAfterRecreation: Boolean) {
        Timber.v("ReviewerViewModel::onPageFinished %b", isAfterRecreation)
        if (isAfterRecreation) {
            enqueue {
                val card = currentCard() ?: return@enqueue
                // TODO handle "Don't keep activities"
                if (showingAnswer.value) {
                    showAnswer(card)
                    // on configuration change: the session is retained
                    // on process death: a new session exists. Call
                    // `runStateMutationHook` to ensure its statesMutated completes.
                    if (session.await()?.statesMutated == false) {
                        runStateMutationHook()
                    }
                } else {
                    showQuestion(card)
                    autoAdvance.onShowQuestion()
                }
            }
        } else {
            enqueue { updateCurrentCard() }
        }
    }

    /** Shows the next card, after the user chose to keep studying once the timebox was reached */
    fun continueAfterTimebox() {
        enqueue { updateCurrentCard() }
    }

    fun onShowAnswer() {
        executeAction(ViewerAction.SHOW_ANSWER)
    }

    fun answerCard(rating: Rating) {
        val action =
            when (rating) {
                Rating.AGAIN -> ViewerAction.ANSWER_AGAIN
                Rating.HARD -> ViewerAction.ANSWER_HARD
                Rating.GOOD -> ViewerAction.ANSWER_GOOD
                Rating.EASY -> ViewerAction.ANSWER_EASY
                Rating.UNRECOGNIZED -> return
            }
        executeAction(action)
    }

    suspend fun getCardId(): CardId? = currentCard()?.id

    /**
     * Sends an [eval] request to load the card answer, and updates components
     * with behavior specific to the `Answer` card side.
     *
     * @see showAnswer
     */
    private suspend fun showAnswerInternal() {
        Timber.v("ReviewerViewModel::onShowAnswer")
        val session = mutatedSessionOrDefer(ViewerAction.SHOW_ANSWER) ?: return

        updateNextTimes()
        showAnswer(session.card)
        loadAndPlayMedia(session.card, CardSide.ANSWER)
        autoAdvance.onShowAnswer()

        if (answerTimer.state.value == AnswerTimerState.Hidden) return
        val did = session.card.currentDeckId()
        val stopTimerOnAnswer = withCol { decks.configDictForDeckId(did) }.stopTimerOnAnswer
        if (stopTimerOnAnswer) {
            answerTimer.stop()
        }
    }

    private suspend fun toggleMark() {
        Timber.v("ReviewerViewModel::toggleMark")
        val card = currentCard() ?: return
        val note = withCol { card.note(this@withCol) }
        NoteService.toggleMark(note)
        cardState.update { it.copy(isMarked = NoteService.isMarked(note)) }
    }

    private suspend fun setFlag(flag: Flag) {
        Timber.v("ReviewerViewModel::setFlag")
        val card = currentCard() ?: return
        undoableOp {
            setUserFlagForCards(listOf(card.id), flag)
        }
        cardState.update { it.copy(flag = flag) }
    }

    private suspend fun toggleFlag(flag: Flag) {
        Timber.v("ReviewerViewModel::toggleFlag")
        if (flag == cardState.value.flag) {
            setFlag(Flag.NONE)
        } else {
            setFlag(flag)
        }
    }

    /** To be called whenever the text of the 'type in the answer' field changes */
    fun onTypedAnswerChanged(text: String) {
        enqueue { session.await()?.typedAnswer = text }
    }

    fun onStateMutationCallback() {
        enqueue { onStatesMutated() }
    }

    private suspend fun emitEditNoteDestination() {
        val cardId = (currentCard() ?: return).id
        Timber.i("Opening 'edit note' for card %d", cardId)
        eventChannel.send(ReviewerEvent.Navigate(NoteEditorDestination.EditNoteFromPreviewer(cardId)))
    }

    private suspend fun emitAddNoteDestination() {
        Timber.i("Launching 'add note'")
        eventChannel.send(ReviewerEvent.Navigate(NoteEditorDestination.AddNoteFromReviewer()))
    }

    private suspend fun emitCardInfoDestination() {
        val cardId = (currentCard() ?: return).id
        val destination = CardInfoDestination(cardId, EntryPoint.CURRENT_CARD_STUDY)
        Timber.i("Launching 'card info' for card %d", cardId)
        eventChannel.send(ReviewerEvent.Navigate(destination))
    }

    private suspend fun emitPreviousCardInfoDestination() {
        val previousCardId: CardId? = savedStateHandle.getLongOrNull(KEY_PREVIOUS_CARD_ID)
        if (previousCardId == null) {
            Timber.i("No previous answered card found, ignoring request for 'previous card info'")
            eventChannel.send(ReviewerEvent.Feedback(TR.cardStatsNoCardClean()))
            return
        }
        val destination = CardInfoDestination(previousCardId, EntryPoint.PREVIOUS_CARD_STUDY)
        Timber.i("Launching 'previous card info' for card %d", previousCardId)
        eventChannel.send(ReviewerEvent.Navigate(destination))
    }

    @NeedsTest("verify that we show the proper deck option targets for the current card")
    private suspend fun emitDeckOptionsDestination() {
        val deckId = withCol { decks.getCurrentId() }
        val card = currentCard() ?: return
        val options = getDeckOptionsTargets(deckId, card)
        val isFiltered = options.first { it.deckId == deckId }.isFiltered
        val destination = DeckOptionsDestination(deckId, isFiltered, options)
        Timber.i("Launching 'deck options' for deck %d", deckId)
        eventChannel.send(ReviewerEvent.Navigate(destination))
    }

    /**
     *  Builds the valid(all [DeckOptionsEntry] that have a proper deck name) list of
     *  [DeckOptionsEntry] from which the user can select one to see its deck options.
     *  @param currentDeckId the [DeckId] of the currently selected deck
     *  @param card current card shown in the study screen
     *  See https://github.com/ankitects/anki/blob/b8884bac72aa50fa1189fe0a5079a71574bc5043/qt/aqt/deckoptions.py#L83-L100
     *  for backend implementation and ordering of the entries.
     */
    private suspend fun getDeckOptionsTargets(
        currentDeckId: DeckId,
        card: Card,
    ): List<DeckOptionsEntry> {
        val extraDeckIds = mutableListOf(currentDeckId)
        if (card.oDid != 0L && card.oDid != currentDeckId) {
            extraDeckIds.add(card.oDid)
        }
        if (card.did != currentDeckId) {
            extraDeckIds.add(card.did)
        }
        return withCol {
            extraDeckIds
                .map { deckId ->
                    DeckOptionsEntry(
                        deckId = deckId,
                        name = decks.nameIfExists(deckId),
                        isFiltered = decks.isFiltered(deckId),
                    )
                }.filter { it.name != null }
                .sortedBy { it.isFiltered }
        }
    }

    private suspend fun emitBrowseDestination() {
        val deckId = withCol { decks.getCurrentId() }
        val cardId = (currentCard() ?: return).id
        val destination = BrowserDestination.ScrollToCard(deckId, cardId)
        Timber.i("Launching 'browse options' for deck %d", deckId)
        eventChannel.send(ReviewerEvent.Navigate(destination))
    }

    private suspend fun deleteNote() {
        val cardId = (currentCard() ?: return).id
        val noteCount =
            undoableOp(handler = this) {
                removeNotes(cardIds = listOf(cardId))
            }.count
        eventChannel.send(ReviewerEvent.Feedback(TR.browsingCardsDeleted(noteCount)))
        updateCurrentCard()
    }

    private suspend fun buryCard() {
        val cardId = (currentCard() ?: return).id
        val noteCount =
            undoableOp(handler = this) {
                sched.buryCards(cids = listOf(cardId))
            }.count
        eventChannel.send(ReviewerEvent.Feedback(TR.studyingCardsBuried(noteCount)))
        updateCurrentCard()
    }

    private suspend fun buryNote() {
        val noteId = (currentCard() ?: return).nid
        val noteCount =
            undoableOp(handler = this) {
                sched.buryNotes(nids = listOf(noteId))
            }.count
        eventChannel.send(ReviewerEvent.Feedback(TR.studyingCardsBuried(noteCount)))
        updateCurrentCard()
    }

    private suspend fun suspendCard() {
        val cardId = (currentCard() ?: return).id
        undoableOp(handler = this) {
            sched.suspendCards(ids = listOf(cardId))
        }.count
        eventChannel.send(ReviewerEvent.Feedback(TR.studyingCardSuspended()))
        updateCurrentCard()
    }

    private suspend fun suspendNote() {
        val noteId = (currentCard() ?: return).nid
        undoableOp(handler = this) {
            sched.suspendNotes(ids = listOf(noteId))
        }
        eventChannel.send(ReviewerEvent.Feedback(TR.studyingNoteSuspended()))
        updateCurrentCard()
    }

    private suspend fun undo() {
        Timber.v("ReviewerViewModel::undo")
        eventChannel.send(ReviewerEvent.Feedback(tryUndo()))
    }

    private suspend fun redo() {
        Timber.v("ReviewerViewModel::redo")
        eventChannel.send(ReviewerEvent.Feedback(tryRedo()))
    }

    private suspend fun userAction(
        @Reviewer.UserAction number: Int,
    ) {
        eval.emit("ankidroid.userAction($number);")
    }

    fun stopAutoAdvance() {
        Timber.v("ReviewerViewModel::stopAutoAdvance")
        autoAdvance.cancelPendingAction()
    }

    private suspend fun toggleAutoAdvance() {
        Timber.v("ReviewerViewModel::toggleAutoAdvance")
        autoAdvance.isEnabled = !autoAdvance.isEnabled
        isAutoAdvanceEnabledFlow.value = autoAdvance.isEnabled
        val message =
            if (autoAdvance.isEnabled) {
                TR.actionsAutoAdvanceActivated()
            } else {
                TR.actionsAutoAdvanceDeactivated()
            }
        eventChannel.send(ReviewerEvent.Feedback(message))

        if (showingAnswer.value) {
            autoAdvance.onShowAnswer()
        } else {
            autoAdvance.onShowQuestion()
        }
    }

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        when (uri.ankidroidMethodName) {
            "focusin" -> {
                isInputFocused = true
                return byteArrayOf()
            }
            "focusout" -> {
                isInputFocused = false
                return byteArrayOf()
            }
            "typedAnswer" -> {
                onTypedAnswerChanged(bytes.decodeToString())
                return byteArrayOf()
            }
            "statesMutated" -> {
                onStateMutationCallback()
                return byteArrayOf()
            }
        }
        return when (uri.backendMethodName) {
            "getSchedulingStatesWithContext" -> getSchedulingStatesWithContext()
            "setSchedulingStates" -> setSchedulingStates(bytes)
            else -> super.handlePostRequest(uri, bytes)
        }
    }

    override suspend fun showQuestion(card: Card) {
        Timber.v("ReviewerViewModel::showQuestion")
        // 'Show answer' may be pressed while the question loads: reset before, not after.
        // If it was pressed earlier, it is already waiting on the pending signal: keep it.
        session.await()?.let {
            it.statesMutated = false
            // the HTML field is created again, empty
            if (isHtmlTypeAnswerEnabled) it.typedAnswer = ""
        }
        super.showQuestion(card)
        runStateMutationHook()
        updateMarkIcon()
        updateFlagIcon()
    }

    private suspend fun runStateMutationHook() {
        val js = stateMutationJs.await()
        if (js.isEmpty()) {
            // not run now: the card is still being shown
            enqueue { onStatesMutated() }
            return
        }
        // https://github.com/ankitects/anki/commit/bd88c6d352dc7aeb4a674029eab7bdda2a821a78
        statesMutationEvalFlow.emit(
            """
            anki.mutateNextCardStates('$stateMutationKey', async (states, customData, ctx) => { $js })
                .finally(() => fetch("ankidroid/statesMutated", { method: "POST" }));
            """,
        )
    }

    private suspend fun getSchedulingStatesWithContext(): ByteArray {
        val state = session.await()?.state ?: return ByteArray(0)
        return state
            .schedulingStatesWithContext()
            .toBuilder()
            .mergeStates(
                state.states
                    .toBuilder()
                    .mergeCurrent(
                        state.states.current
                            .toBuilder()
                            .setCustomData(state.topCard.customData)
                            .build(),
                    ).build(),
            ).build()
            .toByteArray()
    }

    private suspend fun setSchedulingStates(bytes: ByteArray): ByteArray {
        val state = session.await()?.state ?: return ByteArray(0)
        val req = SetSchedulingStatesRequest.parseFrom(bytes)
        if (req.key == stateMutationKey) {
            state.states = req.states
        }
        return ByteArray(0)
    }

    private suspend fun answerCardInternal(
        action: ViewerAction,
        rating: Rating,
    ) {
        Timber.v("ReviewerViewModel::answerCard")
        val session = mutatedSessionOrDefer(action) ?: return
        // as in Anki Desktop, a card can only be answered from its answer
        if (!showingAnswer.value) return
        val state = session.state
        val card = session.card
        val answer =
            withCol {
                sched.answerCard(
                    card = card,
                    states = state.states,
                    rating,
                )
            }

        undoableOp(handler = this) { sched.answerCard(answer) }
        eventChannel.send(ReviewerEvent.AnswerFeedback(rating))
        savedStateHandle[KEY_PREVIOUS_CARD_ID] = card.id

        val wasLeech = withCol { sched.stateIsLeech(answer.newState) }
        if (wasLeech) {
            withCol { card.load(this) }
            val isSuspended = card.queue.code < 0
            onLeech(isSuspended)
        }
        updateCurrentCard()
    }

    // https://github.com/ankitects/anki/blob/da907053460e2b78c31199f97bbea3cf3600f0c2/qt/aqt/reviewer.py#L954
    private suspend fun onLeech(isSuspended: Boolean) {
        Timber.i("ReviewerViewModel::onLeech (isSuspended = %b)", isSuspended)
        val message = StringBuilder(TR.studyingCardWasALeech())
        if (isSuspended) {
            message.append(" ")
            message.append(TR.studyingItHasBeenSuspended())
        }
        eventChannel.send(ReviewerEvent.Feedback(message.toString()))
    }

    private suspend fun updateMarkIcon() {
        Timber.v("ReviewerViewModel::updateMarkIcon")
        val card = currentCard() ?: return
        val isMarkedValue = withCol { card.note(this@withCol).hasTag(this@withCol, MARKED_TAG) }
        cardState.update { it.copy(isMarked = isMarkedValue) }
    }

    private suspend fun updateFlagIcon() {
        Timber.v("ReviewerViewModel::updateFlagIcon")
        val card = currentCard() ?: return
        cardState.update { it.copy(flag = card.flag) }
    }

    private suspend fun updateCurrentCard() {
        Timber.v("ReviewerViewModel::updateCurrentCard")
        val state = withCol { sched.currentQueueState() }
        val previousSession = session
        val newSession = state?.takeIf { it.timeboxReached == null }?.let { CardSession(it) }
        // the same card is being shown again: keep what was requested for it
        previousSession.await()?.takeIf { it.card.id == newSession?.card?.id }?.let {
            newSession?.deferredActions?.addAll(it.deferredActions)
        }
        session = CompletableDeferred(newSession)
        if (state == null) {
            eventChannel.send(ReviewerEvent.Finish(RESULT_NO_MORE_CARDS))
            return
        }
        state.timeboxReached?.let {
            eventChannel.send(ReviewerEvent.TimeboxReached(it))
            return
        }

        val card = state.topCard
        setupAnswerTimer(card)
        autoAdvance.onCardChange(card)
        onCardUpdatedFlow.emit(Unit) // must be before showQuestion()
        showQuestion(card)
        loadAndPlayMedia(card, CardSide.QUESTION)
        autoAdvance.onShowQuestion() // after the media starts, as it may wait for it
        cardState.update { it.copy(canBuryNote = isBuryNoteAvailable(card)) }
        cardState.update { it.copy(canSuspendNote = isSuspendNoteAvailable(card)) }
        val counts = StudyCounts(state)
        savedStateHandle[KEY_COUNTS] = counts
        cardState.update { it.copy(counts = counts) }
    }

    override suspend fun typeAnsFilter(
        card: Card,
        text: String,
        side: SingleCardSide,
    ): String {
        Timber.v("ReviewerViewModel::typeAnsFilter")
        val typeAnswer = TypeAnswer.getInstance(card, text)
        return if (side == SingleCardSide.BACK) {
            cardState.update { it.copy(typeAnswer = null) }
            typeAnswer?.answerFilter(session.await()?.typedAnswer ?: "") ?: text
        } else {
            cardState.update { it.copy(typeAnswer = typeAnswer) }
            if (isHtmlTypeAnswerEnabled) {
                typeAnswer?.let { typeAnsQuestionFilter(text, it) } ?: text
            } else {
                TypeAnswer.removeTags(text)
            }
        }
    }

    // https://github.com/ankitects/anki/blob/da907053460e2b78c31199f97bbea3cf3600f0c2/qt/aqt/reviewer.py#L704
    private fun typeAnsQuestionFilter(
        text: String,
        typeAnswer: TypeAnswer,
    ): String {
        val keyboardAttributes =
            buildString {
                if (typeAnswer.noSuggest) appendNoSuggestAttributes()
            }

        @Suppress("XmlDeprecatedElement") // upstream uses `<center>`
        @Language("HTML")
        val repl =
            """
            <center>
            <input type="text" id="typeans" onkeydown="ankidroid.onTypeAnswerKeyDown(event);" oninput="ankidroid.onTypeAnswerInput(event);"
               $keyboardAttributes
               style="font-family: '${typeAnswer.font}'; font-size: ${typeAnswer.fontSize}px;">
            </center>
            """.trimIndent()
        return typeAnsRe.replace(text, repl)
    }

    private suspend fun updateUndoAndRedoLabels() {
        Timber.v("ReviewerViewModel::updateUndoAndRedoLabels")
        undoLabelFlow.emit(withCol { undoLabel() })
        redoLabelFlow.emit(withCol { redoLabel() })
    }

    private suspend fun updateNextTimes() {
        Timber.v("ReviewerViewModel::updateNextTimes")
        if (!shouldShowNextTimes.await()) return
        val state = session.await()?.state ?: return

        val nextTimes = AnswerButtonsNextTime.from(state)
        cardState.update { it.copy(nextTimes = nextTimes) }
    }

    private suspend fun editNoteTags() {
        val noteId = (currentCard() ?: return).nid
        eventChannel.send(ReviewerEvent.EditTags(noteId))
    }

    fun onEditedTags(selectedTags: List<String>) {
        enqueue {
            val card = currentCard() ?: return@enqueue
            val note = withCol { card.note(this@withCol) }
            if (note.tags == selectedTags) {
                Timber.d("No changed tags")
                return@enqueue
            }

            val tagsString = selectedTags.joinToString(" ")
            withCol { note.setTagsFromStr(this@withCol, tagsString) }
            undoableOp {
                updateNote(note)
            }
        }
    }

    private suspend fun launchSetDueDate() {
        val cardId = (currentCard() ?: return).id
        eventChannel.send(ReviewerEvent.SetDueDate(cardId))
    }

    private suspend fun launchResetProgress() = eventChannel.send(ReviewerEvent.ResetProgress)

    private suspend fun setupAnswerTimer(card: Card) {
        val shouldShowTimer = withCol { card.shouldShowTimer(this@withCol) }
        val limitMs = withCol { card.timeLimit(this@withCol) }
        answerTimer.configureForCard(shouldShowTimer, limitMs)
    }

    private suspend fun replayMedia() {
        val side = if (showingAnswer.value) SingleCardSide.BACK else SingleCardSide.FRONT
        cardMediaPlayer.replayAll(side)
    }

    private fun toggleWhiteboard() {
        val newValue = !whiteboardEnabledFlow.value
        whiteboardEnabledFlow.value = newValue
        repository.isWhiteboardEnabled = newValue
    }

    private fun toggleRecordVoice() {
        val newValue = !voiceRecorderEnabledFlow.value
        voiceRecorderEnabledFlow.value = newValue
        repository.isRecordVoiceEnabled = newValue
    }

    fun executeAction(action: ViewerAction) {
        Timber.v("ReviewerViewModel::executeAction %s", action.name)
        val target = session
        enqueue {
            if (action !in cardIndependentActions && target.await()?.card?.id != session.await()?.card?.id) {
                Timber.i("Ignoring %s: the card it was requested for is no longer shown", action.name)
                return@enqueue
            }
            performAction(action)
        }
    }

    private suspend fun performAction(action: ViewerAction) {
        when (action) {
            ViewerAction.ADD_NOTE -> emitAddNoteDestination()
            ViewerAction.CARD_INFO -> emitCardInfoDestination()
            ViewerAction.PREVIOUS_CARD_INFO -> emitPreviousCardInfoDestination()
            ViewerAction.DECK_OPTIONS -> emitDeckOptionsDestination()
            ViewerAction.EDIT -> emitEditNoteDestination()
            ViewerAction.TAG -> editNoteTags()
            ViewerAction.DELETE -> deleteNote()
            ViewerAction.MARK -> toggleMark()
            ViewerAction.REDO -> redo()
            ViewerAction.UNDO -> undo()
            ViewerAction.RESCHEDULE_NOTE -> launchSetDueDate()
            ViewerAction.RESET_PROGRESS -> launchResetProgress()
            ViewerAction.TOGGLE_AUTO_ADVANCE -> toggleAutoAdvance()
            ViewerAction.BURY_NOTE -> buryNote()
            ViewerAction.BURY_CARD -> buryCard()
            ViewerAction.SUSPEND_NOTE -> suspendNote()
            ViewerAction.SUSPEND_CARD -> suspendCard()
            ViewerAction.UNSET_FLAG -> setFlag(Flag.NONE)
            ViewerAction.FLAG_RED -> setFlag(Flag.RED)
            ViewerAction.FLAG_ORANGE -> setFlag(Flag.ORANGE)
            ViewerAction.FLAG_BLUE -> setFlag(Flag.BLUE)
            ViewerAction.FLAG_GREEN -> setFlag(Flag.GREEN)
            ViewerAction.FLAG_PINK -> setFlag(Flag.PINK)
            ViewerAction.FLAG_TURQUOISE -> setFlag(Flag.TURQUOISE)
            ViewerAction.FLAG_PURPLE -> setFlag(Flag.PURPLE)
            ViewerAction.TOGGLE_FLAG_RED -> toggleFlag(Flag.RED)
            ViewerAction.TOGGLE_FLAG_ORANGE -> toggleFlag(Flag.ORANGE)
            ViewerAction.TOGGLE_FLAG_BLUE -> toggleFlag(Flag.BLUE)
            ViewerAction.TOGGLE_FLAG_GREEN -> toggleFlag(Flag.GREEN)
            ViewerAction.TOGGLE_FLAG_PINK -> toggleFlag(Flag.PINK)
            ViewerAction.TOGGLE_FLAG_TURQUOISE -> toggleFlag(Flag.TURQUOISE)
            ViewerAction.TOGGLE_FLAG_PURPLE -> toggleFlag(Flag.PURPLE)
            ViewerAction.SHOW_ANSWER -> if (!showingAnswer.value) showAnswerInternal()
            ViewerAction.ANSWER_AGAIN -> answerCardInternal(action, Rating.AGAIN)
            ViewerAction.ANSWER_HARD -> answerCardInternal(action, Rating.HARD)
            ViewerAction.ANSWER_GOOD -> answerCardInternal(action, Rating.GOOD)
            ViewerAction.ANSWER_EASY -> answerCardInternal(action, Rating.EASY)
            ViewerAction.SHOW_HINT -> eval.emit("ankidroid.showHint()")
            ViewerAction.SHOW_ALL_HINTS -> eval.emit("ankidroid.showAllHints()")
            ViewerAction.TOGGLE_WHITEBOARD -> toggleWhiteboard()
            ViewerAction.RECORD_VOICE -> toggleRecordVoice()
            ViewerAction.REPLAY_VOICE -> replayVoiceFlow.emit(Unit)
            ViewerAction.PAGE_UP -> eventChannel.send(ReviewerEvent.PageUp)
            ViewerAction.PAGE_DOWN -> eventChannel.send(ReviewerEvent.PageDown)
            ViewerAction.EXIT -> eventChannel.send(ReviewerEvent.Finish(AbstractFlashcardViewer.RESULT_DEFAULT))
            ViewerAction.USER_ACTION_1 -> userAction(1)
            ViewerAction.USER_ACTION_2 -> userAction(2)
            ViewerAction.USER_ACTION_3 -> userAction(3)
            ViewerAction.USER_ACTION_4 -> userAction(4)
            ViewerAction.USER_ACTION_5 -> userAction(5)
            ViewerAction.USER_ACTION_6 -> userAction(6)
            ViewerAction.USER_ACTION_7 -> userAction(7)
            ViewerAction.USER_ACTION_8 -> userAction(8)
            ViewerAction.USER_ACTION_9 -> userAction(9)
            ViewerAction.SUSPEND_MENU -> suspendCard()
            ViewerAction.BURY_MENU -> buryCard()
            ViewerAction.STATISTICS -> eventChannel.send(ReviewerEvent.Navigate(StatisticsDestination))
            ViewerAction.BROWSE -> emitBrowseDestination()
            ViewerAction.PLAY_MEDIA -> replayMedia()
            ViewerAction.FLAG_MENU -> {}
        }
    }

    override fun processAction(
        action: ViewerAction,
        binding: ReviewerBinding,
    ): Boolean {
        Timber.v("ReviewerViewModel::processAction")
        if ((binding.side != CardSide.BOTH && CardSide.fromAnswer(showingAnswer.value) != binding.side) ||
            (binding.isKey && isInputFocused)
        ) {
            return false
        }
        executeAction(action)
        return true
    }

    override suspend fun onAutoAdvanceAction(action: AutoAdvanceAction) {
        when (action) {
            QuestionAction.SHOW_ANSWER -> executeAction(ViewerAction.SHOW_ANSWER)
            QuestionAction.SHOW_REMINDER -> eventChannel.send(ReviewerEvent.Feedback(TR.studyingQuestionTimeElapsed()))
            AnswerAction.BURY_CARD -> executeAction(ViewerAction.BURY_CARD)
            AnswerAction.ANSWER_AGAIN -> executeAction(ViewerAction.ANSWER_AGAIN)
            AnswerAction.ANSWER_GOOD -> executeAction(ViewerAction.ANSWER_GOOD)
            AnswerAction.ANSWER_HARD -> executeAction(ViewerAction.ANSWER_HARD)
            AnswerAction.SHOW_REMINDER -> eventChannel.send(ReviewerEvent.Feedback(TR.studyingAnswerTimeElapsed()))
        }
    }

    // Based in https://github.com/ankitects/anki/blob/1f95d030bbc7ebcc004ffe1e2be2a320c9fe1e94/qt/aqt/reviewer.py#L201
    // and https://github.com/ankitects/anki/blob/1f95d030bbc7ebcc004ffe1e2be2a320c9fe1e94/qt/aqt/reviewer.py#L219
    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        enqueue {
            updateUndoAndRedoLabels()

            if (handler == this) return@enqueue

            when {
                changes.studyQueues -> updateCurrentCard()
                changes.noteText -> {
                    val card = currentCard() ?: return@enqueue
                    withCol { card.load(this) }
                    cardMediaPlayer.loadCardAvTags(card)
                    updateMarkIcon()
                    if (showingAnswer.value) {
                        showAnswer(card)
                    } else {
                        showQuestion(card)
                        autoAdvance.onShowQuestion()
                    }
                }
                changes.card -> {
                    val card = currentCard() ?: return@enqueue
                    withCol { card.load(this) }
                    updateFlagIcon()
                }
            }
        }
    }

    companion object {
        /** Actions that still make sense if the card changed after they were requested */
        private val cardIndependentActions =
            setOf(
                ViewerAction.ADD_NOTE,
                ViewerAction.PREVIOUS_CARD_INFO,
                ViewerAction.UNDO,
                ViewerAction.REDO,
                ViewerAction.TOGGLE_AUTO_ADVANCE,
                ViewerAction.TOGGLE_WHITEBOARD,
                ViewerAction.RECORD_VOICE,
                ViewerAction.REPLAY_VOICE,
                ViewerAction.PAGE_UP,
                ViewerAction.PAGE_DOWN,
                ViewerAction.EXIT,
                ViewerAction.STATISTICS,
            )

        private const val KEY_PREVIOUS_CARD_ID = "key_previous_card_id"
        private const val KEY_COUNTS = "counts"
    }
}
