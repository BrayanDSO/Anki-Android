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
import com.ichi2.anki.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CheckPronunciationViewModel(
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer = AudioPlayer(),
) : ViewModel(),
    AudioPlayView.ButtonPressListener,
    AudioRecordView.RecordingListener {
    //region Playback
    val playbackProgressFlow = MutableStateFlow(0)
    val playbackProgressBarMaxFlow = MutableStateFlow(1)
    val playIconFlow = MutableStateFlow(R.drawable.ic_play)

    val replayFlow = MutableSharedFlow<Unit>()
    val isPlaybackVisibleFlow = MutableStateFlow(false)

    private var progressBarUpdateJob: Job? = null
    private var isPlaying = false

    fun playCurrentFile() {
        val file = currentFile ?: return
        audioPlayer.play(file)

        launchProgressBarUpdateJob()
        viewModelScope.launch {
            playbackProgressBarMaxFlow.emit(audioPlayer.duration)
            isPlaybackVisibleFlow.emit(true)
        }
    }

    fun replayCurrentFile() {
        progressBarUpdateJob?.cancel()
        launchProgressBarUpdateJob()
        audioPlayer.replay()
    }

    fun cancelPlayback() {
        progressBarUpdateJob?.cancel()
        audioPlayer.cancel()
        viewModelScope.launch {
            playbackProgressFlow.emit(0)
        }
    }

    private fun launchProgressBarUpdateJob() {
        progressBarUpdateJob =
            viewModelScope.launch {
                try {
                    for (elapsedTime in 0..playbackProgressBarMaxFlow.value step 50) {
                        playbackProgressFlow.emit(elapsedTime)
                        delay(50L)
                    }
                } finally {
                    isPlaying = false
                    playbackProgressFlow.emit(playbackProgressBarMaxFlow.value)
                }
            }
    }

    fun onReplayVoiceAction() {
        if (isRecording) return
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
    private val isRecording get() = audioRecorder.isRecording

    fun cancelRecording() {
        audioRecorder.cancel()
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
        audioRecorder.cancel()
    }

    override fun onRecordingCompleted() {
        audioRecorder.stop()
        viewModelScope.launch {
            isPlaybackVisibleFlow.emit(true)
        }
    }
    //endregion
}
