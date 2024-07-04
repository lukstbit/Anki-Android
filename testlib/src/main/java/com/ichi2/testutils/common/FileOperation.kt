package com.ichi2.testutils.common

import java.io.File

class FileOperation {

    companion object {
        fun getFileResource(name: String): String {
            val resource = FileOperation::class.java.classLoader?.getResource(name)
                ?: error("Failed to obtain classloader for loading files")
            return (File(resource.path).path)
        }
    }
}
