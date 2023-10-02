package com.ichi2.libanki.utils

/**
 * Throw an exception if the condition is not true. Can optionally pass a message and format
 * parameters.
 *
 * @param condition the condition to test
 * @throws AssertionError if condition is false
 */
fun assertThat(condition: Boolean, message: String?, vararg args: Any?) {
    if (!condition) {
        val msg = String.format(message!!, *args)
        throw AssertionError(msg)
    }
}
