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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.ThemeUtils
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AbstractFlashcardViewer.Companion.RESULT_NO_MORE_CARDS
import com.ichi2.anki.NoteEditor
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.CardMediaPlayer
import com.ichi2.anki.pages.CardInfo.Companion.toIntent
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.CardViewerFragment
import com.ichi2.anki.previewer.stdHtml
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.anki.utils.ext.collectLatestIn
import com.ichi2.anki.utils.navBarNeedsScrim
import com.ichi2.themes.Themes
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import kotlinx.coroutines.launch

class ReviewerFragment :
    CardViewerFragment(R.layout.reviewer2),
    BaseSnackbarBuilderProvider,
    Toolbar.OnMenuItemClickListener {

    override val viewModel: ReviewerViewModel by viewModels {
        ReviewerViewModel.factory(CardMediaPlayer())
    }

    override val webView: WebView
        get() = requireView().findViewById(R.id.webview)

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = this@ReviewerFragment.view?.findViewById(R.id.buttons_area)
    }

    private val editCardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.handleEditCardResult(result)
        }

    private val addCardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        }

    override fun onLoadData(): String {
        return stdHtml(
            requireContext(),
            Themes.currentTheme.isNightMode,
            listOf("scripts/ankidroid.js")
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAnswerButtons(view)

        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setOnMenuItemClickListener(this@ReviewerFragment)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            (menu as? MenuBuilder)?.let {
                it.setOptionalIconsVisible(true)
                requireContext().increaseHorizontalPaddingOfOverflowMenuIcons(it)
            }
        }

        with(requireActivity()) {
            if (!navBarNeedsScrim) {
                window.navigationBarColor =
                    ThemeUtils.getThemeAttrColor(this, R.attr.alternativeBackgroundColor)
            }
        }

        viewModel.isQueueFinishedFlow.collectIn(lifecycleScope) { isQueueFinished ->
            if (isQueueFinished) {
                requireActivity().run {
                    setResult(RESULT_NO_MORE_CARDS)
                    finish()
                }
            }
        }

        viewModel.statesMutationEval.collectIn(lifecycleScope) { eval ->
            webView.evaluateJavascript(eval) { _ ->
                viewModel.onStateMutationCallback()
            }
        }

        viewModel.snackbarMessageFlow.flowWithLifecycle(lifecycle)
            .collectIn(lifecycleScope) {
                showSnackbar(it, Snackbar.LENGTH_SHORT)
            }

        setupReviewerSettings(view)
        setupActions(view)
    }

    private fun setupActions(view: View) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val menu = toolbar.menu
        // Mark
        val markItem = menu.findItem(R.id.action_mark)
        viewModel.isMarked.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { isMarked ->
                if (isMarked) {
                    markItem.setIcon(R.drawable.ic_star)
                    markItem.setTitle(R.string.menu_unmark_note)
                } else {
                    markItem.setIcon(R.drawable.ic_star_border_white)
                    markItem.setTitle(R.string.menu_mark_note)
                }
            }

        // Undo
        val undoItem = menu.findItem(R.id.action_undo)
        viewModel.undoTitleFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { title ->
                undoItem.title = title
            }
        viewModel.isUndoAvailableFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { isEnabled ->
                undoItem.isEnabled = isEnabled
            }
        // Redo
        val redoItem = menu.findItem(R.id.action_redo)
        viewModel.redoTitleFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { title ->
                redoItem.title = title
            }
        viewModel.isRedoAvailableFlow.flowWithLifecycle(lifecycle)
            .collectLatestIn(lifecycleScope) { isEnabled ->
                redoItem.isEnabled = isEnabled
            }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupReviewerSettings(view: View) {
        // TODO: Hide toolbar setting
//        view.findViewById<AppBarLayout>(R.id.appbar).isVisible = false
    }

    // TODO
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> editCard()
            R.id.action_add -> addCard()
            R.id.action_card_info -> cardInfo()
            R.id.action_mark -> viewModel.toggleMark()
            R.id.action_undo -> viewModel.undo()
            R.id.action_redo -> viewModel.redo()
            R.id.action_delete -> viewModel.deleteNote()
            R.id.action_tag, R.id.action_flag -> showSnackbar("Not yet implemented")
            R.id.user_action_1 -> viewModel.userAction(1)
            R.id.user_action_2 -> viewModel.userAction(2)
            R.id.user_action_3 -> viewModel.userAction(3)
            R.id.user_action_4 -> viewModel.userAction(4)
            R.id.user_action_5 -> viewModel.userAction(5)
            R.id.user_action_6 -> viewModel.userAction(6)
            R.id.user_action_7 -> viewModel.userAction(7)
            R.id.user_action_8 -> viewModel.userAction(8)
            R.id.user_action_9 -> viewModel.userAction(9)
            else -> return false
        }
        return true
    }

    private fun setupAnswerButtons(view: View) {
        fun MaterialButton.setAnswerButtonNextTime(@StringRes title: Int, nextTime: String?) {
            val titleString = context.getString(title)
            text = ReviewerViewModel.getAnswerButtonText(titleString, nextTime)
        }

        // Again button
        view.findViewById<MaterialButton>(R.id.again_button).apply {
            setOnClickListener { viewModel.answerAgain() }
            viewModel.againNextTime.collectLatestIn(lifecycleScope) { nextTime ->
                setAnswerButtonNextTime(R.string.ease_button_again, nextTime)
            }
        }

        // Hard button
        view.findViewById<MaterialButton>(R.id.hard_button).apply {
            setOnClickListener { viewModel.answerHard() }
            viewModel.hardNextTime.collectLatestIn(lifecycleScope) { nextTime ->
                setAnswerButtonNextTime(R.string.ease_button_hard, nextTime)
            }
        }

        // Good button
        view.findViewById<MaterialButton>(R.id.good_button).apply {
            setOnClickListener { viewModel.answerGood() }
            viewModel.goodNextTime.collectLatestIn(lifecycleScope) { nextTime ->
                setAnswerButtonNextTime(R.string.ease_button_good, nextTime)
            }
        }

        // Easy button
        view.findViewById<MaterialButton>(R.id.easy_button).apply {
            setOnClickListener { viewModel.answerEasy() }
            viewModel.easyNextTime.collectLatestIn(lifecycleScope) { nextTime ->
                setAnswerButtonNextTime(R.string.ease_button_easy, nextTime)
            }
        }

        val showAnswerButton = view.findViewById<MaterialButton>(R.id.show_answer).apply {
            setOnClickListener {
                viewModel.showAnswer()
            }
        }
        val answerButtonsLayout = view.findViewById<ConstraintLayout>(R.id.answer_buttons)

        viewModel.showingAnswer.collectLatestIn(lifecycleScope) { shouldShowAnswer ->
            val (toShow, toHide) = if (shouldShowAnswer) {
                answerButtonsLayout to showAnswerButton
            } else {
                showAnswerButton to answerButtonsLayout
            }
            val duration = 60L
            toHide.animate()
                .setDuration(duration)
                .alpha(0F)
                .withEndAction {
                    toHide.visibility = View.GONE
                    toShow.alpha = 0F
                    toShow.visibility = View.VISIBLE
                    toShow.animate()
                        .setDuration(duration)
                        .alpha(1F)
                        .start()
                }
                .start()
        }
    }

    private fun editCard() {
        lifecycleScope.launch {
            val intent = viewModel.getNoteEditorDestination().toIntent(requireContext())
            editCardLauncher.launch(intent)
        }
    }

    private fun addCard() {
        val intent = Intent(context, NoteEditor::class.java).apply {
            putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_REVIEWER_ADD)
        }
        addCardLauncher.launch(intent)
    }

    private fun cardInfo() {
        lifecycleScope.launch {
            val intent = viewModel.getCardInfoDestination().toIntent(requireContext())
            startActivity(intent)
        }
    }

    companion object {
        fun getIntent(context: Context): Intent {
            return CardViewerActivity.getIntent(context, ReviewerFragment::class)
        }
    }
}
