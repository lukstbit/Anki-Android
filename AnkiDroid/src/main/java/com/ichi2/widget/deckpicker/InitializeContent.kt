// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget.deckpicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ichi2.compose.theme.dimensions

@Composable
fun InitializeContent(
    modifier: Modifier = Modifier,
    loading: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        if (loading) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .padding(MaterialTheme.dimensions.space200)
                        .align(Alignment.Center),
            )
        } else {
            content()
        }
    }
}
