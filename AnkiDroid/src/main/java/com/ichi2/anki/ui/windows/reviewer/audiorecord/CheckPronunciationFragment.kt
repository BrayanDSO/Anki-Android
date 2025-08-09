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
import com.ichi2.anki.R
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.anki.utils.ext.collectIn

/**
 * Integrates [AudioRecordView] with [AudioPlayView]
 * to play the recorded audios.
 */
class CheckPronunciationFragment :
    Fragment(R.layout.check_pronunciation_fragment),
    AudioPlayView.PlayListener,
    AudioRecordView.RecordingListener {
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
                setAudioPlayer(viewModel.voicePlayer)
                setPlayListener(this@CheckPronunciationFragment)
            }
        recordView =
            view.findViewById<AudioRecordView>(R.id.audio_record_view).apply {
                setRecordingListener(this@CheckPronunciationFragment)
            }

        studyScreenViewModel.voiceRecorderEnabledFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { isEnabled ->
            if (!isEnabled) {
                cancelPlayAndRecording()
            }
        }
        studyScreenViewModel.replayVoiceFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) {
            replay()
        }
    }

    /** region [AudioPlayView.PlayListener] */
    override fun onPlayButtonPressed() = play()

    override fun onCancelButtonPressed() {
        recordView.cancelRecording()
    }
    //endregion

    /** region [AudioRecordView.RecordingListener] */
    override fun onRecordingStarted() {
        playView.cancel()
    }

    override fun onRecordingCompleted() {
        playView.isVisible = true
    }
    //endregion

    private fun replay() {
        if (recordView.isRecording) return
        if (playView.isPlaying) {
            playView.replay()
        } else {
            play()
        }
    }

    private fun cancelPlayAndRecording() {
        playView.cancel()
        recordView.cancelRecording()
    }

    private fun play() {
        val file = recordView.currentFile ?: return
        playView.play(file)
    }
}

class CheckPronunciationViewModel : ViewModel() {
    val voicePlayer = AudioPlayer()
}
