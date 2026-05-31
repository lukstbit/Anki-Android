// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.widget.deckpicker

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import anki.config.preferences
import com.ichi2.anki.libanki.DeckId
import com.ichi2.widget.AppWidgetId

/**
 * Class responsible for storing user data for [DeckPickerWidget].
 * @param preferences the backing preferences data source
 */
class DeckPickerWidgetConfigLocalStorage private constructor(
    val preferences: SharedPreferences,
) {
    /**
     * Fetches the stored [DeckId]s for the given [AppWidgetId].
     * Note: There's no guarantee that the returned [DeckId]s still represent decks that exist at
     * the time of execution.
     */
    fun getAll(appWidgetId: AppWidgetId): List<DeckId> {
        val data = preferences.getString(createPreferenceKeyFor(appWidgetId), null)
        if (data?.isEmpty() == true) return emptyList()
        return data?.split(ENTRIES_SEPARATOR)?.map { it.toLong() } ?: emptyList()
    }

    /**
     * Stores [DeckId]s for the given [AppWidgetId].
     */
    fun saveAll(
        appWidgetId: AppWidgetId,
        dids: List<DeckId>,
    ) {
        val stringIds = dids.joinToString(ENTRIES_SEPARATOR) { it.toString() }
        preferences.edit(commit = true) {
            putString(createPreferenceKeyFor(appWidgetId), stringIds)
        }
    }

    /**
     * Creates a preference key which will be used just for the data associated with the
     * [DeckPickerWidget] identified by [appWidgetId].
     */
    fun createPreferenceKeyFor(appWidgetId: AppWidgetId): String = "deck_picker_widget_selected_decks_$appWidgetId"

    companion object {
        /** Identifier for the preferences file used with [DeckPickerWidget] */
        private const val PREFS_DECK_PICKER_WIDGET = "DeckPickerWidgetPrefs"
        private const val ENTRIES_SEPARATOR = ","

        fun newInstance(context: Context): DeckPickerWidgetConfigLocalStorage {
            val preferences =
                context.getSharedPreferences(
                    PREFS_DECK_PICKER_WIDGET,
                    Context.MODE_PRIVATE,
                )
            return DeckPickerWidgetConfigLocalStorage(preferences)
        }
    }
}
