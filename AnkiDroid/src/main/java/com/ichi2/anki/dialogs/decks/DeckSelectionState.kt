// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.decks

import com.ichi2.anki.common.ALL_DECKS_ID
import com.ichi2.anki.libanki.DeckId

sealed interface DeckSelectionState {
    data class Initializing(
        val error: Exception? = null,
    ) : DeckSelectionState

    data class Data(
        val query: String,
        val decks: List<DeckSelectionItemState>,
    ) : DeckSelectionState
}

data class DeckSelectionItemState(
    val deckId: DeckId,
    val name: String,
    val isFiltered: Boolean,
    val hasChildren: Boolean,
    val isCollapsed: Boolean,
    val depth: Int,
)

data class DeckTreeNodeModel(
    val deckId: DeckId,
    val name: String,
    val filtered: Boolean,
    var collapsed: Boolean,
    val depth: Int,
    val parentDeckId: DeckId? = null,
    val children: List<DeckTreeNodeModel>,
)

val DeckSelectionItemState.isAllDecks: Boolean
    get() = deckId == ALL_DECKS_ID
