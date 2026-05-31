// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit

package com.ichi2.widget.deckpicker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ichi2.anki.R
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.compose.theme.AnkiDroidTheme
import kotlinx.coroutines.launch

@Composable
fun DecksWidgetConfigScreen(
    modifier: Modifier = Modifier,
    viewModel: DecksWidgetConfigViewModel = viewModel<DecksWidgetConfigViewModel>(),
    onNavigationExit: () -> Unit = {},
    onDeckSelection: () -> Unit = {},
    onFinishDeckSelection: () -> Unit = {},
) {
    val snackBarState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val maxReachedMessage = pluralStringResource(R.plurals.deck_limit_reached, 5, 5)
    if (state.hasReachedMaxDecksCount) {
        LaunchedEffect(Unit) {
            onFinishDeckSelection()
            snackBarState.showSnackbar(
                message = maxReachedMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }

    Scaffold(
        topBar = {
            TopBarSingleAction(
                title = "Configure widget",
                onExit = onNavigationExit,
                actionEnabled = !state.isInitializing,
                actionLabel = stringResource(R.string.save),
                onAction = { viewModel.save() },
            )
        },
        floatingActionButton = {
            if (!state.isInitializing && !state.hasReachedMaxDecksCount) {
                ExtendedFloatingActionButton(
                    onClick = onDeckSelection,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription =
                                stringResource(
                                    backendProducer = { actionsAddDeck() },
                                    "Add deck",
                                ),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text =
                                stringResource(
                                    backendProducer = { actionsAddDeck() },
                                    "Add deck",
                                ),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackBarState) },
        modifier = modifier,
    ) {
        InitializeContent(
            loading = state.isInitializing,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it),
        ) {
            val deckRemovedMessage = stringResource(R.string.deck_removed_from_widget)
            DecksWidgetConfigContentScreen(
                state = state,
                modifier = modifier,
                onRemove = { entry ->
                    viewModel.removeDeck(entry)
                    coroutineScope.launch {
                        snackBarState.showSnackbar(
                            message = deckRemovedMessage,
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun DecksWidgetConfigContentScreen(
    state: DecksWidgetConfigState,
    modifier: Modifier = Modifier,
    onRemove: (DeckNameId) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.decks.isEmpty()) {
            Text(
                text = stringResource(R.string.no_selected_deck_placeholder_title),
                style = MaterialTheme.typography.titleMedium,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val currentNrOfDecks = state.decks.size
                Text(
                    "$currentNrOfDecks / 5",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp),
                    color =
                        when (currentNrOfDecks) {
                            5 -> Color.Red
                            else -> Color.Green
                        },
                )
                LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                    items(state.decks) { deckNameId ->
                        DeckWidgetConfigDeckItem(
                            text = deckNameId.name,
                            onRemove = { onRemove(deckNameId) },
                        )
                    }
                }
            }
        }
    }
}

private val previewDecks =
    listOf(
        DeckNameId("Learn english", 1L),
        DeckNameId("Longer deck name that takes more space", 2L),
        DeckNameId("Another study deck", 3L),
        DeckNameId("Easy", 4L),
        DeckNameId("Always last", 5L),
    )

@Preview(widthDp = 320, name = "ContentNoDecks")
@Preview(widthDp = 320, name = "ContentNoDecksDark", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DecksWidgetConfigContentNonePreview() {
    AnkiDroidTheme {
        DecksWidgetConfigContentScreen(
            state = DecksWidgetConfigState(decks = emptyList()),
        )
    }
}

@Preview(widthDp = 320, name = "ContentAllDecks")
@Preview(widthDp = 320, name = "ContentAllDecksDark", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DecksWidgetConfigContentAllPreview() {
    AnkiDroidTheme {
        DecksWidgetConfigContentScreen(
            state = DecksWidgetConfigState(decks = previewDecks),
        )
    }
}

@Preview(widthDp = 320, name = "ContentSomeDecks")
@Preview(widthDp = 320, name = "ContentSomeDecksDark", uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DecksWidgetConfigContentSomePreview() {
    AnkiDroidTheme {
        DecksWidgetConfigContentScreen(
            state = DecksWidgetConfigState(decks = previewDecks.take(3)),
        )
    }
}
