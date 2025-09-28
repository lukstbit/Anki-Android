/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
 *  Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.widget.cardanalysis

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.Group
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.isCollectionEmpty
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.withProgress
import com.ichi2.widget.AppWidgetId.Companion.INVALID_APPWIDGET_ID
import com.ichi2.widget.AppWidgetId.Companion.getAppWidgetId
import com.ichi2.widget.cardanalysis.CardAnalysisWidget.Companion.EXTRA_SELECTED_DECK_ID
import timber.log.Timber

/**
 * Configuration activity for [CardAnalysisWidget]. Only allows selecting a deck.
 * Behavior:
 *  - shows a single centered card(with button for change) with the selected deck name(if any) and
 *    a button to trigger the deck selection dialog
 *  - when the user first adds the widget this activity will start with the deck selection dialog
 *    opened, if there is a deck selected then the activity will start without the selection dialog
 *  - storing the user selection is done when the user clicks 'Done'
 *  - handles user not selecting anything
 *  - finishes immediately when the collection is empty and shows a toast('Collection is empty')
 *  - shows loading states when querying the collection
 *
 * @see CardAnalysisWidget
 * @see CardAnalysisWidgetPreferences
 */
class CardAnalysisWidgetConfig :
    AnkiActivity(),
    DeckSelectionListener,
    BaseSnackbarBuilderProvider {
    private var appWidgetId = INVALID_APPWIDGET_ID
    private lateinit var cardAnalysisWidgetPreferences: CardAnalysisWidgetPreferences
    private lateinit var deckCardName: TextView
    private var deck: SelectableDeck.Deck? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }

        super.onCreate(savedInstanceState)

        if (!ensureStoragePermissions()) {
            return
        }

        setContentView(R.layout.activity_card_analysis_widget_config)

        cardAnalysisWidgetPreferences = CardAnalysisWidgetPreferences(this)

        appWidgetId = intent.getAppWidgetId()

        if (appWidgetId == INVALID_APPWIDGET_ID) {
            Timber.v("Invalid App Widget ID")
            finish()
            return
        }
        findViewById<TextView>(R.id.loading_label).text = getString(R.string.dialog_processing)
        deckCardName = findViewById(R.id.deck_card_name)
        if (savedInstanceState != null) {
            deck =
                BundleCompat.getParcelable(
                    savedInstanceState,
                    KEY_DECK,
                    SelectableDeck.Deck::class.java,
                )
            deckCardName.text = deck?.name
            setContentVisibility(true)
        } else {
            loadContent()
        }
        findViewById<MaterialButton>(R.id.change_btn).setOnClickListener {
            launchCatchingTask { showDeckSelectionDialog() }
        }
        findViewById<MaterialButton>(R.id.done_btn).setOnClickListener {
            updateWidget()
        }
        registerReceiver(
            widgetRemovedReceiver,
            IntentFilter(AppWidgetManager.ACTION_APPWIDGET_DELETED),
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_DECK, deck)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiverSilently(widgetRemovedReceiver)
    }

    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = findViewById<FloatingActionButton>(R.id.fabWidgetDeckPicker)
    }

    override fun onDeckSelected(deck: SelectableDeck?) {
        setContentVisibility(true)
        if (deck is SelectableDeck.Deck?) {
            // if the this.deck is null then the widget was just added so set and return
            val alsoFinish = this.deck == null
            this.deck = deck
            deckCardName.text = deck?.name
            if (alsoFinish) {
                updateWidget()
            }
        } else {
            showThemedToast(this, R.string.something_wrong, false)
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun loadContent() {
        launchCatchingTask {
            setContentVisibility(false)
            if (isCollectionEmpty()) {
                Timber.w("CardAnalysisWidgetConfig: collection is empty")
                showThemedToast(
                    this@CardAnalysisWidgetConfig,
                    R.string.no_cards_placeholder_title,
                    false,
                )
                finish()
                return@launchCatchingTask
            }
            setContentVisibility(true)
            val selectedDeckId =
                cardAnalysisWidgetPreferences.getSelectedDeckIdFromPreferences(appWidgetId)
            if (selectedDeckId == null) {
                showDeckSelectionDialog()
            } else {
                withProgress {
                    val backendDeck = withCol { decks.getLegacy(selectedDeckId) }
                    deck =
                        backendDeck?.let { SelectableDeck.Deck(backendDeck.id, backendDeck.name) }
                    deckCardName.text = deck?.name ?: getString(R.string.select_deck)
                }
            }
        }
    }

    private fun setContentVisibility(isVisible: Boolean) {
        findViewById<Group>(R.id.loading_group_views).isVisible = !isVisible
        findViewById<CardView>(R.id.deck_card).isVisible = isVisible
        findViewById<MaterialButton>(R.id.done_btn).isVisible = isVisible
    }

    private suspend fun showDeckSelectionDialog() =
        withProgress {
            val decks = SelectableDeck.fromCollection(includeFiltered = true)
            val dialog =
                DeckSelectionDialog.newInstance(
                    title = getString(R.string.select_deck_title),
                    summaryMessage = null,
                    keepRestoreDefaultButton = false,
                    decks = decks,
                )
            if (!supportFragmentManager.isStateSaved) {
                dialog.show(supportFragmentManager, "DeckSelectionDialog")
            }
        }

    private fun updateWidget() {
        cardAnalysisWidgetPreferences.saveSelectedDeck(appWidgetId, deck?.deckId)
        val updateIntent =
            Intent(this, CardAnalysisWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId.id))
                putExtra(EXTRA_SELECTED_DECK_ID, deck?.deckId)
            }

        sendBroadcast(updateIntent)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        CardAnalysisWidget.updateWidget(this, appWidgetManager, appWidgetId)

        val intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId.id)
        setResult(RESULT_OK, intent)
        finish()
    }

    /** BroadcastReceiver to handle widget removal. */
    private val widgetRemovedReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action != AppWidgetManager.ACTION_APPWIDGET_DELETED) {
                    return
                }

                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId == INVALID_APPWIDGET_ID) {
                    return
                }

                cardAnalysisWidgetPreferences.deleteDeckData(appWidgetId)
            }
        }

    companion object {
        private const val KEY_DECK = "key_deck"
    }
}

fun ContextWrapper.unregisterReceiverSilently(receiver: BroadcastReceiver) {
    try {
        unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
        Timber.d(e, "unregisterReceiverSilently")
    }
}
