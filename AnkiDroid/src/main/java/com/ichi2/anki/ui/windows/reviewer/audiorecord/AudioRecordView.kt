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
 *
 * This file incorporates code under the following license:
 *
 *     Copyright 2018 Varun John
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 * https://github.com/varunjohn/Audio-Recording-Animation/blob/d5fef5bbf051e81f4ff35bb58496b7848bde6dd4/WhatsappMessengerView/src/main/java/com/varunjohn1990/audio_record_view/AudioRecordView.java
 *
 * CHANGES:
 * * Removed layoutEffect1 and layoutEffect2
 * * Removed imageViewSend
 * * Removed "Attachments" and "Message" views
 * * Added clipChildren to some elements
 * * Simplified the layouts hierarchy
 * * Changed the icons, strings and style
 */
package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.util.TypedValueCompat
import com.ichi2.anki.R
import kotlin.math.abs

class AudioRecordView : FrameLayout {
    //region Views
    private val recordButton: View
    private val lockArrow: View
    private val imageViewLock: View
    private val imageViewMic: View
    private val dustin: View
    private val dustinCover: View
    private val imageViewStop: View
    private val layoutSlideCancel: View
    private val layoutLock: View
    private val chronometer: Chronometer
    private val textViewSlide: TextView
    //endregion

    //region Animations
    private val animBlink = AnimationUtils.loadAnimation(context, R.anim.blink)
    private val animJump = AnimationUtils.loadAnimation(context, R.anim.jump)
    private val animJumpFast = AnimationUtils.loadAnimation(context, R.anim.jump_fast)
    //endregion

    //region State & Logic
    private var isDeleting = false
    private var stopTrackingAction = false

    private var firstX = 0f
    private var firstY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val cancelOffset: Float
    private val lockOffset: Float
    private val dp = TypedValueCompat.dpToPx(1F, resources.displayMetrics)
    private var isLocked = false

    private var userBehavior = UserBehavior.NONE

    private var recordingListener: RecordingListener? = null

    fun setRecordingListener(recordingListener: RecordingListener) {
        this.recordingListener = recordingListener
    }
    //endregion

    constructor(context: Context) : this(context, null, 0, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        LayoutInflater.from(context).inflate(R.layout.audio_record_view, this, true)

        recordButton = findViewById(R.id.recordButton)
        imageViewStop = findViewById(R.id.imageViewStop)
        imageViewLock = findViewById(R.id.imageViewLock)
        lockArrow = findViewById(R.id.imageViewLockArrow)
        textViewSlide = findViewById(R.id.textViewSlide)
        chronometer = findViewById(R.id.chronometer)
        layoutSlideCancel = findViewById(R.id.layoutSlideCancel)
        layoutLock = findViewById(R.id.layoutLock)
        imageViewMic = findViewById(R.id.imageViewMic)
        dustin = findViewById(R.id.dustin)
        dustinCover = findViewById(R.id.dustin_cover)

        cancelOffset = (resources.displayMetrics.widthPixels / 4F)
        lockOffset = (resources.displayMetrics.heightPixels / 4F)

        setupTouchListener()
        imageViewStop.setOnClickListener {
            isLocked = false
            stopRecording(RecordingBehaviour.LOCK_DONE)
        }
    }

    enum class UserBehavior {
        CANCELING,
        LOCKING,
        NONE,
    }

    enum class RecordingBehaviour {
        CANCELED,
        LOCKED,
        LOCK_DONE,
        RELEASED,
    }

    interface RecordingListener {
        fun onRecordingStarted()

        fun onRecordingCompleted()

