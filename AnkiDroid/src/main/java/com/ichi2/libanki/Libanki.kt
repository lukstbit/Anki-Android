package com.ichi2.libanki

import com.ichi2.libanki.utils.StringProvider

object Libanki {

    lateinit var stringProvider: StringProvider
        private set

    fun init(stringProvider: StringProvider) {
        this.stringProvider = stringProvider
    }
}
