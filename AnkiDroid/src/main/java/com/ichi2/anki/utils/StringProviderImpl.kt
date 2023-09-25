package com.ichi2.anki.utils

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.libanki.utils.StringProvider

class StringProviderImpl : StringProvider {
    override fun cardBrowserDueFilteredCard(): String =
        AnkiDroidApp.appResources.getString(R.string.card_browser_due_filtered_card)

    override fun modelFrontFieldName(): String =
        AnkiDroidApp.appResources.getString(R.string.front_field_name)

    override fun modelBackFieldName(): String =
        AnkiDroidApp.appResources.getString(R.string.back_field_name)

    override fun cardOneName(): String =
        AnkiDroidApp.appResources.getString(R.string.card_n_name, 1)

    override fun cardTwoName(): String =
        AnkiDroidApp.appResources.getString(R.string.card_n_name, 2)

    override fun fieldToAskFrontName(): String =
        AnkiDroidApp.appResources.getString(R.string.field_to_ask_front_name)

    override fun clozeTextFieldName(): String =
        AnkiDroidApp.appResources.getString(R.string.text_field_name)

    override fun clozeExtraFieldNameNew(): String =
        AnkiDroidApp.appResources.getString(R.string.extra_field_name_new)

    override fun clozeCardTypeName(): String =
        AnkiDroidApp.appResources.getString(R.string.cloze_model_name)

    override fun basicModelName(): Int = R.string.basic_model_name

    override fun basicTypingModelName(): Int = R.string.basic_typing_model_name

    override fun forwardReverseModelName(): Int = R.string.forward_reverse_model_name

    override fun forwardOptionalReverseModelName(): Int =
        R.string.forward_optional_reverse_model_name

    override fun clozeModelName(): Int = R.string.cloze_model_name

    override fun localizedString(resId: Int): String = AnkiDroidApp.appResources.getString(resId)
}
