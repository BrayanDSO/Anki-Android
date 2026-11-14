// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.core.os.BundleCompat
import androidx.lifecycle.SavedStateHandle
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NotetypeFile
import com.ichi2.anki.asyncIO
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.clozeNumbersInNote
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.utils.ext.require
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.parcelize.Parcelize
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting

class TemplatePreviewerViewModel(
    savedStateHandle: SavedStateHandle,
) : CardViewerViewModel(savedStateHandle) {
    private val arguments = savedStateHandle.require<TemplatePreviewerArguments>(TemplatePreviewerFragment.ARG_KEY)
    private val notetype: NotetypeJson = arguments.notetype
    private val isCloze: Boolean = notetype.isCloze

    /**
     * Identifies which of the card templates or cloze deletions it corresponds to
     *
     * @see CardOrdinal
     */
    @VisibleForTesting
    val ordFlow: MutableStateFlow<CardOrdinal> = savedStateHandle.getMutableStateFlow(KEY_ORD, arguments.ord)

    /**
     * @param clozeOrds the ords of the cloze cards. `null` if the note type isn't cloze
     * @param cardsWithEmptyFronts whether the front of each card is empty, in the order of [templateNames]
     */
    private class Content(
        val note: Note,
        val clozeOrds: List<CardOrdinal>?,
        val templateNames: List<String>,
        val cardsWithEmptyFronts: List<Boolean>,
    )

    private val content: Deferred<Content> = asyncIO { loadContent() }

    private suspend fun loadContent(): Content {
        val note =
            withCol {
                if (arguments.id != 0L) {
                    Note(this, arguments.id)
                } else {
                    Note.fromNotetypeId(this@withCol, arguments.notetype.id)
                }
            }.apply {
                fields = arguments.fields.toMutableList()
                tags = arguments.tags.toMutableList()
            }
        if (!isCloze) {
            val templateNames = notetype.templatesNames
            val cardsWithEmptyFronts =
                List(templateNames.size) { ord ->
                    val questionText = withCol { buildCard(note, ord).renderOutput(this) }.questionText
                    EMPTY_FRONT_LINK in questionText
                }
            return Content(note, clozeOrds = null, templateNames, cardsWithEmptyFronts)
        }
        val ordsInFields = withCol { clozeNumbersInNote(note) }.map { it - 1 }
        // cards of removed clozes are kept until 'Empty Cards' is used
        val existingCardOrds = withCol { note.cards(this) }.map { it.ord }
        val cardOrds = (ordsInFields + existingCardOrds).distinct().sorted()
        // the requested card may not exist, e.g. the template editor requests the first card
        if (cardOrds.isNotEmpty() && ordFlow.value !in cardOrds) {
            ordFlow.value = cardOrds.first()
        }
        val tr = CollectionManager.TR
        return Content(
            note = note,
            clozeOrds = cardOrds,
            templateNames = cardOrds.map { tr.cardTemplatesCard(it + 1) },
            cardsWithEmptyFronts = cardOrds.map { it !in ordsInFields },
        )
    }

    private fun Collection.buildCard(
        note: Note,
        ord: CardOrdinal,
    ): Card =
        note.ephemeralCard(
            col = this,
            ord = ord,
            customNoteType = notetype,
            fillEmpty = arguments.fillEmpty,
            deckId = arguments.deckId,
        )

    /** The card to preview. A cloze card is only selected after its ords are loaded */
    private suspend fun currentCard(): Card {
        val note = content.await().note
        return withCol { buildCard(note, ordFlow.value) }
    }

    /* *********************************************************************************************
     ************************ Public methods: meant to be used by the View **************************
     ********************************************************************************************* */

    override fun onPageFinished(isAfterRecreation: Boolean) {
        enqueue {
            val card = currentCard()
            if (isAfterRecreation && showingAnswer.value) {
                showAnswer(card)
            } else {
                showQuestion(card)
            }
            if (!isAfterRecreation) {
                loadAndPlayMedia(card, CardSide.QUESTION)
            }
        }
    }

    fun toggleShowAnswer() {
        enqueue { showSide(showAnswer = !showingAnswer.value) }
    }

    @CheckResult
    suspend fun getTemplateNames(): List<String> = content.await().templateNames

    /** Whether the front of each card is empty, in the order of [getTemplateNames] */
    @CheckResult
    suspend fun getCardsWithEmptyFronts(): List<Boolean> = content.await().cardsWithEmptyFronts

    fun onTabSelected(position: Int) {
        enqueue {
            val ord = content.await().clozeOrds?.get(position) ?: position
            if (ord == ordFlow.value) return@enqueue
            ordFlow.value = ord
            showSide(showAnswer = false)
        }
    }

    @CheckResult
    suspend fun getCurrentTabIndex(): Int {
        val clozeOrds = content.await().clozeOrds
        return clozeOrds?.indexOf(ordFlow.value) ?: ordFlow.value
    }

    suspend fun getSafeClozeOrd(): CardOrdinal {
        if (!isCloze) return 0
        content.await()
        // the previewer is recreated on the same card, e.g. after an edit
        return ordFlow.value
    }

    fun updateContent(
        fields: List<String>,
        tags: List<String>,
    ) {
        enqueue {
            // Update note fields and tags
            val note = content.await().note
            note.fields = fields.toMutableList()
            note.tags = tags.toMutableList()
            showSide(showAnswer = showingAnswer.value)
        }
    }

    /* *********************************************************************************************
     *************************************** Internal methods ***************************************
     ********************************************************************************************* */

    /** Shows a side of the current card, and plays its media */
    private suspend fun showSide(showAnswer: Boolean) {
        val card = currentCard()
        if (showAnswer) {
            showAnswer(card)
            loadAndPlayMedia(card, CardSide.ANSWER)
        } else {
            showQuestion(card)
            loadAndPlayMedia(card, CardSide.QUESTION)
        }
    }

    // https://github.com/ankitects/anki/blob/df70564079f53e587dc44f015c503fdf6a70924f/qt/aqt/clayout.py#L579
    override suspend fun typeAnsFilter(
        card: Card,
        text: String,
        side: SingleCardSide,
    ): String =
        if (side == SingleCardSide.BACK) {
            val typeAnswer = TypeAnswer.getInstance(card, text)
            if (typeAnswer?.expectedAnswer?.isEmpty() == true) {
                typeAnswer.expectedAnswer = "sample"
            }
            typeAnswer?.answerFilter(typedAnswer = "example") ?: text
        } else {
            val repl = "<center><input id='typeans' type=text value='example' readonly='readonly'></center>"
            val warning = "<center><b>${CollectionManager.TR.cardTemplatesTypeBoxesWarning()}</b></center>"
            StringBuilder(text).replaceFirst(typeAnsRe, repl).replace(typeAnsRe, warning)
        }

    companion object {
        @Language("HTML")
        private const val EMPTY_FRONT_LINK = """<a href='https://docs.ankiweb.net/templates/errors.html#front-of-card-is-blank'>"""
        private const val KEY_ORD = "ord"
    }
}

/**
 * @param id id of the note. Use 0 for non-created notes.
 * @param ord See [CardOrdinal]
 * @param fillEmpty if blank fields should be replaced with placeholder content
 */
@Parcelize
data class TemplatePreviewerArguments(
    private val notetypeFile: NotetypeFile,
    val fields: List<String>,
    val tags: List<String>,
    val id: NoteId = 0,
    val ord: CardOrdinal = 0,
    val fillEmpty: Boolean = false,
    val deckId: DeckId = DEFAULT_DECK_ID,
) : Parcelable {
    val notetype: NotetypeJson get() = notetypeFile.getNotetype()

    companion object {
        /**
         * Returns `true` if [bundle] holds a [TemplatePreviewerArguments]
         * whose backing [NotetypeFile] is still readable. Use this before
         * constructing [TemplatePreviewerViewModel] to detect when the
         * temp file was cleaned up by the OS (e.g. after process death) so
         * the previewer can abort instead of throwing from the constructor.
         */
        fun isUsable(bundle: Bundle): Boolean =
            BundleCompat
                .getParcelable(bundle, TemplatePreviewerFragment.ARG_KEY, TemplatePreviewerArguments::class.java)
                ?.notetypeFile
                ?.getNotetypeOrNull() != null
    }
}
