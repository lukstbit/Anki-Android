package com.ichi2.libanki.utils

import androidx.annotation.StringRes

interface StringProvider {

    fun cardBrowserDueFilteredCard(): String

    fun modelFrontFieldName(): String

    fun modelBackFieldName(): String

    fun cardOneName(): String

    fun cardTwoName(): String

    fun fieldToAskFrontName(): String

    fun clozeTextFieldName(): String

    fun clozeExtraFieldNameNew(): String

    fun clozeCardTypeName(): String

    @StringRes
    fun basicModelName(): Int

    @StringRes
    fun basicTypingModelName(): Int

    @StringRes
    fun forwardReverseModelName(): Int

    @StringRes
    fun forwardOptionalReverseModelName(): Int

    @StringRes
    fun clozeModelName(): Int

    fun localizedString(@StringRes resId: Int): String
}
