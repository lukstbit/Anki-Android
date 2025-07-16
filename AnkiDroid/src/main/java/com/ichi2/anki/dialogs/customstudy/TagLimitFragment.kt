/****************************************************************************************
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.dialogs.customstudy

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.Group
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.customstudy.IncludedExcludedTagsAdapter.TagsSelectionMode.Exclude
import com.ichi2.anki.dialogs.customstudy.IncludedExcludedTagsAdapter.TagsSelectionMode.Include
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import kotlinx.coroutines.launch

/**
 * Fragment that allows the user to select tags to include and/or exclude for the custom study
 * session that is being created. Similar to the desktop TagLimit class.
 * https://github.com/ankitects/anki/blob/main/qt/aqt/taglimit.py#L17
 *
 * Will return, as a fragment result, two lists of tags(as Strings) representing the included and
 * excluded tags for custom studying.
 *
 * @see CustomStudyDialog
 */
class TagLimitFragment : DialogFragment() {
    private val loadingViews: Group?
        get() = dialog?.findViewById(R.id.loading_views_group)
    private val contentViews: Group?
        get() = dialog?.findViewById(R.id.content_views_group)
    private val deckId
        get() = requireArguments().getLong(ARG_DECK_ID)
    private lateinit var tagsIncludedAdapter: IncludedExcludedTagsAdapter
    private lateinit var tagsExcludedAdapter: IncludedExcludedTagsAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tagsIncludedAdapter = IncludedExcludedTagsAdapter(context, Include)
        tagsExcludedAdapter = IncludedExcludedTagsAdapter(context, Exclude)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView = layoutInflater.inflate(R.layout.fragment_tag_limit, null)
        dialogView.findViewById<TextInputEditText>(R.id.input_tags_filter).doOnTextChanged { text, _, _, _ ->
            tagsIncludedAdapter.filter.filter(text)
            tagsExcludedAdapter.filter.filter(text)
        }
        val tabLayout =
            dialogView.findViewById<TabLayout>(R.id.tabs_tags).apply {
                addTab(newTab().setText("Included"))
                addTab(newTab().setText("Excluded"))
            }
        val pager =
            dialogView.findViewById<ViewPager2>(R.id.pager).apply {
                adapter = TagsPageAdapter()
            }
        TabLayoutMediator(tabLayout, pager) { tab, index ->
            tab.text =
                when (index) {
                    0 -> "Included"
                    1 -> "Excluded"
                    else -> error("Unexpected custom study tag category")
                }
        }.attach()
        val title =
            TR
                .customStudySelectiveStudy()
                .toSentenceCase(requireContext(), R.string.sentence_selective_study)
        val dialog =
            AlertDialog
                .Builder(requireContext())
                .title(text = title)
                .customView(dialogView)
                .negativeButton(R.string.dialog_cancel)
                .positiveButton(R.string.dialog_ok, null)
                .create()

        var allowSubmit = true
        // we set the listener here so 'ok' doesn't immediately close the dialog because we don't
        // allow more than 100 tags combined to be selected. In this situation we don't close the
        // dialog(so the user can still act to fix this) and show a Snackbar
        dialog.setOnShowListener {
            dialog.positiveButton.setOnClickListener {
                // prevent race conditions
                if (!allowSubmit) return@setOnClickListener
                allowSubmit = false
                val tagsToInclude =
                    tagsIncludedAdapter.tags.filter { it.isIncluded }.map { it.name }
                val tagsToExclude =
                    tagsExcludedAdapter.tags.filter { it.isExcluded }.map { it.name }
                if (tagsToInclude.size + tagsToExclude.size > 100) {
                    showSnackbar(TR.errors100TagsMax().withCollapsedWhitespace())
                    return@setOnClickListener
                }
                // send the selection to the custom study dialog to setup the session
                setFragmentResult(
                    REQUEST_CUSTOM_STUDY_TAGS,
                    bundleOf(
                        KEY_INCLUDED_TAGS to ArrayList(tagsToInclude),
                        KEY_EXCLUDED_TAGS to ArrayList(tagsToExclude),
                    ),
                )
                dismiss()
            }
        }
        return dialog
    }

    // https://github.com/ankitects/anki/blob/main/pylib/anki/lang.py#L243
    private fun String.withCollapsedWhitespace(): String = replace("\\s+", " ")

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            loadingViews?.isVisible = true
            contentViews?.isVisible = false
            val customStudyDefaults = withCol { sched.customStudyDefaults(deckId) }
            dialog?.findViewById<CheckBox>(R.id.tag_selection_require_check)?.isChecked =
                customStudyDefaults.tagsList.any { tag -> tag.include }
            val tags =
                customStudyDefaults.tagsList.map { backendTag ->
                    TagIncludedExcluded(backendTag.name, backendTag.include, backendTag.exclude)
                }
            tagsIncludedAdapter.tags = tags.toMutableList() // make a copy
            tagsExcludedAdapter.tags = tags.toMutableList() // make a copy
            loadingViews?.isVisible = false
            contentViews?.isVisible = true
        }
    }

    private inner class TagsPageAdapter : RecyclerView.Adapter<TagsPageViewHolder>() {
        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): TagsPageViewHolder = TagsPageViewHolder(layoutInflater.inflate(R.layout.item_tag_page, parent, false))

        override fun getItemCount(): Int = 2 // two pages: included & excluded tags

        override fun onBindViewHolder(
            holder: TagsPageViewHolder,
            position: Int,
        ) {
            when (position) {
                0 -> {
                    holder.pageList.adapter = tagsIncludedAdapter
                    holder.pageLabel.text = TR.customStudyRequireOneOrMoreOfThese()
                }
                1 -> {
                    holder.pageList.adapter = tagsExcludedAdapter
                    holder.pageLabel.text = TR.customStudySelectTagsToExclude()
                }
                else -> {
                    error("Unexpected custom study tags page")
                }
            }
        }
    }

    private class TagsPageViewHolder(
        rowView: View,
    ) : RecyclerView.ViewHolder(rowView) {
        val pageList: RecyclerView = rowView.findViewById(R.id.tags_page_list)
        val pageLabel: TextView = rowView.findViewById(R.id.tag_page_label)
    }

    companion object {
        const val TAG = "TagLimitFragment"
        const val REQUEST_CUSTOM_STUDY_TAGS = "request_custom_study_tags"
        const val KEY_INCLUDED_TAGS = "key_included_tags"
        const val KEY_EXCLUDED_TAGS = "key_excluded_tags"
        private const val ARG_DECK_ID = "arg_deck_id"

        fun newInstance(deckId: DeckId) =
            TagLimitFragment().apply {
                arguments = bundleOf(ARG_DECK_ID to deckId)
            }
    }
}
