// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentTemplatePreviewerContainerBinding
import com.ichi2.anki.previewer.TemplatePreviewerFragment.Companion.ARG_KEY
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch

/**
 * Container for [TemplatePreviewerFragment] that works as a standalone page
 * by including a toolbar and a TabLayout for changing the current template.
 */
class TemplatePreviewerPage : Fragment(R.layout.fragment_template_previewer_container) {
    private val binding by viewBinding(FragmentTemplatePreviewerContainerBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val fragment: TemplatePreviewerFragment
        if (savedInstanceState == null) {
            val arguments = BundleCompat.getParcelable(requireArguments(), ARG_KEY, TemplatePreviewerArguments::class.java)!!
            fragment = TemplatePreviewerFragment.newInstance(arguments)
            childFragmentManager.commitNow {
                replace(R.id.fragment_container, fragment)
            }
        } else {
            fragment = childFragmentManager.findFragmentById(R.id.fragment_container) as TemplatePreviewerFragment
        }

        lifecycleScope.launch { fragment.setupTabs(binding.tabLayout) }
    }

    companion object {
        fun getIntent(
            context: Context,
            arguments: TemplatePreviewerArguments,
        ): Intent =
            CardViewerActivity.getIntent(
                context,
                TemplatePreviewerPage::class,
                Bundle().apply { putParcelable(ARG_KEY, arguments) },
            )
    }
}
