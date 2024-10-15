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
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiActivity.Companion.showDialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.showThemedToast
import timber.log.Timber

/**
 * Adapter used for listing the addons from AnkiDroid/addons directory.
 */
class AddonsBrowserAdapter(
    private val context: Context,
    private val onRefresh: () -> Unit
) : ListAdapter<AddonModel, AddonsBrowserAdapter.AddonsViewHolder>(addonModelDiff) {
    private val preferences: SharedPreferences = context.sharedPrefs()
    private val inflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddonsViewHolder =
        AddonsViewHolder(inflater.inflate(R.layout.item_addon, parent, false))

    override fun onBindViewHolder(holder: AddonsViewHolder, position: Int) {
        val addonModel: AddonModel = getItem(position)
        holder.addonTitle.text = addonModel.addonTitle
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
                showThemedToast(
                    context,
                    "Addon ${addonModel.addonTitle} was enabled",
                    true
                )
            } else {
                addonModel.updatePrefs(preferences, jsAddonKey, true)
                showThemedToast(
                    context,
                    "Addon ${addonModel.addonTitle} was disabled",
                    true
                )
            }
        }

        holder.detailsBtn.setOnClickListener {
            val addonModelDialog = AddonDetailsDialog.newInstance(addonModel)
            showDialogFragment(context as AnkiActivity, addonModelDialog)
        }

        // remove addon from directory and update prefs
        holder.removeBtn.setOnClickListener {
            val dialog = ConfirmationDialog()
            val title: String = addonModel.addonTitle
            val message = "Remove addon ${addonModel.addonTitle}?"
            dialog.setArgs(title, message)

            val confirm = Runnable {
                Timber.i("AddonsAdapter:: Delete addon pressed at %s", position)

                val deleteAddon =
                    AddonStorage(context).deleteSelectedAddonPackageDir(addonModel.name)
                if (!deleteAddon) {
                    showThemedToast(
                        context,
                        "Failed to remove addon.Please delete manually",
                        false
                    )
                    return@Runnable
                }

                // update prefs for removed addon
                addonModel.updatePrefs(preferences, addonModel.addonType, true)
                onRefresh()
            }

            dialog.setConfirm(confirm)
            showDialogFragment(context as AnkiActivity, dialog)
        }

        holder.itemView.setOnClickListener {
            showThemedToast(context, "Configuring an addon is not implemented yet!", false)
        }
    }

    inner class AddonsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val addonTitle: TextView = itemView.findViewById(R.id.addon_title)
        val addonDescription: TextView = itemView.findViewById(R.id.addon_description)
        val addonVersion: TextView = itemView.findViewById(R.id.addon_version)
        val removeBtn: Button = itemView.findViewById(R.id.addon_remove)
        val detailsBtn: Button = itemView.findViewById(R.id.addon_details)
        val addonActivate: SwitchCompat = itemView.findViewById(R.id.toggle_addon)
    }
}

private val addonModelDiff = object : DiffUtil.ItemCallback<AddonModel>() {
    // assume the same names and versions represent the same addon
    override fun areItemsTheSame(oldItem: AddonModel, newItem: AddonModel): Boolean {
        return oldItem.name == newItem.name && oldItem.version == newItem.version
    }

    override fun areContentsTheSame(oldItem: AddonModel, newItem: AddonModel): Boolean {
        return oldItem.name == newItem.name &&
            oldItem.addonType == newItem.addonType &&
            oldItem.addonTitle == newItem.addonTitle &&
            oldItem.author["name"] == oldItem.author["name"] &&
            oldItem.author["url"] == oldItem.author["url"] &&
            oldItem.version == newItem.version &&
            oldItem.ankidroidJsApi == newItem.ankidroidJsApi &&
            oldItem.description == newItem.description &&
            oldItem.homepage == newItem.homepage &&
            oldItem.license == newItem.license
    }
}
