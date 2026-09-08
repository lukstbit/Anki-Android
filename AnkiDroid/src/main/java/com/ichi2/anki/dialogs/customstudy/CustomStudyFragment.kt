// SPDX-FileCopyrightText: 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.customstudy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import anki.scheduler.copy
import anki.scheduler.customStudyRequest
import anki.search.SearchNode
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.databinding.FragmentCustomStudyNewBinding
import com.ichi2.anki.dialogs.customstudy.CustomStudyCardState.AllCardsRandom
import com.ichi2.anki.dialogs.customstudy.CustomStudyCardState.DueCardsOnly
import com.ichi2.anki.dialogs.customstudy.CustomStudyCardState.NewCardsOnly
import com.ichi2.anki.dialogs.customstudy.CustomStudyCardState.ReviewCardsRandom
import com.ichi2.anki.dialogs.customstudy.CustomStudyOption.EXTEND_NEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyOption.EXTEND_REV
import com.ichi2.anki.dialogs.customstudy.CustomStudyOption.STUDY_AHEAD
import com.ichi2.anki.dialogs.customstudy.CustomStudyOption.STUDY_FORGOT
import com.ichi2.anki.dialogs.customstudy.CustomStudyOption.STUDY_PREVIEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyOption.STUDY_TAGS
import com.ichi2.anki.dialogs.customstudy.CustomStudyViewModel.Companion.KEY_DID
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogListener.Companion.ON_SELECTED_TAGS_KEY
import com.ichi2.anki.dialogs.tags.TagsDialogListener.Companion.ON_SELECTED_TAGS__SELECTED_TAGS
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ConfigAwareSingleFragmentActivity
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.anki.withProgress
import com.ichi2.utils.textAsIntOrNull
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import timber.log.Timber

class CustomStudyFragment : Fragment(R.layout.fragment_custom_study_new) {
    @VisibleForTesting(otherwise = PRIVATE)
    val viewModel by viewModels<CustomStudyViewModel>()

    @VisibleForTesting(otherwise = PRIVATE)
    val binding by viewBinding(FragmentCustomStudyNewBinding::bind)

    private val optionsIdsMapping =
        mapOf(
            EXTEND_NEW to R.id.option_new,
            EXTEND_REV to R.id.option_review,
            STUDY_FORGOT to R.id.option_forgotten_cards,
            STUDY_AHEAD to R.id.option_review_ahead,
            STUDY_PREVIEW to R.id.option_preview_new_cards,
            STUDY_TAGS to R.id.option_card_state_or_tags,
        )

    private val cardsStatesIdsMapping =
        mapOf(
            NewCardsOnly to R.id.card_state_new,
            DueCardsOnly to R.id.card_state_due,
            ReviewCardsRandom to R.id.card_state_review_random,
            AllCardsRandom to R.id.card_state_all_random,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(ON_SELECTED_TAGS_KEY, this) { _, bundle ->
            val tagsToInclude =
                bundle.getStringArrayList(ON_SELECTED_TAGS__SELECTED_TAGS) ?: emptyList<String>()
            viewModel.onTagsSelected(tagsToInclude)
            createCustomStudy()
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val constraints = insets.getInsets(systemBars() or displayCutout() or ime())
            view.updatePadding(
                left = constraints.left,
                top = constraints.top,
                bottom = constraints.bottom,
                right = constraints.right,
            )
            insets
        }
        binding.toolbar.setNavigationOnClickListener { requireActivity().finish() }
        binding.optionNew.text = EXTEND_NEW.labelProducer()
        binding.optionReview.text = EXTEND_REV.labelProducer()
        binding.optionForgottenCards.text = STUDY_FORGOT.labelProducer()
        binding.optionReviewAhead.text = STUDY_AHEAD.labelProducer()
        binding.optionPreviewNewCards.text = STUDY_PREVIEW.labelProducer()
        binding.optionCardStateOrTags.text = STUDY_TAGS.labelProducer()

        binding.cardStateNew.text = NewCardsOnly.labelProducer()
        binding.cardStateDue.text = DueCardsOnly.labelProducer()
        binding.cardStateReviewRandom.text = ReviewCardsRandom.labelProducer()
        binding.cardStateAllRandom.text = AllCardsRandom.labelProducer()

        binding.btnAction.setOnClickListener {
            val currentState = viewModel.state.value
            if (currentState.canSelectTags) {
                startTagSelection()
            } else {
                createCustomStudy()
            }
        }

        binding.optionsGroup.setOnCheckedStateChangeListener { _, ids ->
            val selectedId = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val selectedMapping =
                optionsIdsMapping.entries.firstOrNull { it.value == selectedId }
                    ?: return@setOnCheckedStateChangeListener
            viewModel.onOptionSelected(selectedMapping.key)
        }

        binding.cardsStatesGroup.setOnCheckedStateChangeListener { _, ids ->
            val selectedId = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val selectedMapping =
                cardsStatesIdsMapping.entries.firstOrNull { it.value == selectedId }
                    ?: return@setOnCheckedStateChangeListener
            viewModel.onCardStateSelected(selectedMapping.key)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect(::bindState)
        }
    }

