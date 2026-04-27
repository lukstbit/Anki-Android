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

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Represents the state data rendered by [CreateDeckDialogFragment] */
data class CreateDeckState(
    val isInitializing: Boolean = true,
    val input: String = "",
    val inputError: CreateDeckInputError? = null,
    /**
     * One time flag used to signal that EditText input setup can be initiated. Needed as we start
     * with the input disabled and as the data is loaded it needs to be focused(shows keyboard) and
     * to also place the cursor accounting for the initial name being present when renaming.
     */
    val shouldFocus: Boolean = false,
    val showDoubleDigitsHelp: Boolean = false,
    /** Unrecoverable error(during initialization) after which the dialog should dismiss itself */
    val fatalError: Throwable? = null,
)

enum class CreateDeckInputError {
    Empty,
    AlreadyExists,
}

/** Note: creating filtered decks is handled in a different screen. */
@Parcelize
enum class CreateDeckType : Parcelable {
    Deck,
    Subdeck,
    Rename,
}

val CreateDeckState.isInputValid: Boolean
    get() = !isInitializing && input.isNotEmpty() && inputError == null
