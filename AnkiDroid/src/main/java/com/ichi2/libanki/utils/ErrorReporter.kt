package com.ichi2.libanki.utils

interface ErrorReporter {

    fun sendExceptionReport(ex: Exception, origin: String)
}
