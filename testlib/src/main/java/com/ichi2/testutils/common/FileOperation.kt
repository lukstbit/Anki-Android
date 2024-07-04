package com.ichi2.testutils.common

import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString

class FileOperation {

    companion object {
        fun getFileResource(name: String): String {
            val resource = FileOperation::class.java.classLoader?.getResource(name)
                ?: error("Failed to obtain classloader for loading files")
            return (File(resource.path).path)
        }
    }
}

/**
 * Returns a new directory in the OS's default temp directory, using the given [prefix] to generate its name.
 * This directory is deleted on exit
 */
fun createTransientDirectory(prefix: String? = null): File =
    createTempDirectory(prefix = prefix).let {
        val file = File(it.pathString)
        file.deleteOnExit()
        return@let file
    }

/**
 * Returns a temp file with [content]. The file is deleted on exit.
 * @param extension The file extension. Do not include a "."
 */
fun createTransientFile(content: String = "", extension: String? = null): File =
    File(kotlin.io.path.createTempFile(suffix = if (extension == null) null else ".$extension").pathString).also {
        it.deleteOnExit()
        OutputStreamWriter(FileOutputStream(it), "UTF-8").use { writer ->
            writer.write(content)
            writer.flush()
        }
    }

/** Creates a sub-directory with the given name which is deleted on exit */
fun File.createTransientDirectory(name: String): File {
    File(this, name).also { directory ->
        directory.deleteOnExit()
        Timber.d("test: creating $directory")
        MatcherAssert.assertThat("directory should have been created", directory.mkdirs(), CoreMatchers.equalTo(true))
        return directory
    }
}
