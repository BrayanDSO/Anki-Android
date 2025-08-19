/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.con>
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
package com.ichi2.anki.ui.windows.reviewer.jsapi

import androidx.core.graphics.toColorInt
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import anki.scheduler.CardAnswer
import com.ichi2.anki.common.utils.ext.getIntOrNull
import com.ichi2.anki.pages.AnkiServer
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.reviewer.AnswerButtonsNextTime
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.anki.utils.ext.collectIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.test.fail

suspend fun ReviewerViewModel.handleJsApiRequest(
    uri: String,
    bytes: ByteArray,
): ByteArray {
    val path = uri.substring(AnkiServer.ANKIDROID_JS_PREFIX.length)
    val (base, endpointStr) = path.split('/', limit = 2)
    val request = JsApi.parseRequest(bytes)
    val endpoint = Endpoint.from(base, endpointStr) ?: return JsApi.fail("Invalid endpoint")

    return when (endpoint) {
        is Endpoint.StudyScreen -> handleStudyScreenRequest(this, endpoint, request.data)
        else -> JsApi.handleRequest(endpoint, bytes)
    }
}

fun ReviewerFragment.setupJsApi() {
    viewModel.apiRequestFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { request ->
        val result = handleJsUiRequest(request)
        request.result.complete(result)
    }
}

private fun ReviewerFragment.handleJsUiRequest(request: UiRequest): ByteArray {
    return when (request.endpoint) {
        Endpoint.StudyScreen.SHOW_SNACKBAR -> {
            val data = request.data ?: return JsApi.fail("Missing request data")
            val text = data.optString("text") ?: return JsApi.fail("Missing text")
            val duration = data.getIntOrNull("duration") ?: return JsApi.fail("Missing duration")
            showSnackbar(text, duration)
            JsApi.success()
        }
        Endpoint.StudyScreen.SET_BACKGROUND_COLOR -> {
            val hexCode = request.data?.getString("hexCode") ?: return JsApi.fail("Missing hex code")
            val color =
                try {
                    hexCode.toColorInt()
                } catch (_: IllegalArgumentException) {
                    return JsApi.fail("Invalid hex code")
                }
            view?.setBackgroundColor(color)
            JsApi.success()
        }
        else -> JsApi.fail("Unhandled API method")
    }
}

private suspend fun handleStudyScreenRequest(
    viewModel: ReviewerViewModel,
    endpoint: Endpoint.StudyScreen,
    data: JSONObject?,
): ByteArray {
    return when (endpoint) {
        Endpoint.StudyScreen.GET_NEW_COUNT -> JsApi.success(viewModel.countsFlow.value.first.new)
        Endpoint.StudyScreen.GET_LEARNING_COUNT -> JsApi.success(viewModel.countsFlow.value.first.lrn)
        Endpoint.StudyScreen.GET_TO_REVIEW_COUNT -> JsApi.success(viewModel.countsFlow.value.first.rev)
        Endpoint.StudyScreen.SHOW_ANSWER -> {
            if (viewModel.showingAnswer.value) return JsApi.success()
            viewModel.onShowAnswer()
            JsApi.success()
        }
        Endpoint.StudyScreen.ANSWER -> {
            val ratingNumber = data?.getIntOrNull("rating") ?: return JsApi.fail("Missing rating")
            if (ratingNumber !in 1..4) {
                return JsApi.fail("Invalid rating")
            }
            val rating = CardAnswer.Rating.forNumber(ratingNumber - 1)
            viewModel.answerCard(rating)
            JsApi.success()
        }
        Endpoint.StudyScreen.IS_SHOWING_ANSWER -> JsApi.success(viewModel.showingAnswer.value)
        Endpoint.StudyScreen.GET_NEXT_TIME -> {
            val ratingNumber = data?.getIntOrNull("rating") ?: return JsApi.fail("Missing rating")
            if (ratingNumber !in 1..4) {
                return JsApi.fail("Invalid rating")
            }
            val rating = CardAnswer.Rating.forNumber(ratingNumber - 1)

            val queueState = viewModel.queueState.await() ?: return JsApi.fail("There is no card at top of the queue")

            val nextTimes = AnswerButtonsNextTime.from(queueState)
            val nextTime =
                when (rating) {
                    CardAnswer.Rating.AGAIN -> nextTimes.again
                    CardAnswer.Rating.HARD -> nextTimes.hard
                    CardAnswer.Rating.GOOD -> nextTimes.good
                    CardAnswer.Rating.EASY -> nextTimes.easy
                    CardAnswer.Rating.UNRECOGNIZED -> return JsApi.fail("Invalid rating")
                }
            JsApi.success(nextTime)
        }
        Endpoint.StudyScreen.OPEN_CARD_INFO -> {
            val cardId = data?.getLong("cardId")
            viewModel.emitCardInfoDestination(cardId)
            JsApi.success()
        }
        Endpoint.StudyScreen.OPEN_NOTE_EDITOR -> {
            val cardId = data?.getLong("cardId")
            viewModel.emitEditNoteDestination(cardId)
            JsApi.success()
        }
        Endpoint.StudyScreen.UNDO -> {
            viewModel.undo()
            JsApi.success()
        }
        Endpoint.StudyScreen.DELETE_NOTE -> {
            viewModel.deleteNote()
            JsApi.success()
        }
        // UI requests
        Endpoint.StudyScreen.SEARCH,
        Endpoint.StudyScreen.SHOW_SNACKBAR,
        Endpoint.StudyScreen.SET_BACKGROUND_COLOR,
        -> {
            val result = CompletableDeferred<ByteArray>()
            val request = UiRequest(endpoint, data, result)
            viewModel.apiRequestFlow.emit(request)
            // there may be no listeners for the flow, so fail the result after some time
            // e.g. the fragment uses flowWithLifecycle and is at a different lifecycleState
            withTimeoutOrNull(2000L) {
                result.await()
            } ?: JsApi.fail("Method was not handled")
        }
    }
}
