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
import android.graphics.drawable.Drawable
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ichi2.anki.R

class AudioPlayView : ConstraintLayout {
    private val progressBar: LinearProgressIndicator
    private val playIconView: ImageView
    private val playDrawable: Drawable
    private val replayDrawable: Drawable
    private var isPlaying: Boolean = false
        set(value) {
            val drawable = if (value) replayDrawable else playDrawable
            playIconView.setImageDrawable(drawable)
            field = value
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
        LayoutInflater.from(context).inflate(R.layout.audio_play_view, this, true)
        progressBar = findViewById(R.id.progress_indicator)
        playIconView = findViewById(R.id.play_icon)
        playDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_play)!!
        replayDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_replay)!!
        findViewById<View>(R.id.play_button).setOnClickListener {
            if (isPlaying) {
                replay()
            } else {
                playListener?.onAudioPlay()
            }
        }
        findViewById<View>(R.id.cancel_button).setOnClickListener {
            cancel()
        }
    }

    interface PlayListener {
        fun onAudioPlay()

        fun onAudioReplay()

        fun onAudioPlayCancel()
    }

    private var timer: CountDownTimer? = null
    private var playListener: PlayListener? = null

    fun setPlayListener(playListener: PlayListener) {
        this.playListener = playListener
    }

    fun playNew(duration: Int) {
        progressBar.progress = 0
        progressBar.max = duration
        timer =
            object : CountDownTimer(duration.toLong(), PROGRESS_UPDATE_RATE_MS) {
                override fun onTick(millisUntilFinished: Long) {
                    val progress = duration - millisUntilFinished
                    progressBar.setProgress(progress.toInt(), true)
                }

                override fun onFinish() {
                    progressBar.setProgress(progressBar.max, true)
                }
            }.start()

        isPlaying = true
    }

    fun replay() {
        progressBar.progress = 0
        timer?.run {
            cancel()
            start()
        }
        playListener?.onAudioReplay()
    }

    fun cancel() {
        visibility = GONE
        progressBar.progress = 0
        isPlaying = false
        timer?.cancel()
        playListener?.onAudioPlayCancel()
    }

    companion object {
        private const val PROGRESS_UPDATE_RATE_MS = 50L
    }
}
