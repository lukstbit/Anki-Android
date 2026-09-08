// SPDX-FileCopyrightText: 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.customstudy

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.customstudy.CustomStudyCardState.NewCardsOnly
import com.ichi2.anki.dialogs.customstudy.CustomStudyDefaults.Companion.toDomainModel
import com.ichi2.anki.dialogs.customstudy.CustomStudyOption.EXTEND_NEW
import com.ichi2.anki.dialogs.customstudy.CustomStudyOption.EXTEND_REV
import com.ichi2.anki.dialogs.customstudy.CustomStudyOption.STUDY_FORGOT
import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * [ViewModel] handling the state for [CustomStudyFragment].
 * @see CustomStudyFragment
 */
class CustomStudyViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val state: StateFlow<CustomStudyState>
        field = MutableStateFlow<CustomStudyState>(CustomStudyState.Initializing())

    /** Required [DeckId] of the [Deck] for which the custom study session is being built. */
    val deckId: DeckId
        get() = requireNotNull(savedStateHandle.get<DeckId>(KEY_DID))

    init {
        viewModelScope.launch {
            try {
                val (defaults, hasTags) =
                    withCol {
                        decks.select(deckId)
                        Pair(
                            sched.customStudyDefaults(deckId).toDomainModel(),
                            tags.all().isNotEmpty(),
                        )
                    }
                val initialOption =
                    if (EXTEND_NEW.checkAvailability.invoke(defaults)) {
                        EXTEND_NEW
                    } else if (EXTEND_REV.checkAvailability.invoke(defaults)) {
                        EXTEND_REV
                    } else {
                        STUDY_FORGOT
                    }
                state.update {
                    CustomStudyState.Data(
                        defaults = defaults,
                        option = initialOption,
                        cardState = NewCardsOnly,
                        hasTags = hasTags,
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                state.update { CustomStudyState.Initializing(error = ex) }
            }
        }
    }

    fun onOptionSelected(option: CustomStudyOption) {
        updateDataState { currentState -> currentState.copy(option = option) }
    }

    fun onCardStateSelected(cardState: CustomStudyCardState) {
        updateDataState { currentState -> currentState.copy(cardState = cardState) }
    }

    fun onTagsSelected(tags: List<String>) {
        updateDataState { currentState -> currentState.copy(tagsToInclude = tags) }
    }

    private fun updateDataState(update: (CustomStudyState.Data) -> CustomStudyState.Data) {
        state.update { currentState ->
            when (currentState) {
                is CustomStudyState.Initializing -> {
                    Timber.w("Requesting data state update when current state is Initializing")
                    currentState
                }

                is CustomStudyState.Data -> update(currentState)
            }
        }
    }

    /*
     * Translates the user's selection into a specific study type.
     * This prevents the app from "forgetting" user's choice (e.g., Due Cards)
     * even if the tag selection screen is skipped.
     */
    val selectedKind: CramKind
        get() = TODO()
//            if (selectedCardStateIndex != AdapterView.INVALID_POSITION) {
//                CustomStudyCardState.entries[selectedCardStateIndex].kind
//            } else {
//                CramKind.CRAM_KIND_NEW
//            }

    companion object {
        /**
         * Required key for a [DeckId] which [CustomStudyFragment] expects to receive as an argument.
         */
        const val KEY_DID = "key_did"
    }
}
