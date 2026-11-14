// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.google.android.material.tabs.TabLayout
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentTemplatePreviewerBinding
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.utils.ext.collectInViewLifecycle
import com.ichi2.anki.utils.ext.doOnTabSelected
import com.ichi2.anki.workarounds.SafeWebViewLayout
import timber.log.Timber

class TemplatePreviewerFragment :
    CardViewerFragment(R.layout.fragment_template_previewer),
    BaseSnackbarBuilderProvider {
    override val viewModel: TemplatePreviewerViewModel by viewModels()

    lateinit var binding: FragmentTemplatePreviewerBinding

    override val webViewLayout: SafeWebViewLayout get() = binding.webViewLayout

    override val baseSnackbarBuilder: SnackbarBuilder
        get() = { anchorView = binding.showAnswer }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        // binding must be set before super.onViewCreated
        // as super.onViewCreated depends on webViewLayout, which depends on the binding
        binding = FragmentTemplatePreviewerBinding.bind(view)

        // The backing NotetypeFile may be missing after process death if the OS cleared
        // its cache dir. Bail out before the ViewModel is constructed; the constructor
        // would otherwise throw on `getNotetype()`.
        if (!TemplatePreviewerArguments.isUsable(requireArguments())) {
            Timber.w("Notetype file missing on previewer open; finishing")
            requireActivity().finish()
            return
        }

        super.onViewCreated(view, savedInstanceState)

        binding.showAnswer.setOnClickListener { viewModel.toggleShowAnswer() }
        viewModel.showingAnswer.collectInViewLifecycle { showingAnswer ->
            binding.showAnswer.text =
                if (showingAnswer) {
                    getString(CommonString.hide_answer)
                } else {
                    getString(CommonString.show_answer)
                }
        }

        binding.webViewContainer.setFrameStyle()
    }

    /**
     * Sets up the previewer tabs with appropriate titles and selection handling.
     *
     * @param tabLayout The tab layout to configure
     */
    suspend fun setupTabs(tabLayout: TabLayout) {
        tabLayout.removeAllTabs()

        val cardsWithEmptyFronts = viewModel.getCardsWithEmptyFronts()
        val templateNames = viewModel.getTemplateNames()

        for ((index, templateName) in templateNames.withIndex()) {
            val tabTitle =
                if (cardsWithEmptyFronts[index]) {
                    getString(CommonString.card_previewer_empty_front_indicator, templateName)
                } else {
                    templateName
                }
            val newTab = tabLayout.newTab().setText(tabTitle)
            tabLayout.addTab(newTab)
        }

        // Ensure tab index is within valid range
        val currentTabIndex = viewModel.getCurrentTabIndex()
        val safeTabIndex = currentTabIndex.coerceIn(0, maxOf(0, templateNames.size - 1))

        if (templateNames.isNotEmpty()) {
            tabLayout.selectTab(tabLayout.getTabAt(safeTabIndex))
            // Update ViewModel if index was adjusted
            if (safeTabIndex != currentTabIndex) {
                viewModel.onTabSelected(safeTabIndex)
            }
        }

        // Remove any existing listeners to avoid duplicates
        tabLayout.clearOnTabSelectedListeners()
        tabLayout.doOnTabSelected { tab ->
            Timber.v("Selected tab %d", tab.position)
            viewModel.onTabSelected(tab.position)
        }
    }

    /**
     * Updates the content displayed in the previewer with the provided fields and tags
     *
     * Should not be called for cloze deletions, since they they have dynamic ord
     *
     * @param fields The list of field values to display
     * @param tags The list of tags associated with the note
     */
    fun updateContent(
        fields: List<String>,
        tags: List<String>,
    ) {
        viewModel.updateContent(fields, tags)
    }

    /**
     * Retrieves a safe cloze ordinal number for cloze deletions.
     *
     * @return The safe cloze ordinal number
     */
    suspend fun getSafeClozeOrd(): CardOrdinal = viewModel.getSafeClozeOrd()

    companion object {
        const val ARG_KEY = "arg_key"

        fun newInstance(arguments: TemplatePreviewerArguments): TemplatePreviewerFragment =
            TemplatePreviewerFragment().apply {
                val args = Bundle().apply { putParcelable(ARG_KEY, arguments) }
                this.arguments = args
            }
    }
}
