/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.compat

import com.ichi2.testutils.common.FileOperation.Companion.getFileResource
import org.junit.Assert
import org.junit.Test
import java.io.*
import java.lang.StringBuilder
import java.net.URL
import java.security.MessageDigest

class CompatCopyFileTest : Test21And26() {
    @Test
    @Throws(Exception::class)
    fun testCopyFileToStream() {
        val resourcePath = getFileResource("path-traversal.zip")
        val copy = File.createTempFile("testCopyFileToStream", ".zip")
        copy.deleteOnExit()
        FileOutputStream(copy.canonicalPath).use { outputStream ->
            CompatHelper.compat.copyFile(resourcePath, outputStream)
        }
        Assert.assertEquals(getMD5(resourcePath), getMD5(copy.canonicalPath))
    }

    @Test
    @Throws(Exception::class)
    fun testCopyStreamToFile() {
        val resourcePath = getFileResource("path-traversal.zip")
        val copy = File.createTempFile("testCopyStreamToFile", ".zip")
        copy.deleteOnExit()
        CompatHelper.compat.copyFile(resourcePath, copy.canonicalPath)
        Assert.assertEquals(getMD5(resourcePath), getMD5(copy.canonicalPath))
    }

    @Test
    @Throws(Exception::class)
    fun testCopyErrors() {
        val resourcePath = getFileResource("path-traversal.zip")
        val copy = File.createTempFile("testCopyStreamToFile", ".zip")
        copy.deleteOnExit()

        // Try copying from a bogus file
        try {
            CompatHelper.compat.copyFile(FileInputStream(""), copy.canonicalPath)
            Assert.fail("Should have caught an exception")
        } catch (e: FileNotFoundException) {
            // This is expected
        }

        // Try copying to a closed stream
        try {
            val outputStream = FileOutputStream(copy.canonicalPath).apply { close() }
            CompatHelper.compat.copyFile(resourcePath, outputStream)
            Assert.fail("Should have caught an exception")
        } catch (e: IOException) {
            // this is expected
        }

        // Try copying from a closed stream
        try {
            val source = URL(resourcePath).openStream().apply { close() }
            CompatHelper.compat.copyFile(source, copy.canonicalPath)
            Assert.fail("Should have caught an exception")
        } catch (e: IOException) {
            // this is expected
        }

        // Try copying to a bogus file
        try {
            CompatHelper.compat.copyFile(resourcePath, "")
            Assert.fail("Should have caught an exception")
        } catch (e: Exception) {
            // this is expected
        }
    }

    /**
     * Get the MD5 checksum (in hex) for the given filename
     */
    @Throws(java.lang.Exception::class)
    private fun getMD5(filename: String): String {
        val md = MessageDigest.getInstance("MD5")
        val file = RandomAccessFile(File(filename), "r")
        val contentBytes = ByteArray(file.length().toInt())
        file.readFully(contentBytes)
        md.update(contentBytes)
        val hex = StringBuilder()
        for (b in md.digest()) {
            hex.append(String.format("%02x", b))
        }
        return hex.toString()
    }
}
