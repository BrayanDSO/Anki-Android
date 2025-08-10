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
    private var isRecording = false

    private var userBehavior = UserBehavior.NONE

    private var recordingListener: RecordingListener? = null
    private lateinit var gestureDetector: GestureDetector

    fun setRecordingListener(recordingListener: RecordingListener) {
        this.recordingListener = recordingListener
    }

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
                        // Provide immediate feedback on press
                        recordButton
                            .animate()
                            .scaleX(1.2f)
                            .scaleY(1.2f)
                            .setDuration(150)
                            .start()
                        return true // Must return true to receive other events
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (!hasMicrophonePermission()) {
                            recordingListener?.onRecordingPermissionRequired()
                            // Reset animation if permission is not granted
                            recordButton
                                .animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start()
                            return true
                        }
                        startRecord(showHints = false, animateScale = false)
                        lock()
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (!hasMicrophonePermission()) {
                            recordingListener?.onRecordingPermissionRequired()
                            return
                        }
                        // The button is already scaled from onDown, now start the main recording animation
                        startRecord(showHints = true, animateScale = true)
                        firstX = e.rawX
                        firstY = e.rawY
                    }
                },
            )
        // Set the initial listener for gestures
        recordButton.setOnTouchListener(gestureListener)
    }

    private val gestureListener =
        OnTouchListener { _, motionEvent ->
            gestureDetector.onTouchEvent(motionEvent)

            if (motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_CANCEL) {
                if (!isRecording && !isLocked) {
                    recordButton
                        .animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
            }

            if (isDeleting) return@OnTouchListener true

            // Handle move and up actions for a long press
            if (isRecording && !isLocked) {
                when (motionEvent.action) {
                    MotionEvent.ACTION_UP -> {
                        stopRecording(RecordingBehaviour.RELEASED)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (stopTrackingAction) return@OnTouchListener true

                        val behavior = getBehaviorFromDirection(motionEvent.rawX, motionEvent.rawY)
                        if (behavior != userBehavior) {
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
            }
            true
        }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PermissionChecker.PERMISSION_GRANTED

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
        isLocked = true
        recordIcon.setImageResource(R.drawable.ic_stop)

        // Reset the button's scale to its original size when locking
        recordButton.animate().cancel()
        recordButton.scaleX = 1f
        recordButton.scaleY = 1f

        // When locked, the button should only respond to a simple click to stop
        recordButton.setOnTouchListener(null)
        recordButton.setOnClickListener {
            stopRecording(RecordingBehaviour.LOCK_DONE)
        }
        layoutSlideCancel.visibility = GONE
        layoutLock.visibility = GONE
    }

    private fun cancel() {
        stopTrackingAction = true
        stopRecording(RecordingBehaviour.CANCELED)
    }

    private fun stopRecording(recordingBehaviour: RecordingBehaviour) {
        if (!isRecording) return

        isRecording = false
        isLocked = false
        stopTrackingAction = true
        firstX = 0f
        firstY = 0f
        lastX = 0f
        lastY = 0f
        userBehavior = UserBehavior.NONE

        // Restore the gesture listener and mic icon
        recordIcon.setImageResource(R.drawable.ic_action_mic)
        recordButton.setOnClickListener(null)
        recordButton.setOnTouchListener(gestureListener)

        if (recordingBehaviour == RecordingBehaviour.RELEASED) {
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
            recordButton.scaleX = 1f
            recordButton.scaleY = 1f
            recordButton.translationX = 0f
            recordButton.translationY = 0f
        }

        layoutSlideCancel.translationX = 0f
        layoutSlideCancel.visibility = GONE

        layoutLock.visibility = GONE
        layoutLock.translationY = 0f
        lockArrow.clearAnimation()
        imageViewLock.clearAnimation()

        when (recordingBehaviour) {
            RecordingBehaviour.CANCELED -> {
                chronometer.clearAnimation()
                chronometer.stop()
                chronometer.visibility = INVISIBLE
                imageViewMic.visibility = INVISIBLE
                delete()
                recordingListener?.onRecordingCanceled()
            }
            RecordingBehaviour.RELEASED, RecordingBehaviour.LOCK_DONE -> {
                chronometer.clearAnimation()
                chronometer.stop()
                chronometer.visibility = INVISIBLE
                imageViewMic.visibility = INVISIBLE
                recordingListener?.onRecordingCompleted()
            }
            RecordingBehaviour.LOCKED -> {
                // This case is now handled by the lock() method directly
            }
        }
    }

    private fun startRecord(
        showHints: Boolean,
        animateScale: Boolean,
    ) {
        if (isRecording) return
        isRecording = true
        recordingListener?.onRecordingStarted()
        stopTrackingAction = false

        if (animateScale) {
            recordButton
                .animate()
                .scaleX(2f)
                .scaleY(2f)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator())
                .start()
        }

        chronometer.visibility = VISIBLE
        imageViewMic.visibility = VISIBLE

        if (showHints) {
            layoutLock.visibility = VISIBLE
            layoutSlideCancel.visibility = VISIBLE
            lockArrow.startAnimation(animJumpFast)
            imageViewLock.startAnimation(animJump)
        }

        chronometer.startAnimation(animBlink)
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.start()
    }

    private fun delete() {
        imageViewMic.visibility = VISIBLE
        imageViewMic.rotation = 0f
        isDeleting = true
        recordButton.isEnabled = false

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
                        isDeleting = false
                        recordButton.isEnabled = true
                    }.start()
            }.start()
    }

    fun cancelRecording() {
        if (!isRecording || isDeleting) {
            return
        }
        if (isLocked) {
            isLocked = false
        }
        stopRecording(RecordingBehaviour.CANCELED)
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
