/*
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.io.IOException

class AudioRecorder(
    context: Context,
) : Closeable {
    @Suppress("DEPRECATION")
    private val recorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

    private val cacheDir = context.cacheDir
    var currentFile: String? = null
        private set

    fun startRecording() {
        recorder.run {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            try {
                // try high quality AAC @ 44.1kHz / 192kbps first
                // can throw IllegalArgumentException if codec isn't supported
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(2)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(192000)
            } catch (exception: Exception) {
                Timber.w(exception)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            }
            currentFile = createTempRecordingFile()?.absolutePath ?: return
            setOutputFile(currentFile)
            prepare()
            start()
        }
    }

    fun stop() {
        recorder.stop()
    }

    override fun close() {
        recorder.reset()
        currentFile = null
    }

    private fun createTempRecordingFile() =
        try {
            File.createTempFile("audiorec", ".3gp", cacheDir)
        } catch (exception: IOException) {
            Timber.w(exception, "Could not create temporary recording file.")
            null
        }
}
