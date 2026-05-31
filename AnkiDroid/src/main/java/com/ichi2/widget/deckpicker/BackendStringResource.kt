// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit

package com.ichi2.widget.deckpicker

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import anki.i18n.GeneratedTranslations
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.ui.internationalization.SentenceCase
import com.ichi2.anki.ui.internationalization.sentenceCase
import net.ankiweb.rsdroid.Translations

@Composable
fun stringResource(
    backendProducer: SentenceCase.() -> String,
    @StringRes sentenceCase: Int,
): String =
    runCatching { TR.sentenceCase.backendProducer() }.getOrNull()
        ?: LocalResources.current.getString(sentenceCase)

@Composable
fun stringResource(
    backendProducer: Translations.() -> String,
    alternative: String,
): String = runCatching { TR.backendProducer() }.getOrNull() ?: alternative