        fun onRecordingCanceled()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        recordButton.setOnTouchListener { _, motionEvent ->
            if (!requireMicrophonePermission(context)) return@setOnTouchListener true // TODO decidir o fluxo de pedir a permissão
            if (isDeleting) return@setOnTouchListener true

            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    firstX = motionEvent.rawX
                    firstY = motionEvent.rawY
                    startRecord()
                }
                MotionEvent.ACTION_UP -> {
                    stopRecording(RecordingBehaviour.RELEASED)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (stopTrackingAction) return@setOnTouchListener true

                    val behavior = getBehaviorFromDirection(motionEvent.rawX, motionEvent.rawY)
                    if (behavior != userBehavior) {
                        // Set behavior only on the first detection of a swipe direction
                        userBehavior = behavior
                    }

                    when (userBehavior) {
                        UserBehavior.CANCELING -> translateX(motionEvent.rawX - firstX)
                        UserBehavior.LOCKING -> translateY(motionEvent.rawY - firstY)
                        else -> {}
                    }

                    lastX = motionEvent.rawX
                    lastY = motionEvent.rawY
                }
            }
            true
        }
    }

    private fun getBehaviorFromDirection(
        currentX: Float,
        currentY: Float,
    ): UserBehavior {
        val motionX = abs(firstX - currentX)
        val motionY = abs(firstY - currentY)

        return when {
            motionY > motionX && currentY < firstY -> UserBehavior.LOCKING
            motionX > motionY && currentX < firstX -> UserBehavior.CANCELING
            else -> UserBehavior.NONE
        }
    }

    private fun translateY(y: Float) {
        if (y < -lockOffset) {
            lock()
            recordButton.translationY = 0f
            return
        }

        if (layoutLock.visibility != VISIBLE) {
            layoutLock.visibility = VISIBLE
        }

        recordButton.translationY = y
        layoutLock.translationY = y / 2
        recordButton.translationX = 0f
    }

    private fun translateX(x: Float) {
        if (x < -cancelOffset) {
            cancel()
            recordButton.translationX = 0f
            layoutSlideCancel.translationX = 0f
            return
        }

        recordButton.translationX = x
        layoutSlideCancel.translationX = x
        layoutLock.translationY = 0f
        recordButton.translationY = 0f

        if (abs(x) < imageViewMic.width / 2) {
            if (layoutLock.visibility != VISIBLE) {
                layoutLock.visibility = VISIBLE
            }
        } else {
            if (layoutLock.visibility != GONE) {
                layoutLock.visibility = GONE
            }
        }
    }

    private fun lock() {
        stopTrackingAction = true
        stopRecording(RecordingBehaviour.LOCKED)
        isLocked = true
    }

    private fun cancel() {
        stopTrackingAction = true
        stopRecording(RecordingBehaviour.CANCELED)
    }

    private fun stopRecording(recordingBehaviour: RecordingBehaviour) {
        stopTrackingAction = true
        firstX = 0f
        firstY = 0f
        lastX = 0f
        lastY = 0f
        userBehavior = UserBehavior.NONE

        recordButton
            .animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(100)
            .setInterpolator(LinearInterpolator())
            .start()
        layoutSlideCancel.translationX = 0f
        layoutSlideCancel.visibility = GONE

        layoutLock.visibility = GONE
        layoutLock.translationY = 0f
        lockArrow.clearAnimation()
        imageViewLock.clearAnimation()

        if (isLocked) return

        when (recordingBehaviour) {
            RecordingBehaviour.LOCKED -> {
                imageViewStop.visibility = VISIBLE
                recordButton.visibility = GONE
            }
            RecordingBehaviour.CANCELED -> {
                chronometer.clearAnimation()
                chronometer.stop()
                chronometer.visibility = INVISIBLE
                imageViewMic.visibility = INVISIBLE
                imageViewStop.visibility = GONE
                recordButton.visibility = VISIBLE
                delete()
                recordingListener?.onRecordingCanceled()
            }
            RecordingBehaviour.RELEASED, RecordingBehaviour.LOCK_DONE -> {
                chronometer.clearAnimation()
                chronometer.stop()
                chronometer.visibility = INVISIBLE
                imageViewMic.visibility = INVISIBLE
                imageViewStop.visibility = GONE
                recordButton.visibility = VISIBLE
                recordingListener?.onRecordingCompleted()
            }
        }
    }

    private fun startRecord() {
        recordingListener?.onRecordingStarted()

        stopTrackingAction = false
        recordButton
            .animate()
            .scaleXBy(1f)
            .scaleYBy(1f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator())
            .start()
        chronometer.visibility = VISIBLE
        layoutLock.visibility = VISIBLE
        layoutSlideCancel.visibility = VISIBLE
        imageViewMic.visibility = VISIBLE

        chronometer.startAnimation(animBlink)
        lockArrow.clearAnimation()
        imageViewLock.clearAnimation()
        lockArrow.startAnimation(animJumpFast)
        imageViewLock.startAnimation(animJump)

        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.start()
    }

    private fun delete() {
        imageViewMic.visibility = VISIBLE
        imageViewMic.rotation = 0f
        isDeleting = true
        recordButton.isEnabled = false

        postDelayed({
            isDeleting = false
            recordButton.isEnabled = true
        }, DURATION_DELETE_TOTAL)

        val trashCanDisplacement = -dp * 40

        // 1. Animate Mic flying up to the trash can
        imageViewMic
            .animate()
            .translationY(-dp * 150)
            .rotation(360f)
            .scaleX(0.6f)
            .scaleY(0.6f)
            .setDuration(DURATION_DELETE_MIC_FLY_UP)
            .setInterpolator(DecelerateInterpolator())
            .withStartAction {
                // 2. Animate trash can sliding in at the same time
                dustin.translationX = trashCanDisplacement
                dustinCover.translationX = trashCanDisplacement

                dustinCover
                    .animate()
                    .translationX(0f)
                    .rotation(-120f)
                    .setDuration(DURATION_DELETE_TRASH_SLIDE_IN)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                dustin
                    .animate()
                    .translationX(0f)
                    .setDuration(DURATION_DELETE_TRASH_SLIDE_IN)
                    .setInterpolator(DecelerateInterpolator())
                    .withStartAction {
                        dustin.visibility = VISIBLE
                        dustinCover.visibility = VISIBLE
                    }.start()
            }.withEndAction {
                // 3. Animate mic dropping into the can
                imageViewMic
                    .animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(DURATION_DELETE_MIC_DROP)
                    .setInterpolator(LinearInterpolator())
                    .withEndAction {
                        imageViewMic.visibility = INVISIBLE
                        imageViewMic.rotation = 0f

                        // 4. Animate trash can sliding out
                        dustinCover
                            .animate()
                            .rotation(0f)
                            .setDuration(DURATION_DELETE_COVER_CLOSE)
                            .setStartDelay(DELAY_DELETE_COVER_CLOSE)
                            .start()

                        val slideOutAnimator = { view: View ->
                            view
                                .animate()
                                .translationX(trashCanDisplacement)
                                .setDuration(DURATION_DELETE_TRASH_SLIDE_OUT)
                                .setStartDelay(DELAY_DELETE_TRASH_SLIDE_OUT)
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                        }
                        slideOutAnimator(dustin)
                        slideOutAnimator(dustinCover)
                    }.start()
            }.start()
    }

    private fun requireMicrophonePermission(context: Context): Boolean {
        val isGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PermissionChecker.PERMISSION_GRANTED
        if (isGranted) return true
        if (context !is Activity) return false
        ActivityCompat.requestPermissions(
            context,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            0,
        )
        return false
    }

    companion object {
        private const val DURATION_DELETE_MIC_FLY_UP = 500L
        private const val DURATION_DELETE_TRASH_SLIDE_IN = 350L
        private const val DURATION_DELETE_MIC_DROP = 350L
        private const val DURATION_DELETE_COVER_CLOSE = 150L
        private const val DELAY_DELETE_COVER_CLOSE = 50L
        private const val DURATION_DELETE_TRASH_SLIDE_OUT = 200L
        private const val DELAY_DELETE_TRASH_SLIDE_OUT = 250L
        private const val DURATION_DELETE_TOTAL = 1250L
    }
}
