// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit

package com.ichi2.widget.deckpicker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.isCollectionEmpty
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.widget.AppWidgetId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class DecksWidgetConfigViewModel(
    private val appWidgetId: AppWidgetId,
    private val localStorage: DeckPickerWidgetConfigLocalStorage,
) : ViewModel() {
    val state: StateFlow<DecksWidgetConfigState>
        field = MutableStateFlow<DecksWidgetConfigState>(DecksWidgetConfigState())
    val backCallbackEnabledState: StateFlow<Boolean>
        field = MutableStateFlow(false)
    private var initialStoredDecks: List<DeckId> = emptyList()
    private var allDecks: List<DeckNameId> = emptyList()

    init {
        viewModelScope.launch {
            try {
                if (isCollectionEmpty()) {
                    updateState { copy(isCollectionEmpty = true) }
                    return@launch
                }
                initialStoredDecks = localStorage.getAll(appWidgetId)
                allDecks = withCol { decks.allNamesAndIds() }
                val allDecksIds = allDecks.map { it.id }
                val (available, _) = initialStoredDecks.partition { it in allDecksIds }
                updateState {
                    copy(
                        isInitializing = false,
                        decks = allDecks.filter { it.id in available },
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.d(ex, "Failed to initialize DeckPickerWidgetConfig")
                updateState { copy(initializationError = ex) }
            }
        }
    }

    fun addDeck(
        deckId: DeckId,
        name: String,
    ) {
        val newDeckRef = DeckNameId(name, deckId)
        allDecks = allDecks + newDeckRef
        updateState {
            val updatedDecks = decks.toMutableList().apply { add(newDeckRef) }
            copy(
                decks = updatedDecks,
                wasChanged = initialStoredDecks != updatedDecks.map { it.id },
            )
        }
    }

    fun removeDeck(entry: DeckNameId) {
        updateState {
            val updatedDecks = decks.toMutableList().apply { remove(entry) }
            copy(
                decks = updatedDecks,
                wasChanged = initialStoredDecks != updatedDecks.map { it.id },
            )
        }
    }

    fun save() {
        val currentDecks = state.value.decks
        localStorage.saveAll(appWidgetId, currentDecks.map { it.id })
        updateState { copy(hasSaved = true) }
    }

    private fun updateState(update: DecksWidgetConfigState.() -> DecksWidgetConfigState) {
        state.update { currentState -> currentState.update() }
    }

    companion object {
        fun factory(
            appWidgetId: AppWidgetId,
            context: Context,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                val localStorage = DeckPickerWidgetConfigLocalStorage.newInstance(context)
                initializer {
                    DecksWidgetConfigViewModel(appWidgetId, localStorage)
                }
            }
    }
}

data class DecksWidgetConfigState(
    val isInitializing: Boolean = true,
    val initializationError: Exception? = null,
    val isCollectionEmpty: Boolean = false,
    val decks: List<DeckNameId> = emptyList(),
    val wasChanged: Boolean = false,
    val hasSaved: Boolean = false,
)

/** We allow a maximum of 5 decks to be selected. */
const val MAX_DECKS_ALLOWED = 5

val DecksWidgetConfigState.hasReachedMaxDecksCount: Boolean
    get() = decks.size == MAX_DECKS_ALLOWED
