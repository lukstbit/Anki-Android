// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.decks.DeckTreeNode
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.DeckId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeckSelectionViewModel : ViewModel() {
    val state: StateFlow<DeckSelectionState>
        field = MutableStateFlow<DeckSelectionState>(DeckSelectionState.Initializing())

    /** Details about all decks that could be shown in the ui */
    private lateinit var allDecks: DeckTreeNode
    private lateinit var allDecksFlattened: List<DeckTreeNode>

    init {
        viewModelScope.launch {
            try {
                allDecks = withCol { backend.deckTree(0) }
                allDecksFlattened = allDecks.flatten()
                state.update {
                    DeckSelectionState.Data(
                        query = "",
                        decks = allDecks.flattenIntoUiModel(),
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                state.update { DeckSelectionState.Initializing(error = ex) }
            }
        }
    }

    fun filter(query: String) {
//        val decksMapping =
//            allDecks.map { model ->
//                DeckSelectionItemState(
//                    deckId = model.deckId,
//                    name = model.name,
//                    isFiltered = model.filtered,
//                    hasChildren = false, // ignored, all decks use the same level when filtering
//                    isCollapsed = false, // ignored, expand/collapse is disabled when filtering
//                    depth = 1, // when filtering all items are at the same level
//                )
//            }
//        val filteredResults =
//            if (query.isBlank()) {
//                decksMapping
//            } else {
//                decksMapping.filter { it.name.contains(query, ignoreCase = true) }
//            }
//        state.update { DeckSelectionState.Data(query, filteredResults) }
    }

    fun onToggleCollapse(deckId: DeckId) {
//        state.update {
//            DeckSelectionState.Data(
//                query = "",
//                decks = allDecks.map(::asUiModel),
//            )
//        }
    }

    private fun DeckTreeNode.flattenIntoUiModel(): List<DeckSelectionItemState> {
        val entries = mutableListOf<DeckSelectionItemState>()
        if (this.deckId != 0L) {
            entries.add(asUiModel(this))
        }
        if (childrenList.isNotEmpty() && !collapsed) {
            entries.addAll(childrenList.flatMap { it.flattenIntoUiModel() })
        }
        return entries
    }

    private fun asUiModel(backend: DeckTreeNode): DeckSelectionItemState =
        DeckSelectionItemState(
            deckId = backend.deckId,
            name = backend.name,
            isFiltered = backend.filtered,
            hasChildren = backend.childrenList.isNotEmpty(),
            isCollapsed = backend.collapsed,
            depth = backend.level - 1,
        )

    /** We ignore the first element */
    private fun DeckTreeNode.flatten(): List<DeckTreeNode> {
        val entries = mutableListOf<DeckTreeNode>()
        if (this.deckId != 0L) {
            entries.add(this)
        }
        val childrenEntries = childrenList.flatMap { it.flatten() }
        if (childrenEntries.isNotEmpty()) {
            entries.addAll(childrenEntries)
        }
        return entries
    }
}
