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

import android.media.MediaPlayer
import java.io.Closeable
import java.io.IOException

class AudioPlayer : Closeable {
    private val mediaPlayer = MediaPlayer()

    var isPlaying = false
        private set
    private var isPrepared = false

    /** A callback that fires when playback is complete. */
    var onCompletion: (() -> Unit)? = null

    val duration: Int
        get() = if (isPrepared) mediaPlayer.duration else 0
    val currentPosition: Int
        get() = if (isPrepared) mediaPlayer.currentPosition else 0

    init {
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
            // Invoke the callback when playback completes
            onCompletion?.invoke()
        }
    }

    fun play(
        filePath: String,
        onPrepared: () -> Unit,
    ) {
        try {
            mediaPlayer.reset()
            isPrepared = false
            isPlaying = false

            mediaPlayer.setDataSource(filePath)
            mediaPlayer.setOnPreparedListener { mp ->
                isPrepared = true
                mp.start()
                isPlaying = true
                onPrepared()
            }
            mediaPlayer.prepareAsync()
        } catch (e: IOException) {
            close()
        }
    }

    fun replay() {
        if (isPrepared) {
            mediaPlayer.seekTo(0)
            mediaPlayer.start()
            isPlaying = true
        }
    }

    override fun close() {
        mediaPlayer.reset()
        isPrepared = false
        isPlaying = false
    }
}
