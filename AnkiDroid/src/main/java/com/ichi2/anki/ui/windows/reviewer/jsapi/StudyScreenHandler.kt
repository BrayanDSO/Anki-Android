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
import com.ichi2.anki.ui.windows.reviewer.jsapi.endpoints.StudyScreenEndpoint
import com.ichi2.anki.utils.ext.collectIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class UiRequest(
    val endpoint: StudyScreenEndpoint,
    val data: JSONObject?,
    val result: CompletableDeferred<ByteArray>,
)

suspend fun ReviewerViewModel.handleJsApiRequest(
    uri: String,
    bytes: ByteArray,
): ByteArray {
    val path = uri.substring(AnkiServer.ANKIDROID_JS_PREFIX.length)
    val (base, endpoint) = path.split('/', limit = 2)
    val request = JsApi.parseRequest(bytes)

    return when (base) {
        StudyScreenEndpoint.BASE -> handleStudyScreenRequest(this, endpoint, request.data)
        else -> JsApi.handleRequest(base, endpoint, bytes)
    }
}

private fun ReviewerFragment.handleJsRequest(request: UiRequest): ByteArray {
    return when (request.endpoint) {
        StudyScreenEndpoint.SHOW_SNACKBAR -> {
            val data = request.data ?: return JsApi.fail("Missing request data")
            val text = data.optString("text") ?: return JsApi.fail("Missing text")
            val duration = data.getIntOrNull("duration") ?: return JsApi.fail("Missing duration")
            showSnackbar(text, duration)
            JsApi.success()
        }
        StudyScreenEndpoint.SET_BACKGROUND_COLOR -> {
            val hex = request.data?.getString("data") ?: return JsApi.fail("Missing hex")
            val color = hex.toColorInt()
            view?.setBackgroundColor(color)
            JsApi.success()
        }
        else -> JsApi.fail("Unhandled API method")
    }
}

fun ReviewerFragment.setupJsApi() {
    viewModel.apiRequestFlow.flowWithLifecycle(lifecycle).collectIn(lifecycleScope) { request ->
        val result = handleJsRequest(request)
        request.result.complete(result)
    }
}

private suspend fun handleStudyScreenRequest(
    viewModel: ReviewerViewModel,
    endpointString: String,
    data: JSONObject?,
): ByteArray {
    val endpoint = StudyScreenEndpoint.from(endpointString) ?: return JsApi.fail("Invalid endpoint")
    return when (endpoint) {
        StudyScreenEndpoint.GET_NEW_COUNT -> JsApi.success(viewModel.countsFlow.value.first.new)
        StudyScreenEndpoint.GET_LRN_COUNT -> JsApi.success(viewModel.countsFlow.value.first.lrn)
        StudyScreenEndpoint.GET_REV_COUNT -> JsApi.success(viewModel.countsFlow.value.first.rev)
        StudyScreenEndpoint.SHOW_ANSWER -> {
            if (viewModel.showingAnswer.value) return JsApi.success()
            viewModel.onShowAnswer()
            JsApi.success()
        }
        StudyScreenEndpoint.ANSWER -> {
            val ratingNumber = data!!.getInt("data") - 1
            val rating = CardAnswer.Rating.forNumber(ratingNumber)
            viewModel.answerCard(rating)
            JsApi.success()
        }
        StudyScreenEndpoint.IS_SHOWING_ANSWER -> JsApi.success(viewModel.showingAnswer.value)
        StudyScreenEndpoint.GET_NEXT_TIME -> {
            val ratingNumber = data!!.getInt("data") - 1
            val rating = CardAnswer.Rating.forNumber(ratingNumber)
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
        StudyScreenEndpoint.CARD_INFO -> {
            val cardId = data?.getLong("data")
            viewModel.emitCardInfoDestination(cardId)
            JsApi.success()
        }
        StudyScreenEndpoint.EDIT_NOTE -> {
            val cardId = data?.getLong("data")
            viewModel.emitEditNoteDestination(cardId)
            JsApi.success()
        }
        StudyScreenEndpoint.SEARCH,
        StudyScreenEndpoint.SHOW_SNACKBAR,
        StudyScreenEndpoint.SET_BACKGROUND_COLOR,
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
