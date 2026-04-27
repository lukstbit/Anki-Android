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

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentCreateDeckBinding
import com.ichi2.anki.dialogs.decks.CreateDeckDialogFragment.Companion.ARG_DECK_ID
import com.ichi2.anki.dialogs.decks.CreateDeckDialogFragment.Companion.ARG_NAME
import com.ichi2.anki.dialogs.decks.CreateDeckDialogFragment.Companion.ARG_TYPE
import com.ichi2.anki.dialogs.decks.CreateDeckDialogFragment.Companion.TAG
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.getLongOrNull
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.utils.AndroidUiUtils
import com.ichi2.utils.cancelable
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.launch

/**
 * Dialog responsible for obtaining user input(name) for a new deck(for all types: normal, subdeck).
 * Can also handle deck renames(which can technically be viewed as creating a deck).
 * @see CreateDeckViewModel
 * @see CreateDeckState
 */
class CreateDeckDialogFragment : DialogFragment() {
    private val viewModel by viewModels<CreateDeckViewModel>()
    private val type: CreateDeckType
        get() = requireArguments().requireParcelable<CreateDeckType>(ARG_TYPE)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentCreateDeckBinding.inflate(layoutInflater)
        binding.textInput.apply {
            doAfterTextChanged { text ->
                if (text != null && text.toString() != viewModel.state.value.input) {
                    viewModel.onInputChanged(text.toString())
                }
            }
            hint =
                when (type) {
                    CreateDeckType.Deck, CreateDeckType.Subdeck ->
                        TR
                            .actionsName()
                            .dropLastWhile { it == ':' }

                    CreateDeckType.Rename -> TR.actionsNewName().dropLastWhile { it == ':' }
                }
            setOnEditorActionListener { _, actionId, event ->
                if (viewModel.state.value.isInitializing) return@setOnEditorActionListener false
                if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    onConfirm()
                    true
                } else {
                    false
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> binding.bindState(state) }
            }
        }
        val title =
            when (type) {
                CreateDeckType.Deck -> getString(R.string.new_deck)
                CreateDeckType.Subdeck -> getString(R.string.create_subdeck)
                CreateDeckType.Rename -> getString(R.string.rename_deck)
            }
        val positiveButtonText =
            when (type) {
                CreateDeckType.Deck, CreateDeckType.Subdeck -> getString(R.string.dialog_positive_create)
                CreateDeckType.Rename -> getString(R.string.rename)
            }
        return AlertDialog
            .Builder(requireContext())
            .show {
                title(text = title)
                cancelable(true)
                negativeButton(R.string.dialog_cancel)
                positiveButton(text = positiveButtonText) { onConfirm() }
                setView(binding.root)
            }.apply { positiveButton.isEnabled = false }
    }

    private fun onConfirm() {
        val resultBundle =
            Bundle().apply {
                putParcelable(ARG_TYPE, type)
                putString(ARG_NAME, viewModel.fullDeckName)
                putLong(
                    ARG_DECK_ID,
                    requireArguments().getLongOrNull(ARG_DECK_ID) ?: -1,
                )
            }
        setFragmentResult(REQUEST_CREATE_DECK, resultBundle)
        dismiss()
    }

    private fun FragmentCreateDeckBinding.bindState(state: CreateDeckState) {
        if (state.fatalError != null) {
            // initialization has failed, nothing to do but exit
            requireActivity().showSnackbar(R.string.something_wrong)
            dismiss()
            return
        }
        loadingIndicator.isVisible = state.isInitializing
        if (state.shouldFocus) {
            textInput.isEnabled = true
            // set the text one time because we might be renaming and in this case we have something
            // to show
            textInput.setText(state.input)
            textInput.moveCursorToEnd()
            AndroidUiUtils.setFocusAndOpenKeyboard(textInput)
            viewModel.clearFocusRequest()
        }
        // Note: when helperText and error are both set, the last set one wins and is displayed while
        // the other text it's just briefly shown and then hidden, error is more important
        textInputLayout.helperText =
            if (state.showDoubleDigitsHelp) getString(R.string.create_deck_numeric_hint) else null
        textInputLayout.error =
            when (state.inputError) {
                CreateDeckInputError.Empty -> getString(R.string.toast_empty_name)
                CreateDeckInputError.AlreadyExists -> getString(R.string.error_name_exists)
                null -> null
            }
        (dialog as? AlertDialog)?.positiveButton?.isEnabled = state.isInputValid
    }

    companion object {
        const val TAG = "CreateDeckDialogFragment"
        const val REQUEST_CREATE_DECK = "request_create_deck"
        const val ARG_TYPE = "arg_type"
        const val ARG_DECK_ID = "arg_deck_id"
        const val ARG_NAME = "arg_name"
    }
}

fun FragmentManager.createDeck() {
    CreateDeckDialogFragment()
        .apply {
            arguments =
                Bundle().apply {
                    putParcelable(ARG_TYPE, CreateDeckType.Deck)
                }
        }.show(this, TAG)
}

fun FragmentManager.createSubDeck(deckId: DeckId) {
    CreateDeckDialogFragment()
        .apply {
            arguments =
                Bundle().apply {
                    putParcelable(ARG_TYPE, CreateDeckType.Subdeck)
                    putLong(ARG_DECK_ID, deckId)
                }
        }.show(this, TAG)
}

fun FragmentManager.renameDeck(deckId: DeckId) {
    CreateDeckDialogFragment()
        .apply {
            arguments =
                Bundle().apply {
                    putParcelable(ARG_TYPE, CreateDeckType.Rename)
                    putLong(ARG_DECK_ID, deckId)
                }
        }.show(this, TAG)
}

/**
 * Register a fragment result listener for [CreateDeckDialogFragment.REQUEST_CREATE_DECK]
 * @param action lambda which provides the type of the action and the fully qualified deck name
 * after the user's action
 */
fun FragmentManager.registerCreateDeckHandler(
    owner: LifecycleOwner,
    action: (type: CreateDeckType, name: String, did: DeckId) -> Unit,
) {
    setFragmentResultListener(
        CreateDeckDialogFragment.REQUEST_CREATE_DECK,
        owner,
    ) { _, bundle ->
        val type = bundle.requireParcelable<CreateDeckType>(ARG_TYPE)
        val name = bundle.requireString(ARG_NAME)
        val did = bundle.getLong(ARG_DECK_ID)
        action(type, name, did)
    }
}
