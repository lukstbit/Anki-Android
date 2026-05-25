// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget.cardanalysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.isCollectionEmpty
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.widget.AppWidgetId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CardAnalysisWidgetConfigViewModel(
    private val appWidgetId: AppWidgetId,
    private val localStorage: CardAnalysisWidgetPreferences,
) : ViewModel() {
    val state: StateFlow<CardAnalysisWidgetConfigState>
        field = MutableStateFlow(CardAnalysisWidgetConfigState())
    private var allDecks: List<DeckNameId> = emptyList()
    private var initialDeck: SelectableDeck.Deck? = null

    init {
        viewModelScope.launch {
            try {
                if (isCollectionEmpty()) {
                    state.update { CardAnalysisWidgetConfigState(isCollectionEmpty = true) }
                    return@launch
                }
                allDecks = withCol { decks.allNamesAndIds(skipEmptyDefault = false) }
                val savedDeckId = localStorage.get(appWidgetId)
                initialDeck = allDecks.firstOrNull { it.id == savedDeckId }?.asSelectableDeck
                if (savedDeckId != null && initialDeck == null) {
                    // a deck was stored but it wasn't found in the full list of decks
                    localStorage.delete(appWidgetId)
                }
                state.update { CardAnalysisWidgetConfigState(deck = initialDeck) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                state.update { CardAnalysisWidgetConfigState(initializationError = ex) }
            }
        }
    }

    fun select(deck: SelectableDeck.Deck) {
        localStorage.save(appWidgetId, deck.deckId)
        state.update { it.copy(shouldFinish = initialDeck == null) }
    }

    private val DeckNameId.asSelectableDeck: SelectableDeck.Deck
        get() = SelectableDeck.Deck(this.id, this.name)
}

data class CardAnalysisWidgetConfigState(
    val isCollectionEmpty: Boolean = false,
    val initializationError: Exception? = null,
    val deck: SelectableDeck.Deck? = null,
    val shouldFinish: Boolean = false,
)
