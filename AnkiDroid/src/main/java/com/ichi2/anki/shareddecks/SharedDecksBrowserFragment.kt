/****************************************************************************************
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
package com.ichi2.anki.shareddecks

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_INDEFINITE
import com.ichi2.anki.DownloadFile
import com.ichi2.anki.R
import com.ichi2.anki.isLoggedIn
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.openUrl
import com.ichi2.annotations.NeedsTest
import com.ichi2.ui.AccessibleSearchView
import timber.log.Timber

/**
 * Displays an in app [WebView] for the user to browse shared decks from AnkiWeb and initiate shared
 * decks downloads.
 *
 * @see com.ichi2.anki.SharedDecksActivity
 * @see com.ichi2.anki.SharedDecksDownloadFragment
 */
class SharedDecksBrowserFragment : Fragment(R.layout.fragment_shared_decks_browser), MenuProvider {

    private var shouldClearHistory = false
    private var webView: WebView? = null

    // starts disabled
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            webView?.goBack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        shouldClearHistory =
            savedInstanceState?.getBoolean(KEY_SHOULD_CLEAR_HISTORY, false) ?: false
        webView = view.findViewById<WebView?>(R.id.webview).apply {
            settings.javaScriptEnabled = true
            loadUrl(getString(R.string.shared_decks_url))
            webViewClient = customWebViewClient
            setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                setFragmentResult(
                    KEY_DOWNLOAD_REQUESTED,
                    bundleOf(
                        ARG_DOWNLOAD_FILE to DownloadFile(
                            url,
                            userAgent,
                            contentDisposition,
                            mimetype
                        )
                    )
                )
            }
        }
        (requireActivity() as MenuHost).addMenuProvider(this)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )
    }

    /**
     * Handle the situation when page finishes loading and history needs to be cleared. Currently,
     * this condition arises when user presses the home button on the toolbar.
     *
     * History should not be cleared before the page finishes loading otherwise there would be
     * an extra entry in the history since the previous page would not get cleared.
     */
    private val customWebViewClient = object : WebViewClient() {
        private var redirectTimes = 0

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            onBackPressedCallback.isEnabled = view?.canGoBack() ?: false
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            // clear history if shouldClearHistory is true and set it to false
            if (shouldClearHistory) {
                webView?.clearHistory()
                shouldClearHistory = false
            }
            super.onPageFinished(view, url)
        }

        /**
         * Prevent the [WebView] from loading urls which aren't needed for importing shared decks(
         * avoids potential misuse like bypassing content restrictions or using the AnkiDroid
         * [WebView] as a regular browser)
         */
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            val host = request?.url?.host
            if (host != null && allowedHosts.any { regex -> regex.matches(host) }) {
                return super.shouldOverrideUrlLoading(view, request)
            }
            request?.url?.let { requireActivity().openUrl(it) }
            return true
        }

        private val cookieManager: CookieManager by lazy { CookieManager.getInstance() }

        private val isLoggedInToAnkiWeb: Boolean
            get() {
                try {
                    // cookies are null after the user logs out, or if the site is first visited
                    val cookies = cookieManager.getCookie("https://ankiweb.net") ?: return false
                    // ankiweb currently (2024-09-25) sets two cookies:
                    // * `ankiweb`, which is base64-encoded JSON
                    // * `has_auth`, which is 1
                    return cookies.contains("has_auth=1")
                } catch (e: Exception) {
                    Timber.w(e, "Could not determine login status")
                    return false
                }
            }

        @NeedsTest("A user is not redirected to login/signup if they are logged in to AnkiWeb")
        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)

            if (errorResponse?.statusCode != HTTP_STATUS_TOO_MANY_REQUESTS) return

            // If a user is logged in, they see: "Daily limit exceeded; please try again tomorrow."
            // We have nothing we can do here
            if (isLoggedInToAnkiWeb) return

            // The following cases are handled below:
            // "Please log in to download more decks." - on clicking "Download"
            // "Please log in to perform more searches" - on searching
            redirectUserToSignUpOrLogin()
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            // set shouldClearHistory to false if error occurs since it might have been true
            shouldClearHistory = false
            super.onReceivedError(view, request, error)
        }

        /**
         * Redirects the user to a login page
         *
         * A message is shown informing the user they need to log in to download more decks
         *
         * If the user has not logged in **inside AnkiDroid** then the message provides
         * the user with an action to sign up
         *
         * The redirect is not performed if [redirectTimes] is 3 or more
         */
        private fun redirectUserToSignUpOrLogin() {
            // inform the user they need to log in as they've hit a rate limit
            showSnackbar(R.string.shared_decks_login_required, LENGTH_INDEFINITE) {
                if (isLoggedIn()) return@showSnackbar

                // If a user is not logged in inside AnkiDroid, assume they have no AnkiWeb account
                // and give them the option to sign up
                setAction(R.string.sign_up) {
                    webView?.loadUrl(getString(R.string.shared_decks_sign_up_url))
                }
            }

            // redirect user to /account/login
            // TODO: the result of login is typically redirecting the user to their decks
            // this should be improved

            if (redirectTimes++ < 3) {
                val url = getString(R.string.shared_decks_login_url)
                Timber.i("HTTP 429, redirecting to login: '$url'")
                webView?.loadUrl(url)
            } else {
                // Ensure that we do not have an infinite redirect
                Timber.w("HTTP 429 redirect limit exceeded, only displaying message")
            }
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.shared_decks_browser, menu)

        (menu.findItem(R.id.search)?.actionView as? AccessibleSearchView)?.apply {
            queryHint = getString(R.string.search_using_deck_name)
            setMaxWidth(Integer.MAX_VALUE)
            val queryListener = object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    webView?.loadUrl(resources.getString(R.string.shared_decks_url) + query)
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    // Nothing to do here
                    return false
                }
            }
            setOnQueryTextListener(queryListener)
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.home -> {
                shouldClearHistory = true
                webView?.loadUrl(resources.getString(R.string.shared_decks_url))
                true
            }

            else -> false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SHOULD_CLEAR_HISTORY, shouldClearHistory)
    }

    companion object {
        const val KEY_DOWNLOAD_REQUESTED = "key_download_requested"
        const val ARG_DOWNLOAD_FILE = "arg_download_file"
        private const val KEY_SHOULD_CLEAR_HISTORY = "key_should_clear_history"
        private const val HTTP_STATUS_TOO_MANY_REQUESTS = 429
        private val allowedHosts = listOf(
            Regex("""^(?:.*\.)?ankiweb\.net$"""),
            Regex("""^ankiuser\.net$"""),
            Regex("""^ankisrs\.net$""")
        )
    }
}
