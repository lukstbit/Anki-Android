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

package com.ichi2.anki

import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.commit
import com.ichi2.anki.shareddecks.SharedDecksBrowserFragment
import java.io.Serializable

/**
 * Container activity for fragments related to browsing and downloading shared decks from AnkiWeb.
 *
 * @see SharedDecksBrowserFragment
 * @see SharedDecksDownloadFragment
 */
class SharedDecksActivity : AnkiActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shared_decks)
        enableToolbar().apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.download_deck)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, SharedDecksBrowserFragment())
            }
        }
        supportFragmentManager.setFragmentResultListener(
            SharedDecksBrowserFragment.KEY_DOWNLOAD_REQUESTED,
            this
        ) { _, bundle ->
            supportFragmentManager.commit {
                replace(
                    R.id.fragment_container,
                    SharedDecksDownloadFragment.newInstance(
                        BundleCompat.getSerializable(
                            bundle,
                            SharedDecksBrowserFragment.ARG_DOWNLOAD_FILE,
                            DownloadFile::class.java
                        ) ?: error("Missing required download file info")
                    )
                ).addToBackStack(null)
            }
        }
    }
}

/**
 * Used for sending download info between [SharedDecksBrowserFragment] and [SharedDecksDownloadFragment].
 */
data class DownloadFile(
    val url: String,
    val userAgent: String,
    val contentDisposition: String,
    val mimeType: String
) : Serializable
