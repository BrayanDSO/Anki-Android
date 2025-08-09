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

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.ichi2.anki.R

/**
 * Integrates [AudioRecordView] with [AudioPlayView]
 * to play the recorded audios.
 */
class CheckPronunciationView :
    ConstraintLayout,
    AudioPlayView.PlayListener,
    AudioRecordView.RecordingListener {
    private val playView: AudioPlayView
    private val recordView: AudioRecordView

    fun setAudioPlayer(audioPlayer: AudioPlayer) {
        playView.setAudioPlayer(audioPlayer)
    }

    constructor(context: Context) : this(context, null, 0, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        LayoutInflater.from(context).inflate(R.layout.check_pronunciation_view, this, true)
        playView =
            findViewById<AudioPlayView>(R.id.audio_play_view).apply {
                setPlayListener(this@CheckPronunciationView)
            }
        recordView =
            findViewById<AudioRecordView>(R.id.audio_record_view).apply {
                setRecordingListener(this@CheckPronunciationView)
            }
    }

    /** region [AudioPlayView.PlayListener] */
    override fun onAudioPlay() = play()

    override fun onAudioPlayCancel() {
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

    fun replay() {
        if (recordView.isRecording) return
        if (playView.isPlaying) {
            playView.replay()
        } else {
            play()
        }
    }

    fun cancelPlayAndRecording() {
        playView.cancel() // FIXME isso indiretamente cancela o recording
    }

    private fun play() {
        val file = recordView.currentFile ?: return
        playView.play(file)
    }
}
