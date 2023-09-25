package com.ichi2.anki.utils

import com.ichi2.anki.CrashReportService
import com.ichi2.libanki.utils.ErrorReporter

class ErrorReporterImpl : ErrorReporter {
    override fun sendExceptionReport(ex: Exception, origin: String) {
        CrashReportService.sendExceptionReport(ex, origin)
    }
}
