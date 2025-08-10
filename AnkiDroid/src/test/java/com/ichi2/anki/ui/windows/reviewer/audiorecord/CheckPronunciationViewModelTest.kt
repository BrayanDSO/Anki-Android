/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.audiorecord

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.R
import com.ichi2.testutils.JvmTest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CheckPronunciationViewModelTest : JvmTest() {
    private lateinit var mockRecorder: AudioRecorder
    private lateinit var mockPlayer: AudioPlayer
    private lateinit var viewModel: CheckPronunciationViewModel

    private val onPreparedCallback = slot<() -> Unit>()
    private val onCompletionCallback = slot<() -> Unit>()

    private var isPlayingMock = false

    @Before
    fun setup() {
        mockRecorder =
            mockk {
                every { currentFile } returns "test_file.3gp"
                every { stop() } just runs
            }
        mockPlayer =
            mockk(relaxUnitFun = true) {
                every { play("test_file.3gp", capture(onPreparedCallback)) } just runs
                every { isPlaying } answers { isPlayingMock }
                every { duration } returns 3000
                every { currentPosition } returns 0
                every { onCompletion = capture(onCompletionCallback) } just runs
            }

        viewModel = CheckPronunciationViewModel(mockRecorder, mockPlayer)
        viewModel.addCloseable(mockPlayer)
    }

    @Test
    fun `onRecordingCompleted should make playback visible`() {
        assertFalse(viewModel.isPlaybackVisibleFlow.value)
        viewModel.onRecordingCompleted()
        assertTrue(viewModel.isPlaybackVisibleFlow.value)
    }

    @Test
    fun `onPlayButtonPressed when stopped should start playback and update UI`() =
        runTest {
            viewModel.playIconFlow.test {
                assertEquals(R.drawable.ic_play, awaitItem())
                viewModel.onPlayButtonPressed()
                assertEquals(R.drawable.ic_replay, awaitItem())
            }

            verify { mockPlayer.play("test_file.3gp", any()) }

            viewModel.playbackProgressBarMaxFlow.test {
                assertEquals(1, awaitItem())
                onPreparedCallback.captured.invoke()
                assertEquals(3000, awaitItem())
            }
        }

    @Test
    fun `onPlayButtonPressed when playing should replay audio and reset progress`() =
        runTest {
            isPlayingMock = true

            viewModel.playbackProgressFlow.test {
                // The replay logic should immediately emit 0
                viewModel.onPlayButtonPressed()
                assertEquals(0, awaitItem())
                // Allow the while loop to terminate and clean up the test
                isPlayingMock = false
            }

            verify { mockPlayer.replay() }
        }

    @Test
    fun `when playback completes should reset icon and fill progress`() =
        runTest {
            isPlayingMock = true

            val iconJob =
                launch {
                    viewModel.playIconFlow.test {
                        // Initial state is ic_play, but it will be updated by onPlayButtonPressed.
                        // We are interested in the state change after onCompletion.
                        // The most robust way is to await an expected value after the action.
                        viewModel.onPlayButtonPressed()
                        assertEquals(R.drawable.ic_replay, awaitItem()) // Consumed the change from play being pressed
                        // The onCompletion action will happen now, we expect a change back to play
                        assertEquals(R.drawable.ic_play, awaitItem())
                    }
                }

            val progressJob =
                launch {
                    viewModel.playbackProgressFlow.test {
                        // Wait for the final progress value after completion
                        assertEquals(3000, awaitItem())
                    }
                }
            // Simulate the audio finishing. This triggers both flows above.
            onCompletionCallback.captured.invoke()
            // Clean up jobs to ensure test completion
            iconJob.cancel()
            progressJob.cancel()
        }
}
