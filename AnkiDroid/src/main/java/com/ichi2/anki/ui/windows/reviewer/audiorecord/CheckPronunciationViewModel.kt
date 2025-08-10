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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CheckPronunciationViewModel(
    private val audioRecorder: AudioRecorder = AudioRecorder(AnkiDroidApp.instance),
    private val audioPlayer: AudioPlayer = AudioPlayer(),
) : ViewModel(),
    AudioPlayView.ButtonPressListener,
    AudioRecordView.RecordingListener {
    init {
        addCloseable(audioPlayer)
        addCloseable(audioRecorder)
    }

    //region Playback
    val playbackProgressFlow = MutableStateFlow(0)
    val playbackProgressBarMaxFlow = MutableStateFlow(1)
    val playIconFlow = MutableStateFlow(R.drawable.ic_play)

    val replayFlow = MutableSharedFlow<Unit>()
    val isPlaybackVisibleFlow = MutableStateFlow(false)

    private var progressBarUpdateJob: Job? = null
    private val isPlaying get() = audioPlayer.isPlaying

    private fun playCurrentFile() {
        val file = currentFile ?: return
        // Use the callback to sync UI updates with the player's state
        audioPlayer.play(file) {
            viewModelScope.launch {
                playbackProgressBarMaxFlow.emit(audioPlayer.duration)
                launchProgressBarUpdateJob()
            }
        }
    }

    private fun replayCurrentFile() {
        audioPlayer.replay()
        // Relaunch the job to update progress from the beginning
        launchProgressBarUpdateJob()
    }

    fun cancelPlayback() {
        progressBarUpdateJob?.cancel()
        audioPlayer.close()
        viewModelScope.launch {
            playbackProgressFlow.emit(0)
        }
    }

    private fun launchProgressBarUpdateJob() {
        progressBarUpdateJob?.cancel() // Cancel any existing job
        progressBarUpdateJob =
            viewModelScope.launch {
                // Poll the actual player position while it's playing
                while (isPlaying) {
                    playbackProgressFlow.emit(audioPlayer.currentPosition)
                    delay(50L) // Update ~20 times per second
                }
                // When playback is finished, ensure the progress bar is full
                if (!isPlaying && audioPlayer.duration > 0) {
                    playbackProgressFlow.emit(audioPlayer.duration)
                }
            }
    }

    fun onReplayVoiceAction() {
        if (!isPlaybackVisibleFlow.value) return
        onPlayButtonPressed()
    }
    //endregion

    //region AudioPlayView.ButtonPressListener
    override fun onPlayButtonPressed() {
        viewModelScope.launch {
            playIconFlow.emit(R.drawable.ic_replay)
        }
        if (isPlaying) {
            replayCurrentFile()
            viewModelScope.launch {
                replayFlow.emit(Unit)
            }
        } else {
            // This now correctly handles both the first play and replay after completion
            playCurrentFile()
        }
    }

    override fun onCancelButtonPressed() {
        viewModelScope.launch {
            isPlaybackVisibleFlow.emit(false)
            cancelPlayback()
        }
    }
    //endregion

    //region Recording
    private val currentFile get() = audioRecorder.currentFile

    fun cancelRecording() {
        audioRecorder.close()
    }

    //endregion

    //region AudioRecordView.RecordingListener
    override fun onRecordingStarted() {
        audioRecorder.startRecording()
        viewModelScope.launch {
            isPlaybackVisibleFlow.emit(false)
            cancelPlayback()
        }
    }

    override fun onRecordingCanceled() {
        audioRecorder.close()
    }

    override fun onRecordingCompleted() {
        audioRecorder.stop()
        viewModelScope.launch {
            isPlaybackVisibleFlow.emit(true)
            // Reset UI for the new recording
            playIconFlow.emit(R.drawable.ic_play)
            playbackProgressFlow.emit(0)
        }
    }
    //endregion
}
