// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.widget.deckpicker

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.compose.theme.AnkiDroidTheme

@Composable
fun DeckWidgetConfigDeckItem(
    text: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_drag_indicator_24),
            contentDescription = null,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.sentence_delete_deck),
            )
        }
    }
}

@Preview(widthDp = 320)
@Preview(widthDp = 320, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DeckWidgetConfigDeckItemPreview() {
    AnkiDroidTheme {
        DeckWidgetConfigDeckItem(
            text = "Learn english",
            onRemove = {},
        )
    }
}
