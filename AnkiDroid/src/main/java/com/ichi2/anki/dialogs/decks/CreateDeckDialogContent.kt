// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.dialogs.decks

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R

@Composable
fun CreateDeckDialogContent(
    modifier: Modifier = Modifier,
    isInitializing: Boolean = false,
    inputState: TextFieldState = rememberTextFieldState(),
    inputErrorMessage: String? = null,
) {
    OutlinedTextField(
        state = inputState,
        enabled = !isInitializing,
        isError = inputErrorMessage != null,
        modifier = modifier,
        supportingText = {
            if (inputErrorMessage != null) {
                Text(
                    inputErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        trailingIcon = {
            if (isInitializing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            if (inputErrorMessage != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_error_outline),
                    contentDescription = null,
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CreateDeckDialogContentPreview() {
    CreateDeckDialogContent(
        inputState = TextFieldState("Default"),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CreateDeckDialogContentInitPreview() {
    CreateDeckDialogContent(
        isInitializing = true,
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CreateDeckDialogContentErrorPreview() {
    CreateDeckDialogContent(
        inputErrorMessage = "Already exists",
    )
}
