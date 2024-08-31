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

package com.ichi2.anki.browser

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.utils.openUrl
import com.ichi2.libanki.DeckId
import com.ichi2.utils.BundleUtils.requireLong
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class FindAndReplaceDialogFragment : AnalyticsDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val deckId = requireArguments().requireLong(ARG_DECK_ID)
        val contentView = LayoutInflater.from(requireContext()).inflate(
            R.layout.fragment_find_replace,
            null
        ).apply(::setupUi)
        return AlertDialog.Builder(requireContext()).show {
            title(text = TR.browsingFindAndReplace())
            customView(contentView)
            neutralButton(R.string.help) { openUrl(R.string.browser_find_replace_help_url) }
            negativeButton(R.string.dialog_cancel)
            positiveButton(R.string.dialog_ok) {
            }
        }
    }

    private fun setupUi(contentView: View): View = contentView.apply {
        findViewById<TextView>(R.id.label_find).text =
            HtmlCompat.fromHtml(TR.browsingFind(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        findViewById<TextView>(R.id.label_replace).text =
            HtmlCompat.fromHtml(TR.browsingReplaceWith(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        findViewById<TextView>(R.id.label_in).text =
            HtmlCompat.fromHtml(TR.browsingIn(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        findViewById<CheckBox>(R.id.check_only_notes).text = TR.browsingSelectedNotesOnly()
        findViewById<CheckBox>(R.id.check_ignore_case).text = TR.browsingIgnoreCase()
        findViewById<CheckBox>(R.id.check_regex_input).text =
            TR.browsingTreatInputAsRegularExpression()
    }

    companion object {
        private const val ARG_DECK_ID = "arg_deck_id"
        const val TAG = "FindAndReplaceDialog"

        fun newInstance(deckId: DeckId): FindAndReplaceDialogFragment =
            FindAndReplaceDialogFragment().apply {
                arguments = bundleOf(ARG_DECK_ID to deckId)
            }
    }
}
