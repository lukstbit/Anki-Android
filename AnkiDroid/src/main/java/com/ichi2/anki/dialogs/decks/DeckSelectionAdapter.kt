// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.decks

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.getDrawableOrThrow
import androidx.core.content.res.use
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.ichi2.anki.OnContextAndLongClickListener.Companion.setOnContextAndLongClickListener
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ItemDeckPickerDialogBinding
import com.ichi2.anki.libanki.DeckId

class DeckSelectionAdapter(
    context: Context,
    val onDeckSelected: (DeckId, String) -> Unit,
    val onDeckContextSelected: (DeckId) -> Unit,
    val onDeckToggleCollapse: (DeckId) -> Unit,
) : ListAdapter<DeckSelectionItemState, DeckSelectionAdapter.DeckSelectionViewHolder>(
        selectableDeckUiDiff,
    ) {
    private val layoutInflater = LayoutInflater.from(context)
    private val indentSize = context.resources.getDimensionPixelSize(R.dimen.keyline_1)
    private lateinit var expandImage: Drawable
    private lateinit var collapseImage: Drawable

    val attrs =
        intArrayOf(
            R.attr.expandRef,
            R.attr.collapseRef,
        )

    init {
        context.obtainStyledAttributes(attrs).use { typedArray ->
            expandImage = typedArray.getDrawableOrThrow(0)
            expandImage.isAutoMirrored = true
            collapseImage = typedArray.getDrawableOrThrow(1)
            collapseImage.isAutoMirrored = true
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): DeckSelectionViewHolder =
        DeckSelectionViewHolder(
            ItemDeckPickerDialogBinding.inflate(layoutInflater, parent, false),
        )

    override fun onBindViewHolder(
        holder: DeckSelectionViewHolder,
        position: Int,
    ) {
        val deck = getItem(position)
        holder.binding.deckTextView.text = deck.name
        updateSupportingViews(holder.binding, deck)
    }

    /** Handles the expand/collapse and indentation views */
    private fun updateSupportingViews(
        binding: ItemDeckPickerDialogBinding,
        deck: DeckSelectionItemState,
    ) {
        if (deck.hasChildren) {
            binding.expander.apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                setImageDrawable(if (deck.isCollapsed) expandImage else collapseImage)
                contentDescription =
                    context.getString(if (deck.isCollapsed) R.string.expand else R.string.collapse)
                visibility = View.VISIBLE
            }
        } else {
            binding.expander.apply {
                visibility = View.INVISIBLE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }
        binding.indent.minimumWidth = deck.depth * indentSize
    }

    inner class DeckSelectionViewHolder(
        val binding: ItemDeckPickerDialogBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val targetDeck: DeckSelectionItemState?
            get() =
                if (bindingAdapterPosition == NO_POSITION) {
                    return null
                } else {
                    return (bindingAdapter as DeckSelectionAdapter).getItem(bindingAdapterPosition)
                }

        init {
            binding.root.setOnClickListener {
                targetDeck?.let { deck -> onDeckSelected(deck.deckId, deck.name) }
            }
            binding.root.setOnContextAndLongClickListener {
                targetDeck?.let {
                    if (!it.isAllDecks && !it.isFiltered) {
                        onDeckContextSelected(it.deckId)
                    }
                    return@setOnContextAndLongClickListener true
                }
                false
            }
            binding.expander.setOnClickListener {
                targetDeck?.let { onDeckToggleCollapse(it.deckId) }
            }
        }
    }
}

private val selectableDeckUiDiff =
    object : DiffUtil.ItemCallback<DeckSelectionItemState>() {
        override fun areItemsTheSame(
            old: DeckSelectionItemState,
            new: DeckSelectionItemState,
        ): Boolean = old.deckId == new.deckId

        override fun areContentsTheSame(
            old: DeckSelectionItemState,
            new: DeckSelectionItemState,
        ): Boolean = old == new
    }
