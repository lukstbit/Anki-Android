// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit

package com.ichi2.widget.deckpicker

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.R
import com.ichi2.compose.theme.AnkiDroidTheme

/**
 * Creates a [TopAppBar] which has an exit icon, (optional) title and a single action, similar with
 * some full screen dialogs.
 */
@Composable
fun TopBarSingleAction(
    modifier: Modifier = Modifier,
    title: String? = null,
    onExit: () -> Unit = {},
    actionEnabled: Boolean = true,
    actionLabel: String = stringResource(R.string.dialog_ok),
    onAction: () -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(
                    painter = painterResource(R.drawable.close_icon),
                    contentDescription = stringResource(R.string.dialog_exit),
                )
            }
        },
        actions = {
            TextButton(
                onClick = onAction,
                enabled = actionEnabled,
            ) {
                Text(text = actionLabel)
            }
        },
    )
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun TopBarSingleActionPreview() {
    AnkiDroidTheme {
        TopBarSingleAction(
            title = "Add account",
        )
    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun TopBarSingleDisabledActionPreview() {
    AnkiDroidTheme {
        TopBarSingleAction(
            title = "Add account",
            actionEnabled = false,
        )
    }
}
