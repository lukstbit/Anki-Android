/****************************************************************************************
 * Copyright (c) 2022 Mani <infinyte01@gmail.com>                                       *
 *                                                                                      *
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
 ***************************************************************************************/

package com.ichi2.anki.jsaddons

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.*
import com.ichi2.anki.AnkiActivity.showDialogFragment
import com.ichi2.anki.dialogs.ConfirmationDialog
import timber.log.Timber
import java.util.*

/**
 * Browser Adapter for listing addons from AnkiDroid/addons directory in recycler view for toggle on/off
 * to add content to reviewer or note editor. Also view info and delete addons.
 */
class AddonsBrowserAdapter(private var addonList: MutableList<AddonModel>) : RecyclerView.Adapter<AddonsBrowserAdapter.AddonsViewHolder>() {
    private lateinit var preferences: SharedPreferences
    private lateinit var context: Context
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddonsViewHolder {
        context = parent.context
        preferences = AnkiDroidApp.getSharedPrefs(context)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.addon_item_in_browser, parent, false)
        return AddonsViewHolder(view)
    }

    override fun onBindViewHolder(holder: AddonsViewHolder, position: Int) {
        val addonModel: AddonModel = addonList[position]
        holder.addonTitleTextView.text = addonModel.addonTitle
        holder.addonVersion.text = addonModel.version
        holder.addonDescription.text = addonModel.description

        // enabled/disabled status as boolean value in SharedPreferences
        val jsAddonKey: String = addonModel.addonType
        val enabledAddonSet = preferences.getStringSet(jsAddonKey, HashSet())
        if (enabledAddonSet != null) {
            holder.addonActivate.isChecked = enabledAddonSet.contains(addonModel.name)
        }

        // toggle on/off addons
        holder.addonActivate.setOnClickListener {
            if (holder.addonActivate.isChecked) {
                addonModel.updatePrefs(preferences, jsAddonKey, false)
                UIUtils.showThemedToast(context, context.getString(R.string.addon_enabled, addonModel.addonTitle), true)
            } else {
                addonModel.updatePrefs(preferences, jsAddonKey, true)
                UIUtils.showThemedToast(context, context.getString(R.string.addon_disabled, addonModel.addonTitle), true)
            }
        }

        holder.detailsBtn.setOnClickListener {
            val addonModelDialog = AddonDetailsDialog(context, addonModel)
            showDialogFragment(context as AnkiActivity?, addonModelDialog)
        }

        // remove addon from directory and update prefs
        holder.removeBtn.setOnClickListener {
            val dialog = ConfirmationDialog()
            val title: String = addonModel.addonTitle
            val message = context.getString(R.string.confirm_remove_addon, addonModel.addonTitle)
            dialog.setArgs(title, message)

            val confirm = Runnable {
                Timber.i("AddonsAdapter:: Delete addon pressed at %s", position)

                val deleteAddon = AddonStorage(context).deleteSelectedAddonPackageDir(addonModel.name)
                if (!deleteAddon) {
                    UIUtils.showThemedToast(context, context.getString(R.string.failed_to_remove_addon), false)
                    return@Runnable
                }

                // update prefs for removed addon
                addonModel.updatePrefs(preferences, addonModel.addonType, true)
                addonList.remove(addonModel)
                notifyDataSetChanged()

                // show for getting addons no addons listed
                (context as AnkiActivity).findViewById<LinearLayout>(R.id.no_addons_found_msg).visibleIf(addonList.size == 0)
            }

            dialog.setConfirm(confirm)
            showDialogFragment(context as AnkiActivity?, dialog)
        }
    }

    override fun getItemCount(): Int {
        return addonList.size
    }

    inner class AddonsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var addonTitleTextView: TextView = itemView.findViewById(R.id.addon_title)
        var addonDescription: TextView = itemView.findViewById(R.id.addon_description)
        var addonVersion: TextView = itemView.findViewById(R.id.addon_version)
        var removeBtn: Button = itemView.findViewById(R.id.addon_remove)
        var detailsBtn: Button = itemView.findViewById(R.id.addon_details)
        var addonActivate: SwitchCompat = itemView.findViewById(R.id.activate_addon)
    }
}
