// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.widget.cardanalysis

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks.Companion.NOT_FOUND_DECK_ID
import com.ichi2.widget.AppWidgetId

/** Provides local storage handling for data from [CardAnalysisWidgetConfig]. */
class CardAnalysisWidgetPreferences private constructor(
    private val preferences: SharedPreferences,
) {
    fun delete(appWidgetId: AppWidgetId) {
        preferences.edit { remove(getPrefKeyFor(appWidgetId)) }
    }

    fun get(appWidgetId: AppWidgetId): DeckId? =
        preferences
            .getLong(getPrefKeyFor(appWidgetId), NOT_FOUND_DECK_ID)
            .takeIf { it != NOT_FOUND_DECK_ID }

    fun save(
        appWidgetId: AppWidgetId,
        deckId: DeckId,
    ) {
        preferences.edit { putLong(getPrefKeyFor(appWidgetId), deckId) }
    }

    /** Returns the preferences key for data associated with [appWidgetId]. */
    private fun getPrefKeyFor(appWidgetId: AppWidgetId): String = "card_analysis_extra_widget_selected_deck_$appWidgetId"

    companion object {
        /** Name of the preferences file where data will be stored */
        const val PREFS_CARD_ANALYSIS_WIDGET_CONFIG = "prefs_card_analysis_widget_config"

        fun newInstance(context: Context): CardAnalysisWidgetPreferences {
            val preferences =
                context.getSharedPreferences(
                    PREFS_CARD_ANALYSIS_WIDGET_CONFIG,
                    Context.MODE_PRIVATE,
                )
            return CardAnalysisWidgetPreferences(preferences)
        }
    }
}
