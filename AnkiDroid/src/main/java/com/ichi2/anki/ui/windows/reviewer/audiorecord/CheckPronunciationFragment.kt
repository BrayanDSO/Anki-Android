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

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.anki.utils.ext.collectIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Integrates [AudioRecordView] with [AudioPlayView]
 * to play the recorded audios.
 */
class CheckPronunciationFragment : Fragment(R.layout.check_pronunciation_fragment) {
    private lateinit var playView: AudioPlayView
    private lateinit var recordView: AudioRecordView

    private val viewModel: CheckPronunciationViewModel by viewModels()
    private val studyScreenViewModel: ReviewerViewModel by viewModels({ requireParentFragment() })

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        playView =
            view.findViewById<AudioPlayView>(R.id.audio_play_view).apply {
                setButtonPressListener(viewModel)
            }
        recordView =
            view.findViewById<AudioRecordView>(R.id.audio_record_view).apply {
                setRecordingListener(viewModel)
            }

        studyScreenViewModel.voiceRecorderEnabledFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { isEnabled ->
            if (!isEnabled) {
                viewModel.cancelPlayback()
                viewModel.cancelRecording()
            }
        }
        studyScreenViewModel.replayVoiceFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            viewModel.onReplayVoiceAction()
        }

        setupPlaybackView()
    }

    fun setupPlaybackView() {
        viewModel.isPlaybackVisibleFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { isVisible ->
            playView.isVisible = isVisible
        }
        viewModel.playbackProgressFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { progress ->
                playView.setPlaybackProgress(progress)
            }
        viewModel.playbackProgressBarMaxFlow
            .flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) { max ->
                playView.setPlaybackProgressBarMax(max)
            }
        viewModel.playIconFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { iconRes ->
            playView.changePlayIcon(iconRes)
        }
        viewModel.replayFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            playView.rotateReplayIcon()
        }
    }
}

class CheckPronunciationViewModel :
    ViewModel(),
    AudioPlayView.ButtonPressListener,
    AudioRecordView.RecordingListener {
    //region Playback
    val audioPlayer = AudioPlayer()

    val playbackProgressFlow = MutableStateFlow(0)
    val playbackProgressBarMaxFlow = MutableStateFlow(1)
    val playIconFlow = MutableStateFlow(R.drawable.ic_play)

    val replayFlow = MutableSharedFlow<Unit>()
    val isPlaybackVisibleFlow = MutableStateFlow(false)

    private var progressBarUpdateJob: Job? = null

    suspend fun playCurrentFile() {
        val file = currentFile ?: return
        audioPlayer.play(file)
        val duration = audioPlayer.duration
        playbackProgressBarMaxFlow.emit(duration)
        launchProgressBarUpdateJob()
    }

    fun replayCurrentFile() {
        progressBarUpdateJob?.cancel()
        launchProgressBarUpdateJob()
        audioPlayer.replay()
    }

    suspend fun cancelPlayback() {
        progressBarUpdateJob?.cancel()
        playbackProgressFlow.emit(0)
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
                    playbackProgressFlow.emit(playbackProgressBarMaxFlow.value)
                }
            }
    }

    suspend fun onReplayVoiceAction() {
        if (isRecording) return
        val isPlaying = true
        if (isPlaying) {
            replayCurrentFile()
            replayFlow.emit(Unit)
        } else {
            playCurrentFile()
        }
    }
    //endregion

    //region AudioPlayView.ButtonPressListener
    override fun onPlayButtonPressed() {
        viewModelScope.launch { playCurrentFile() }
    }

    override fun onCancelButtonPressed() {
        viewModelScope.launch {
            cancelPlayback()
        }
    }
    //endregion

    //region Recording
    private val audioRecorder = AudioRecorder(AnkiDroidApp.instance)
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
