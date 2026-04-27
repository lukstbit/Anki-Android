/*
 * Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs.decks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.decks.CreateDeckDialogFragment.Companion.ARG_DECK_ID
import com.ichi2.anki.dialogs.decks.CreateDeckDialogFragment.Companion.ARG_TYPE
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.libanki.Decks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class CreateDeckViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val state: StateFlow<CreateDeckState>
        field = MutableStateFlow(CreateDeckState())

    /** The full deck name after the user input */
    val fullDeckName: String
        get() =
            if (type == CreateDeckType.Subdeck) {
                "$parentName${Decks.DECK_SEPARATOR}${state.value.input}"
            } else {
                state.value.input
            }
    private val type: CreateDeckType
        get() = requireNotNull(savedStateHandle.get<CreateDeckType>(ARG_TYPE))
    private val deckId: DeckId?
        get() = savedStateHandle.get<DeckId>(ARG_DECK_ID)
    private var backendDecks: List<DeckNameId> = emptyList()

    /** Current deck name, set only when type is [CreateDeckType.Rename] */
    private var currentName: String? = null

    /** The full parent deck name, set only when type is [CreateDeckType.Subdeck] */
    private var parentName: String? = null

    init {
        viewModelScope.launch {
            state.update { it.copy(isInitializing = true) }
            try {
                backendDecks =
                    withCol {
                        decks.allNamesAndIds()
                    }
                if (type == CreateDeckType.Subdeck) {
                    // in this case deckId represents the id of the parent
                    parentName = withCol { decks.name(requireNotNull(deckId)) }
                }
                if (type == CreateDeckType.Rename) {
                    // in this case deckId represents the id of the deck being renamed
                    currentName = withCol { decks.name(requireNotNull(deckId)) }
                }
                state.update {
                    it.copy(
                        isInitializing = false,
                        shouldFocus = true,
                        input = currentName ?: "",
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                state.update { it.copy(fatalError = ex) }
            }
        }
    }

    fun onInputChanged(text: String) {
        if (text == state.value.input) return
        Timber.d("CreateDeckViewModel::Input changed")
        val showDoubleDigitsHelp = text.containsNumberLargerThanNine()
        val inputError = verifyInput(text)
        state.update {
            it.copy(
                isInitializing = false,
                input = text,
                inputError = inputError,
                showDoubleDigitsHelp = showDoubleDigitsHelp,
            )
        }
    }

    /**
     * Verifies the user input for potential errors.
     * @return null if there are no errors, otherwise returns a [CreateDeckInputError] describing
     * the error
     */
    private fun verifyInput(userInput: String): CreateDeckInputError? {
        if (userInput.isBlank()) {
            return CreateDeckInputError.Empty
        }
        val actualName =
            if (type == CreateDeckType.Subdeck) {
                "$parentName${Decks.DECK_SEPARATOR}$userInput"
            } else {
                userInput
            }
        if (isNameAlreadyUsed(actualName)) {
            return CreateDeckInputError.AlreadyExists
        }
        return null
    }

    fun clearFocusRequest() {
        state.update { it.copy(shouldFocus = false) }
    }

    private fun isNameAlreadyUsed(name: String): Boolean = backendDecks.map { it.name }.any { it == name }

    private fun CharSequence.containsNumberLargerThanNine(): Boolean = Regex("""(?:[^:]|^)[1-9]\d+(?:[^:]|$)""").find(this) != null
}
