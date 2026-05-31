// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit

package com.ichi2.widget.deckpicker

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.utils.ext.unregisterReceiverSilently
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.widget.AppWidgetId.Companion.INVALID_APPWIDGET_ID
import com.ichi2.widget.AppWidgetId.Companion.getAppWidgetId
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Configuration activity for [DeckPickerWidgetConfig].
 * @see DeckPickerWidgetConfig
 */
class DeckPickerWidgetConfig : AnkiActivity() {
    private lateinit var viewModel: DecksWidgetConfigViewModel
    private var appWidgetId = INVALID_APPWIDGET_ID
    private lateinit var deckPickerWidgetPreferences: DeckPickerWidgetPreferences
    private val onBackPressedCallback: OnBackPressedCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                DiscardChangesDialog.showDialog(
                    context = this@DeckPickerWidgetConfig,
                    positiveMethod = { finish() },
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)

        if (!ensureStoragePermissions()) {
            return
        }
        setResult(RESULT_CANCELED)
        deckPickerWidgetPreferences = DeckPickerWidgetPreferences(this)
        appWidgetId = intent.getAppWidgetId()
        if (appWidgetId == INVALID_APPWIDGET_ID) {
            Timber.v("Invalid App Widget ID")
            finish()
            return
        }
        registerDeckSelectedHandler(action = ::onDeckSelected)
        registerReceiver(
            widgetRemovedReceiver,
            IntentFilter(AppWidgetManager.ACTION_APPWIDGET_DELETED),
        )
        viewModel =
            ViewModelProvider.create(
                this,
                DecksWidgetConfigViewModel.factory(appWidgetId, this),
            )[DecksWidgetConfigViewModel::class.java]
        listenForBackCallbackUpdates()
        listenForModelUpdates()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        setContent {
            AnkiDroidTheme {
                DecksWidgetConfigScreen(
                    onNavigationExit = { onBackPressedDispatcher.onBackPressed() },
                    onDeckSelection = {
                        startDeckSelection(
                            title = getString(R.string.select_decks_title),
                            allowAll = false,
                            skipEmptyDefault = true,
                            allowMultipleSelection = true,
                        )
                    },
                    onFinishDeckSelection = {
                        val fragment =
                            supportFragmentManager.findFragmentByTag(DeckSelectionDialog.TAG)
                        (fragment as? DeckSelectionDialog)?.dismissNow()
                    },
                )
            }
        }
    }

    private fun listenForBackCallbackUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.backCallbackEnabledState.collect { isEnabled ->
                    onBackPressedCallback.isEnabled = isEnabled
                }
            }
        }
    }

    private fun listenForModelUpdates() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
//                    onBackPressedCallback.isEnabled = state.wasChanged
//                    val currentDecksIds = state.decks.map { it.id }
//                    if (state.hasSaved) {
//                        val appWidgetManager =
//                            AppWidgetManager.getInstance(this@DeckPickerWidgetConfig)
//                        DeckPickerWidget.updateWidget(
//                            this@DeckPickerWidgetConfig,
//                            appWidgetManager,
//                            appWidgetId,
//                            currentDecksIds.toLongArray(),
//                        )
//
//                        val resultValue = Intent().updateWidget(appWidgetId)
//                        if (state.decks.isEmpty()) {
//                            setResult(RESULT_CANCELED)
//                        } else {
//                            setResult(RESULT_OK, resultValue)
//                        }
//                        finish()
//                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiverSilently(widgetRemovedReceiver)
    }

    /** Called when a deck is selected from the deck selection dialog. */
    fun onDeckSelected(deck: SelectableDeck?) {
        if (deck == null) return
        require(deck is SelectableDeck.Deck)
        val currentState = viewModel.state.value

        // TODO fix this snackbar

        val isDeckAlreadySelected = currentState.decks.any { it.id == deck.deckId }
        if (isDeckAlreadySelected) {
            showSnackbar(getString(R.string.deck_already_selected_message))
            return
        }
        viewModel.addDeck(deck.deckId, deck.name)
    }

    /** ItemTouchHelper callback for handling drag and drop of decks. */
    private val itemTouchHelperCallback =
        object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun getDragDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int {
//                val selectedDeckCount = deckAdapter.itemCount
//                return if (selectedDeckCount > 1) {
//                    super.getDragDirs(recyclerView, viewHolder)
//                } else {
//                    0 // Disable drag if there's only one item
//                }
                TODO()
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
//                val fromPosition = viewHolder.bindingAdapterPosition
//                val toPosition = target.bindingAdapterPosition
//                deckAdapter.moveDeck(fromPosition, toPosition)
//                hasUnsavedChanges = true
//                setUnsavedChanges(true)
                return true
            }

            override fun onSwiped(
                viewHolder: RecyclerView.ViewHolder,
                direction: Int,
            ) {
                // No swipe action
            }
        }

    /** BroadcastReceiver to handle widget removal. */
    private val widgetRemovedReceiver =
        object : AnkiBroadcastReceiver() {
            override fun onReceiveBroadcast(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action != AppWidgetManager.ACTION_APPWIDGET_DELETED) {
                    return
                }

                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId == INVALID_APPWIDGET_ID) {
                    return
                }

                deckPickerWidgetPreferences.deleteDeckData(appWidgetId)
            }
        }
}
