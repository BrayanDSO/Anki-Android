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
 * This file incorporates code from https://github.com/varunjohn/Audio-Recording-Animation
 * under the Apache License, Version 2.0.
 */
package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.util.TypedValueCompat
import com.ichi2.anki.R
import kotlin.math.abs

class AudioRecordView : FrameLayout {
    // region Views
    private val recordButton: View
    private val recordIcon: ImageView
    private val lockArrow: View
    private val imageViewLock: View
    private val imageViewMic: View
    private val dustin: View
    private val dustinCover: View
    private val layoutSlideCancel: View
    private val layoutLock: View
    private val chronometer: Chronometer
    private val textViewSlide: TextView
    // endregion

    // region Animations
    private val animBlink = AnimationUtils.loadAnimation(context, R.anim.blink)
    private val animJump = AnimationUtils.loadAnimation(context, R.anim.jump)
    private val animJumpFast = AnimationUtils.loadAnimation(context, R.anim.jump_fast)
    // endregion

    // region State & Logic
    private var state = ViewState.IDLE
    private var stopTrackingAction = false

    private var firstX = 0f
    private var firstY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val cancelOffset: Float
    private val lockOffset: Float
    private val dp = TypedValueCompat.dpToPx(1F, resources.displayMetrics)

    private var recordingListener: RecordingListener? = null
    private lateinit var gestureDetector: GestureDetector
    // endregion

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
        recordIcon = findViewById(R.id.recordIcon)
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
    }

    fun setRecordingListener(recordingListener: RecordingListener) {
        this.recordingListener = recordingListener
    }

    private enum class ViewState {
        IDLE,
        RECORDING,
        LOCKED,
        DELETING,
    }

    enum class RecordingBehavior {
        CANCEL,
        LOCK,
        RELEASE,
    }

    interface RecordingListener {
        fun onRecordingPermissionRequired()

        fun onRecordingStarted()

        fun onRecordingCanceled()

        fun onRecordingCompleted()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        gestureDetector =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        recordButton
                            .animate()
                            .scaleX(1.25f)
                            .scaleY(1.25f)
                            .setDuration(150)
                            .start()
                        return true
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (!hasMicrophonePermission()) {
                            recordingListener?.onRecordingPermissionRequired()
                            reset(animate = true)
                            return true
                        }
                        startRecord(showHints = false)
                        lock()
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (!hasMicrophonePermission()) {
                            recordingListener?.onRecordingPermissionRequired()
                            return
                        }
                        startRecord(showHints = true)
                        firstX = e.rawX
                        firstY = e.rawY
                    }
                },
            )
        recordButton.setOnTouchListener(gestureListener)
    }

    private val gestureListener =
        OnTouchListener { _, motionEvent ->
            gestureDetector.onTouchEvent(motionEvent)

            if (motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_CANCEL) {
                if (state == ViewState.IDLE) {
                    reset(animate = true)
                }
            }

            if (state == ViewState.DELETING) return@OnTouchListener true

            if (state == ViewState.RECORDING) {
                when (motionEvent.action) {
                    MotionEvent.ACTION_UP -> stopRecording(RecordingBehavior.RELEASE)
                    MotionEvent.ACTION_MOVE -> handleMove(motionEvent)
                }
            }
            true
        }

    private fun handleMove(motionEvent: MotionEvent) {
        if (stopTrackingAction) return

        val behavior = getBehaviorFromDirection(motionEvent.rawX, motionEvent.rawY)
        when (behavior) {
            RecordingBehavior.CANCEL -> translateX(motionEvent.rawX - firstX)
            RecordingBehavior.LOCK -> translateY(motionEvent.rawY - firstY)
            else -> {}
        }

        lastX = motionEvent.rawX
        lastY = motionEvent.rawY
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PermissionChecker.PERMISSION_GRANTED

    private fun getBehaviorFromDirection(
        currentX: Float,
        currentY: Float,
    ): RecordingBehavior? {
        val motionX = abs(firstX - currentX)
        val motionY = abs(firstY - currentY)

        return when {
            motionY > motionX && currentY < firstY -> RecordingBehavior.LOCK
            motionX > motionY && currentX < firstX -> RecordingBehavior.CANCEL
            else -> null
        }
    }

    private fun translateY(y: Float) {
        if (y < -lockOffset) {
            lock()
            recordButton.translationY = 0f
            return
        }

        layoutLock.visibility = VISIBLE
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
            layoutLock.visibility = VISIBLE
        } else {
            layoutLock.visibility = GONE
        }
    }

    private fun startRecord(showHints: Boolean) {
        if (state != ViewState.IDLE) return

        state = ViewState.RECORDING
        stopTrackingAction = false
        recordingListener?.onRecordingStarted()

        showRecordingAnimation(showHints)
    }

    private fun showRecordingAnimation(showHints: Boolean) {
        chronometer.visibility = VISIBLE
        imageViewMic.visibility = VISIBLE
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.startAnimation(animBlink)
        chronometer.start()

        if (showHints) {
            recordButton
                .animate()
                .scaleX(1.8f)
                .scaleY(1.8f)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator())
                .start()

            layoutLock.visibility = VISIBLE
            layoutSlideCancel.visibility = VISIBLE
            lockArrow.startAnimation(animJumpFast)
            imageViewLock.startAnimation(animJump)
        }
    }

    private fun lock() {
        if (state != ViewState.RECORDING) return
        state = ViewState.LOCKED
        stopTrackingAction = true

        recordIcon.setImageResource(R.drawable.ic_stop)

        recordButton.animate().cancel()
        recordButton.scaleX = 1f
        recordButton.scaleY = 1f

        recordButton.setOnTouchListener(null)
        recordButton.setOnClickListener {
            stopRecording(RecordingBehavior.LOCK)
        }
        layoutSlideCancel.visibility = GONE
        layoutLock.visibility = GONE
    }

    private fun cancel() {
        stopTrackingAction = true
        stopRecording(RecordingBehavior.CANCEL)
    }

    private fun stopRecording(outcome: RecordingBehavior) {
        if (state != ViewState.RECORDING && state != ViewState.LOCKED) return

        val animateRelease = outcome == RecordingBehavior.RELEASE
        reset(animate = animateRelease)
        chronometer.stop()

        when (outcome) {
            RecordingBehavior.CANCEL -> {
                showDeleteAnimation()
                recordingListener?.onRecordingCanceled()
            }
            RecordingBehavior.RELEASE, RecordingBehavior.LOCK -> {
                recordingListener?.onRecordingCompleted()
            }
        }
    }

    private fun reset(animate: Boolean) {
        state = ViewState.IDLE
        stopTrackingAction = false
        firstX = 0f
        firstY = 0f
        lastX = 0f
        lastY = 0f

        recordIcon.setImageResource(R.drawable.ic_action_mic)
        recordButton.setOnClickListener(null)
        recordButton.setOnTouchListener(gestureListener)

        if (animate) {
            recordButton
                .animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(100)
                .setInterpolator(LinearInterpolator())
                .start()
        } else {
            recordButton.animate().cancel()
            recordButton.scaleX = 1f
            recordButton.scaleY = 1f
            recordButton.translationX = 0f
            recordButton.translationY = 0f
        }

        layoutSlideCancel.visibility = GONE
        layoutLock.visibility = GONE
        chronometer.visibility = INVISIBLE
        imageViewMic.visibility = INVISIBLE
        layoutLock.translationY = 0f

        chronometer.clearAnimation()
        lockArrow.clearAnimation()
        imageViewLock.clearAnimation()
    }

    private fun showDeleteAnimation() {
        state = ViewState.DELETING
        imageViewMic.visibility = VISIBLE
        imageViewMic.rotation = 0f
        recordButton.isEnabled = false

        val trashCanDisplacement = -dp * 40
        imageViewMic
            .animate()
            .translationY(-dp * 150)
            .rotation(360f)
            .scaleX(0.6f)
            .scaleY(0.6f)
            .setDuration(DURATION_DELETE_MIC_FLY_UP)
            .setInterpolator(DecelerateInterpolator())
            .withStartAction {
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
                        }
                        slideOutAnimator(dustin).start()
                        slideOutAnimator(dustinCover)
                            .withEndAction {
                                state = ViewState.IDLE
                                recordButton.isEnabled = true
                            }.start()
                    }.start()
            }.start()
    }

    /**
     * Immediately stops all actions and animations, and returns the view to its initial state.
     */
    fun forceReset() {
        chronometer.stop()
        recordButton.clearAnimation()
        imageViewMic.clearAnimation()
        dustin.clearAnimation()
        dustinCover.clearAnimation()
        reset(animate = false)
    }

    companion object {
        private const val DURATION_DELETE_MIC_FLY_UP = 400L
        private const val DURATION_DELETE_TRASH_SLIDE_IN = 250L
        private const val DURATION_DELETE_MIC_DROP = 250L
        private const val DURATION_DELETE_COVER_CLOSE = 150L
        private const val DELAY_DELETE_COVER_CLOSE = 50L
        private const val DURATION_DELETE_TRASH_SLIDE_OUT = 200L
        private const val DELAY_DELETE_TRASH_SLIDE_OUT = 200L
    }
}
