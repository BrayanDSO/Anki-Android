/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.ThemeUtils
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_NO_MORE_CARDS
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.SoundPlayer
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.launchCollect
import com.ichi2.anki.utils.navBarNeedsScrim
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ReviewerFragment :
    CardViewerFragment(R.layout.reviewer2),
    BaseSnackbarBuilderProvider,
    Toolbar.OnMenuItemClickListener {

    override val viewModel: ReviewerViewModel by viewModels {
        ReviewerViewModel.factory(SoundPlayer())
    }

    override val webView: WebView
        get() = requireView().findViewById(R.id.webview)

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = this@ReviewerFragment.view?.findViewById(R.id.buttons_area)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setOnMenuItemClickListener(this@ReviewerFragment)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            (menu as? MenuBuilder)?.let {
                it.setOptionalIconsVisible(true)
                requireContext().increaseHorizontalPaddingOfOverflowMenuIcons(it)
            }
        }

        val showAnswerButton = view.findViewById<MaterialButton>(R.id.show_answer).apply {
            setOnClickListener {
                viewModel.showAnswer()
            }
        }
        val answerButtonsLayout = view.findViewById<ConstraintLayout>(R.id.answer_buttons)

        view.findViewById<MaterialButton>(R.id.again_button)
            .setOnClickListener {
                viewModel.answerAgain()
            }

        view.findViewById<MaterialButton>(R.id.hard_button)
            .setOnClickListener {
                viewModel.answerHard()
            }

        view.findViewById<MaterialButton>(R.id.good_button)
            .setOnClickListener {
                viewModel.answerGood()
            }

        view.findViewById<MaterialButton>(R.id.easy_button)
            .setOnClickListener {
                viewModel.answerEasy()
            }

        viewModel.showingAnswer.onEach { shouldShowAnswer ->
            val (toShow, toHide) = if (shouldShowAnswer) {
                answerButtonsLayout to showAnswerButton
            } else {
                showAnswerButton to answerButtonsLayout
            }
            val animationDuration = 60L
            toHide.animate()
                .setDuration(animationDuration)
                .alpha(0F)
                .withEndAction {
                    toHide.visibility = View.GONE
                    toShow.alpha = 0F
                    toShow.visibility = View.VISIBLE
                    toShow.animate()
                        .setDuration(animationDuration)
                        .alpha(1F)
                        .start()
                }
                .start()
        }.launchIn(lifecycleScope)

        with(requireActivity()) {
            if (!navBarNeedsScrim) {
                window.navigationBarColor =
                    ThemeUtils.getThemeAttrColor(this, R.attr.alternativeBackgroundColor)
            }
        }

        viewModel.isQueueFinishedFlow.launchCollect(lifecycleScope) { isQueueFinished ->
            if (isQueueFinished) {
                requireActivity().run {
                    setResult(RESULT_NO_MORE_CARDS)
                    finish()
                }
            }
        }
    }

    // TODO
    override fun onMenuItemClick(item: MenuItem?): Boolean {
        showSnackbar("Not yet implemented")
        return true
    }

    companion object {
        fun getIntent(context: Context): Intent {
            return CardViewerActivity.getIntent(context, ReviewerFragment::class)
        }
    }
}
