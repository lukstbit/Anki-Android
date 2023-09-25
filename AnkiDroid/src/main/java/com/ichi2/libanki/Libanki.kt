package com.ichi2.libanki

import com.ichi2.libanki.utils.ErrorReporter
import com.ichi2.libanki.utils.StringProvider

object Libanki {

    lateinit var stringProvider: StringProvider
        private set
    lateinit var errorReporter: ErrorReporter
        private set

    fun init(stringProvider: StringProvider, errorReporter: ErrorReporter) {
        this.stringProvider = stringProvider
        this.errorReporter = errorReporter
    }
}
