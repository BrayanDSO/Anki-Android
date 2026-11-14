// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.SoundTagPlayer
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.ext.cardStateCustomizer
import com.ichi2.anki.libanki.DeckConfig.Companion.ANSWER_ACTION
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.AnswerAction
import io.mockk.coEvery
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class ReviewerViewModelTest : RobolectricTest() {
    private val viewModelStore = ViewModelStore()

    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Before
    fun disableAutoplay() {
        // stored in collection; no need for a reset
        updateDeckConfig(col.decks.selected()) { autoplay = false }
    }

    @After
    fun clearViewModels() {
        viewModelStore.clear()
    }

    @After
    fun unmockAudio() {
        unmockkAll()
    }

    @Test
    fun `flipping during the question audio does not start the answer timer`() =
        runTest {
            val card = addBasicNote("[sound:question.mp3]", "[sound:answer.mp3]").firstCard()
            updateDeckConfig(col.decks.selected()) {
                autoplay = true
                secondsToShowAnswer = 5.0
                jsonObject.put(ANSWER_ACTION, AnswerAction.ANSWER_GOOD.code)
            }
            assertTrue(col.decks.configDictForDeckId(col.decks.selected()).waitForAudio, "'Wait for audio' is on by default")
            val startedAudios = mockAudioThatPlaysUntilStopped()
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
            viewModel.onPageFinished(false)
            startedAudios.awaitStart() // question audio
            viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)

            viewModel.onShowAnswer() // stops the question audio
            viewModel.awaitPendingActions()
            startedAudios.awaitStart() // answer audio, which never ends
            advanceTimeBy(6.seconds)

            assertEquals(0, col.getCard(card.id).reps, "card answered while its answer audio was playing")
        }

    @Test
    fun `answering during the answer audio does not start the next question timer`() =
        runTest {
            addBasicNote("[sound:question1.mp3]", "[sound:answer1.mp3]")
            addBasicNote("[sound:question2.mp3]", "[sound:answer2.mp3]")
            updateDeckConfig(col.decks.selected()) {
                autoplay = true
                secondsToShowQuestion = 5.0 // the default action is 'Show answer'
            }
            assertTrue(col.decks.configDictForDeckId(col.decks.selected()).waitForAudio, "'Wait for audio' is on by default")
            val startedAudios = mockAudioThatPlaysUntilStopped()
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
            viewModel.onPageFinished(false)
            startedAudios.awaitStart() // question audio
            viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
            viewModel.onShowAnswer()
            startedAudios.awaitStart() // answer audio

            viewModel.answerCard(Rating.GOOD) // stops the answer audio
            viewModel.awaitPendingActions()
            startedAudios.awaitStart() // next question audio, which never ends
            advanceTimeBy(6.seconds)

            assertFalse(viewModel.showingAnswer.value, "answer shown while the question audio was playing")
        }

    /** Mocks `[sound:]` playback so it only ends when stopped. Releases a permit per started playback */
    private fun mockAudioThatPlaysUntilStopped(): Semaphore {
        val startedAudios = Semaphore(0)
        mockkConstructor(SoundTagPlayer::class)
        coEvery { anyConstructed<SoundTagPlayer>().play(any(), any()) } coAnswers {
            startedAudios.release()
            awaitCancellation()
        }
        return startedAudios
    }

    /** Playback runs on the real IO dispatcher, so it is awaited in wall-clock time */
    private fun Semaphore.awaitStart() = assertTrue(tryAcquire(5, TimeUnit.SECONDS), "audio did not start")

    /** Waits for the previously requested actions to complete: they are executed in order */
    private suspend fun ReviewerViewModel.awaitPendingActions() =
        events.filter { it is ReviewerEvent.PageUp }.test {
            executeAction(ViewerAction.PAGE_UP)
            awaitItem()
        }

    @Test
    fun `burying the card refreshes it once`() =
        runTest {
            addBasicNote("one", "1")
            addBasicNote("two", "2")
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
            viewModel.onPageFinished(false)
            advanceUntilIdle()

            viewModel.onCardUpdatedFlow.test {
                viewModel.executeAction(ViewerAction.BURY_CARD)
                advanceUntilIdle()
                awaitItem()
                expectNoEvents()
            }
        }

    @Test
    fun `answering twice in a row only answers the card being shown`() =
        runTest {
            val cards = listOf(addBasicNote("one", "1").firstCard(), addBasicNote("two", "2").firstCard())
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
            viewModel.onPageFinished(false)
            advanceUntilIdle()
            viewModel.onShowAnswer()
            advanceUntilIdle()

            viewModel.answerCard(Rating.GOOD)
            viewModel.answerCard(Rating.GOOD)
            advanceUntilIdle()

            assertEquals(1, cards.sumOf { col.getCard(it.id).reps }, "a card was answered without being shown")
        }

    @Test
    fun `answering after the timebox is reached does not answer the previous card again`() =
        runTest {
            addBasicNote("one", "1")
            addBasicNote("two", "2")
            col.config.set("timeLim", 60)
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
            viewModel.onPageFinished(false)
            advanceUntilIdle()
            val answeredCardId = assertNotNull(viewModel.getCardId())
            viewModel.onShowAnswer()
            advanceUntilIdle()
            assertIs<MockTime>(TimeManager.time).addM(2)
            viewModel.events.filter { it is ReviewerEvent.TimeboxReached }.test {
                viewModel.answerCard(Rating.GOOD)
                advanceUntilIdle()
                awaitItem()
            }

            // the timebox dialog isn't shown if the screen is in the background
            viewModel.answerCard(Rating.GOOD)
            advanceUntilIdle()

            assertEquals(1, col.getCard(answeredCardId).reps, "the previous card was answered again")
        }

    @Test
    fun `auto advance waiting for audio shows the answer if audio is not played automatically`() =
        runTest {
            addBasicNote("one", "1")
            addBasicNote("two", "2")
            updateDeckConfig(col.decks.selected()) { secondsToShowQuestion = 5.0 } // autoplay is off
            assertTrue(col.decks.configDictForDeckId(col.decks.selected()).waitForAudio, "'Wait for audio' is on by default")
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
            viewModel.onPageFinished(false)
            advanceUntilIdle()
            viewModel.executeAction(ViewerAction.TOGGLE_AUTO_ADVANCE)
            advanceUntilIdle()
            assertTrue(viewModel.showingAnswer.value, "the answer of the first card was not shown")

            viewModel.answerCard(Rating.GOOD)
            advanceUntilIdle()
            advanceTimeBy(6.seconds)

            assertTrue(viewModel.showingAnswer.value, "the answer of the next card was not shown")
        }

    @Test
    fun `typed answer is compared when the answer is shown`() =
        runTest {
            addBasicWithTypingNote("front", "a \"b\" <c")
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
            viewModel.onPageFinished(false)
            advanceUntilIdle()
            viewModel.onTypedAnswerChanged("a \"b\" <c")

            viewModel.eval
                .filter { it.startsWith("_showAnswer(") }
                .test {
                    viewModel.onShowAnswer()
                    advanceUntilIdle()
                    val answer = awaitItem()
                    assertTrue("typeGood" in answer, "the typed answer was not compared")
                    assertFalse("typeBad" in answer, "the typed answer was not the one compared")
                }
        }

    @Test
    fun `HTML answer marks nosuggest fields and requests a Done key`() =
        runTest {
            val field = htmlAnswerField("nosuggest:type")

            assertEquals("true", field.attr("data-ankidroid-nosuggest"))
            assertEquals("done", field.attr("enterkeyhint"))
        }

    @Test
    fun `ordinary HTML answers keep their keyboard defaults`() =
        runTest {
            val field = htmlAnswerField("type")

            assertFalse(field.hasAttr("data-ankidroid-nosuggest"))
            assertFalse(field.hasAttr("enterkeyhint"))
        }

    private suspend fun TestScope.htmlAnswerField(filter: String): Element {
        targetContext.sharedPrefs().edit { putBoolean("useInputTag", true) }
        val card = addBasicWithTypingNote("front", "back").firstCard()
        val noteType = card.noteType(col)
        noteType.templates[0].qfmt = "{{Front}}{{$filter:Back}}"
        col.notetypes.save(noteType)
        val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }
        lateinit var html: String
        viewModel.eval
            .filter { it.startsWith("_showQuestion(") }
            .test {
                viewModel.onPageFinished(false)
                advanceUntilIdle()
                // The first argument is a JSON string containing the question HTML.
                html = assertIs<String>(JSONTokener(awaitItem().removePrefix("_showQuestion(")).nextValue())
                cancelAndIgnoreRemainingEvents()
            }
        return assertNotNull(Jsoup.parseBodyFragment(html).selectFirst("input#typeans"))
    }

    @Test
    fun `answering waits for the custom scheduler of the first card`() =
        runTest {
            val card = addBasicNote().firstCard()
            col.config.cardStateCustomizer = "await new Promise(resolve => setTimeout(resolve, 5000));"
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }

            viewModel.onShowAnswer()
            viewModel.answerCard(Rating.GOOD)
            viewModel.onPageFinished(false)
            advanceUntilIdle()
            assertFalse(viewModel.showingAnswer.value)
            assertEquals(0, col.getCard(card.id).reps)

            viewModel.onStateMutationCallback()
            advanceUntilIdle()
            assertEquals(1, col.getCard(card.id).reps)
        }

    @Test
    fun `restored answer can be graded after process death`() =
        runTest {
            val card = addBasicNote().firstCard()
            val savedStateHandle = SavedStateHandle(mapOf("showingAnswer" to true))
            val viewModel = ReviewerViewModel(savedStateHandle).also { viewModelStore.put("reviewer", it) }

            viewModel.onPageFinished(true)
            advanceUntilIdle()
            assertTrue(viewModel.showingAnswer.value)

            viewModel.answerCard(Rating.GOOD)
            advanceUntilIdle()
            assertEquals(1, col.getCard(card.id).reps)
        }

    @Test
    fun `restored answer waits for the custom scheduler after process death`() =
        runTest {
            val card = addBasicNote().firstCard()
            col.config.cardStateCustomizer = "await new Promise(resolve => setTimeout(resolve, 5000));"
            val savedStateHandle = SavedStateHandle(mapOf("showingAnswer" to true))
            val viewModel = ReviewerViewModel(savedStateHandle).also { viewModelStore.put("reviewer", it) }

            viewModel.statesMutationEvalFlow.test {
                viewModel.onPageFinished(true)
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)
                assertTrue(awaitItem().contains(col.config.cardStateCustomizer))

                viewModel.answerCard(Rating.GOOD)
                advanceUntilIdle()
                assertEquals(0, col.getCard(card.id).reps)
                cancelAndIgnoreRemainingEvents()

                viewModel.onStateMutationCallback()
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).reps)
            }
        }

    @Test
    fun `restoring answer with retained view model does not rerun custom scheduler`() =
        runTest {
            val card = addBasicNote().firstCard()
            col.config.cardStateCustomizer = "await new Promise(resolve => setTimeout(resolve, 5000));"
            val viewModel = ReviewerViewModel(SavedStateHandle()).also { viewModelStore.put("reviewer", it) }

            viewModel.statesMutationEvalFlow.test {
                viewModel.onPageFinished(false)
                advanceUntilIdle()
                awaitItem()
                viewModel.onStateMutationCallback()
                viewModel.onShowAnswer()
                advanceUntilIdle()
                assertTrue(viewModel.showingAnswer.value)

                viewModel.onPageFinished(true)
                advanceUntilIdle()
                expectNoEvents()
                assertTrue(viewModel.showingAnswer.value)
                cancelAndIgnoreRemainingEvents()

                viewModel.answerCard(Rating.GOOD)
                advanceUntilIdle()
                assertEquals(1, col.getCard(card.id).reps)
            }
        }
}