    private fun bindState(state: CustomStudyState) {
        if (state.hasInitializationFailed) {
            activity?.let {
                showThemedToast(it, R.string.something_wrong, false)
            }
            activity?.finish()
            return
        }
        binding.loadingIndicator.isVisible = state.isInitializing
        binding.optionsGroup.isVisible = !state.isInitializing
        binding.optionsInputPanel.isVisible = !state.isInitializing
        binding.btnAction.isVisible = !state.isInitializing

        if (state is CustomStudyState.Data) {
            binding.btnAction.text =
                if (state.option == STUDY_TAGS && state.canSelectTags) {
                    TR.sentenceCase.chooseTags
                } else {
                    TR.studyingFinish()
                }

            binding.optionsGroup.check(optionsIdsMapping[state.option] ?: R.id.option_new)
            val detailsLabel = getDetails1LabelText(state)
            binding.detailsLabel.text = detailsLabel
            binding.detailsLabel.isVisible = detailsLabel.isNotEmpty()
            val detailsLabelExtra = getDetails2LabelText(state)
            binding.detailsLabelExtra.text = detailsLabelExtra
            binding.detailsLabelExtra.isVisible = detailsLabelExtra.isNotEmpty()
            binding.input.setText(getDefaultInputValue(state))
            binding.input.selectAll()
            binding.input.requestFocus()
            binding.cardsStatesGroup.isVisible = state.option == STUDY_TAGS
            binding.cardsStatesGroup.check(
                cardsStatesIdsMapping[state.cardState] ?: R.id.card_state_new,
            )
        }
    }

    private fun createCustomStudy() {
        val currentState = viewModel.state.value as? CustomStudyState.Data ?: return
        val userInput = binding.input.textAsIntOrNull() ?: return
        Timber.i("Custom study: ${currentState.option} option; input = $userInput")

        val request =
            customStudyRequest {
                deckId = viewModel.deckId
                when (currentState.option) {
                    EXTEND_NEW -> newLimitDelta = userInput
                    EXTEND_REV -> reviewLimitDelta = userInput
                    STUDY_FORGOT -> forgotDays = userInput
                    STUDY_AHEAD -> reviewAheadDays = userInput
                    STUDY_PREVIEW -> previewDays = userInput
                    STUDY_TAGS -> {
                        // https://github.com/ankitects/anki/blob/acaeee91fa853e4a7a78dcddbb832d009ec3529a/qt/aqt/customstudy.py#L169-L177
                        cram =
                            cram.copy {
                                kind = currentState.cardState.kind
                                cardLimit = userInput
                                tagsToInclude.addAll(currentState.tagsToInclude)
                                tagsToExclude.addAll(currentState.tagsToExclude)
                            }
                    }
                }
            }
        launchCatchingTask {
            withProgress {
                undoableOp { sched.customStudy(request) }
            }

            // save the default values (not in upstream)
            when (currentState.option) {
                STUDY_FORGOT -> sharedPrefs().edit { putInt("forgottenDays", userInput) }
                STUDY_AHEAD -> sharedPrefs().edit { putInt("aheadDays", userInput) }
                STUDY_PREVIEW -> sharedPrefs().edit { putInt("previewDays", userInput) }
                STUDY_TAGS -> sharedPrefs().edit { putInt("amountOfCards", userInput) }
                EXTEND_NEW, EXTEND_REV -> {
                    // Nothing to do in ankidroid. The default value is provided by the backend.
                }
            }
            activity?.finish()
        }
    }

    private fun startTagSelection() {
        launchCatchingTask {
            val nids =
                withProgress {
                    withCol {
                        val currentDeckname = decks.name(viewModel.deckId)
                        val search = SearchNode.newBuilder().setDeck(currentDeckname).build()
                        val query = buildSearchString(listOf(search))
                        findNotes(query)
                    }
                }
            if (!parentFragmentManager.isStateSaved) {
                TagsDialog()
                    .withArguments(
                        requireContext(),
                        TagsDialog.DialogType.CUSTOM_STUDY,
                        nids,
                    ).show(parentFragmentManager, "TagsDialog")
            }
        }
    }

    private fun getDetails1LabelText(state: CustomStudyState.Data): String =
        when (state.option) {
            EXTEND_NEW -> state.defaults.labelForNewQueueAvailable()
            EXTEND_REV -> state.defaults.labelForReviewQueueAvailable()
            STUDY_FORGOT,
            STUDY_AHEAD,
            STUDY_PREVIEW,
            STUDY_TAGS,
            -> ""
        }

    /** Line 2 of the number entry dialog */
    private fun getDetails2LabelText(state: CustomStudyState.Data): String =
        when (state.option) {
            EXTEND_NEW -> getString(R.string.custom_study_new_extend)
            EXTEND_REV -> getString(R.string.custom_study_rev_extend)
            STUDY_FORGOT -> getString(R.string.custom_study_forgotten)
            STUDY_AHEAD -> TODO()
            STUDY_PREVIEW -> getString(R.string.custom_study_preview)
            STUDY_TAGS -> getString(R.string.custom_study_tags)
        }

    /**
     * Initial value of the number entry dialog.
     *
     * Requires [CustomStudyOption.checkAvailability] to return true.
     */
    private fun getDefaultInputValue(state: CustomStudyState.Data): String {
        val prefs = requireActivity().sharedPrefs()
        return when (state.option) {
            EXTEND_NEW ->
                state.defaults.extendNew.initialValue
                    .toString()

            EXTEND_REV ->
                state.defaults.extendReview.initialValue
                    .toString()

            STUDY_FORGOT -> prefs.getInt("forgottenDays", 1).toString()
            STUDY_AHEAD -> prefs.getInt("aheadDays", 1).toString()
            STUDY_PREVIEW -> prefs.getInt("previewDays", 1).toString()
            // currently(as of Anki 25.02) not upstream
            STUDY_TAGS -> prefs.getInt("amountOfCards", 100).toString()
        }
    }

    companion object {
        fun getIntent(
            context: Context,
            did: DeckId,
        ): Intent =
            ConfigAwareSingleFragmentActivity.getIntent(
                context = context,
                fragmentClass = CustomStudyFragment::class,
                arguments =
                    Bundle().apply {
                        putLong(KEY_DID, did)
                    },
            )
    }
}
