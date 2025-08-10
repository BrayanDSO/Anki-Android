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
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.assertFalse
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CheckPronunciationViewModelTest : JvmTest() {
    @Test
    fun `play makes the playback visible`() =
        runViewModelTest {
            assertFalse("default state should be false", isPlaybackVisibleFlow.value)
            playCurrentFile()
            assertTrue(isPlaybackVisibleFlow.value)
        }

    private fun runViewModelTest(testBody: suspend CheckPronunciationViewModel.() -> Unit) =
        runTest {
            val mockRecorder: AudioRecorder =
                mockk {
                    every { currentFile } returns ""
                }
            val mockPlayer: AudioPlayer =
                mockk {
                    every { play(any()) } answers {}
                    every { duration } returns 3000
                }
            val viewModel = CheckPronunciationViewModel(mockRecorder, mockPlayer)
            testBody(viewModel)
        }
}
