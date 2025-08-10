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
) : ViewModel() {
    init {
        addCloseable(audioPlayer)
        addCloseable(audioRecorder)

        audioPlayer.onCompletion = {
            viewModelScope.launch {
                playbackProgressFlow.emit(playbackProgressBarMaxFlow.value)
                playIconFlow.emit(R.drawable.ic_play)
            }
        }
    }

    val playbackProgressFlow = MutableStateFlow(0)
    val playbackProgressBarMaxFlow = MutableStateFlow(1)
    val playIconFlow = MutableStateFlow(R.drawable.ic_play)
    val replayFlow = MutableSharedFlow<Unit>()
    val isPlaybackVisibleFlow = MutableStateFlow(false)

    private var progressBarUpdateJob: Job? = null
    private val currentFile get() = audioRecorder.currentFile
    private val isPlaying get() = audioPlayer.isPlaying

    fun handleEvent(event: CheckPronunciationUiEvent) {
        when (event) {
            is CheckPronunciationUiEvent.RecordingStarted -> onRecordingStarted()
            is CheckPronunciationUiEvent.RecordingCompleted -> onRecordingCompleted()
            is CheckPronunciationUiEvent.RecordingCancelled -> onRecordingCancelled()
            is CheckPronunciationUiEvent.PlayOrReplay -> onPlayOrReplay()
            is CheckPronunciationUiEvent.CancelPlayback -> onCancelPlayback()
            is CheckPronunciationUiEvent.ReplayFromAction -> onReplayFromAction()
        }
    }

    fun cancelAll() {
        onRecordingCancelled()
        onCancelPlayback()
    }

    private fun playCurrentFile() {
        val file = currentFile ?: return
        audioPlayer.play(file) {
            viewModelScope.launch {
                playbackProgressBarMaxFlow.emit(audioPlayer.duration)
                launchProgressBarUpdateJob()
            }
        }
    }

    private fun replayCurrentFile() {
        audioPlayer.replay()
        launchProgressBarUpdateJob()
    }

    private fun onCancelPlayback() {
        progressBarUpdateJob?.cancel()
        audioPlayer.close()
        viewModelScope.launch {
            isPlaybackVisibleFlow.emit(false)
            playbackProgressFlow.emit(0)
        }
    }

    private fun launchProgressBarUpdateJob() {
        progressBarUpdateJob?.cancel()
        progressBarUpdateJob =
            viewModelScope.launch {
                while (isPlaying) {
                    playbackProgressFlow.emit(audioPlayer.currentPosition)
                    delay(50L)
                }
            }
    }

    private fun onReplayFromAction() {
        if (!isPlaybackVisibleFlow.value) return
        onPlayOrReplay()
    }

    private fun onPlayOrReplay() {
        if (!isPlaying) {
            viewModelScope.launch { playIconFlow.emit(R.drawable.ic_replay) }
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

    private fun onRecordingStarted() {
        audioRecorder.startRecording()
        viewModelScope.launch {
            onCancelPlayback()
        }
    }

    private fun onRecordingCancelled() {
        audioRecorder.close()
    }

    private fun onRecordingCompleted() {
        audioRecorder.stop()
        viewModelScope.launch {
            isPlaybackVisibleFlow.emit(true)
            playIconFlow.emit(R.drawable.ic_play)
            playbackProgressFlow.emit(0)
        }
    }
}

/**
 * Represents all the user actions or events that can occur in the UI.
 */
sealed interface CheckPronunciationUiEvent {
    data object PlayOrReplay : CheckPronunciationUiEvent

    data object CancelPlayback : CheckPronunciationUiEvent

    data object RecordingStarted : CheckPronunciationUiEvent

    data object RecordingCompleted : CheckPronunciationUiEvent

    data object RecordingCancelled : CheckPronunciationUiEvent

    data object ReplayFromAction : CheckPronunciationUiEvent
}
