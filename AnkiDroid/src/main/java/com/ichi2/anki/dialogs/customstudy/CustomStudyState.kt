// SPDX-FileCopyrightText: 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.customstudy

sealed interface CustomStudyState {
    data class Initializing(
        val error: Exception? = null,
    ) : CustomStudyState

    data class Data(
        val defaults: CustomStudyDefaults,
        val option: CustomStudyOption,
        val cardState: CustomStudyCardState,
        val hasTags: Boolean = true,
        val tagsToInclude: List<String> = emptyList(),
        val tagsToExclude: List<String> = emptyList(),
    ) : CustomStudyState
}

val CustomStudyState.isInitializing: Boolean
    get() = this is CustomStudyState.Initializing

val CustomStudyState.hasInitializationFailed: Boolean
    get() = this is CustomStudyState.Initializing && this.error != null

val CustomStudyState.canSelectTags: Boolean
    get() = this is CustomStudyState.Data && option == CustomStudyOption.STUDY_TAGS && hasTags
