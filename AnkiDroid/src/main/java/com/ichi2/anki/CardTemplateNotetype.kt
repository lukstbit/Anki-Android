/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>                                   *
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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.bundleOf
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.CardTemplate
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.async.saveNoteType
import com.ichi2.compat.CompatHelper.Companion.compat
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** A wrapper for a notetype in JSON format with helpers for editing the notetype. */
@KotlinCleanup("_templateChanges -> use templateChanges")
class CardTemplateNotetype(
    val notetype: NotetypeJson,
) {
    enum class ChangeType {
        ADD,
        DELETE,
    }

    private var _templateChanges = ArrayList<Array<Any>>()

    fun toBundle(): Bundle =
        bundleOf(
            INTENT_MODEL_FILENAME to saveTempNoteType(AnkiDroidApp.instance.applicationContext, notetype),
            "mTemplateChanges" to _templateChanges,
        )

    private fun loadTemplateChanges(bundle: Bundle) {
        try {
            _templateChanges = bundle.getSerializableCompat("mTemplateChanges")!!
        } catch (e: ClassCastException) {
            Timber.e(e, "Unexpected cast failure")
        }
    }

    fun getTemplate(ord: Int): CardTemplate {
        Timber.d("getTemplate() on ordinal %s", ord)
        return notetype.templates[ord]
    }

    val templateCount: Int
        get() = notetype.templates.length()

    val noteTypeId: NoteTypeId
        get() = notetype.id

    var css: String
        get() = notetype.css
        set(value) {
            notetype.css = value
        }

    fun updateTemplate(
        ordinal: Int,
        template: CardTemplate,
    ) {
        notetype.templates[ordinal] = template
    }

    fun addNewTemplate(newTemplate: CardTemplate) {
        Timber.d("addNewTemplate()")
        addTemplateChange(ChangeType.ADD, newTemplate.ord)
    }

    fun removeTemplate(ord: Int) {
        Timber.d("removeTemplate() on ordinal %s", ord)
        addTemplateChange(ChangeType.DELETE, ord)
    }

    fun saveToDatabase(col: Collection) {
        Timber.d("saveToDatabase() called")
        dumpChanges()
        clearTempNoteTypeFiles()
        return saveNoteType(col, notetype, adjustedTemplateChanges)
    }

    /**
     * Template deletes shift card ordinals in the database. To operate without saving, we must keep track to apply in order.
     * In addition, we don't want to persist a template add just to delete it later, so we combine those if they happen
     */
    fun addTemplateChange(
        type: ChangeType,
        ordinal: Int,
    ) {
        Timber.d("addTemplateChange() type %s for ordinal %s", type, ordinal)
        val templateChanges = templateChanges
        val change = arrayOf<Any>(ordinal, type)

        // If we are deleting something we added but have not saved, edit it out of the change list
        if (type == ChangeType.DELETE) {
            var ordinalAdjustment = 0
            for (i in templateChanges.indices.reversed()) {
                val oldChange = templateChanges[i]
                when (oldChange[1] as ChangeType) {
                    ChangeType.DELETE ->
                        if (oldChange[0] as Int - ordinalAdjustment <= ordinal) {
                            // Deleting an ordinal at or below us? Adjust our comparison basis...
                            ordinalAdjustment++
                            continue
                        }
                    ChangeType.ADD ->
                        if (ordinal == oldChange[0] as Int - ordinalAdjustment) {
                            // Deleting something we added this session? Edit it out via compaction
                            compactTemplateChanges(oldChange[0] as Int)
                            return
                        }
                }
            }
        }
        Timber.d("addTemplateChange() added ord/type: %s/%s", change[0], change[1])
        templateChanges.add(change)
        dumpChanges()
    }

    /**
     * Return an int[] containing the collection-relative ordinals of all the currently pending deletes,
     * including the ordinal passed in, as opposed to the changelist-relative ordinals
     *
     * @param ord int UI-relative ordinal to check database for delete safety along with existing deletes
     * @return int[] of all ordinals currently in the database, pending delete
     */
    fun getDeleteDbOrds(ord: Int): IntArray {
        dumpChanges()
        Timber.d("getDeleteDbOrds()")

        // array containing the original / db-relative ordinals for all pending deletes plus the proposed one
        val deletedDbOrds = ArrayList<Int>(_templateChanges.size)

        // For each entry in the changes list - and the proposed delete - scan for deletes to get original ordinal
        for (i in 0.._templateChanges.size) {
            var ordinalAdjustment = 0

            // We need an initializer. Though proposed change is checked last, it's a reasonable default initializer.
            var currentChange = arrayOf<Any>(ord, ChangeType.DELETE)
            if (i < _templateChanges.size) {
                // Until we exhaust the pending change list we will use them
                currentChange = _templateChanges[i]
            }

            // If the current pending change isn't a delete, it is unimportant here
            if (currentChange[1] !== ChangeType.DELETE) {
                continue
            }

            // If it is a delete, scan previous deletes and shift as necessary for original ord
            for (j in 0 until i) {
                val previousChange = _templateChanges[j]

                // Is previous change a delete? Lower ordinal than current change?
                if (previousChange[1] === ChangeType.DELETE && previousChange[0] as Int <= currentChange[0] as Int) {
                    // If so, that is the case where things shift. It means our ordinals moved and original ord is higher
                    ordinalAdjustment++
                }
            }

            // We know how many times ordinals smaller than the current were deleted so we have the total adjustment
            // Save this pending delete at it's original / db-relative position
            deletedDbOrds.add(currentChange[0] as Int + ordinalAdjustment)
        }
        val deletedDbOrdInts = IntArray(deletedDbOrds.size)
        for (i in deletedDbOrdInts.indices) {
            deletedDbOrdInts[i] = deletedDbOrds[i]
        }
        return deletedDbOrdInts
    }

    private fun dumpChanges() {
        if (!BuildConfig.DEBUG) {
            return
        }
        val adjustedChanges = adjustedTemplateChanges
        for (i in _templateChanges.indices) {
            val change = _templateChanges[i]
            val adjustedChange = adjustedChanges[i]
            Timber.d("dumpChanges() Change %s is ord/type %s/%s", i, change[0], change[1])
            Timber.d(
                "dumpChanges() During save change %s will be ord/type %s/%s",
                i,
                adjustedChange[0],
                adjustedChange[1],
            )
        }
    }

    val templateChanges: ArrayList<Array<Any>>
        get() {
            return _templateChanges
        }

    /**
     * Adjust the ordinals in our accrued change list so that any pending adds have the correct
     * ordinal after taking into account any pending deletes
     *
     * @return ArrayList<Object></Object>[2]> of [ordinal][ChangeType] entries
     */
    val adjustedTemplateChanges: ArrayList<Array<Any>>
        get() {
            val changes = templateChanges
            val adjustedChanges = ArrayList<Array<Any>>(changes.size)

            // In order to save the changes into the database, the ordinals in the changelist must correspond to the
            // ordinals in the database (for deletes) or the correct index in the changes array (for adds)
            // It is not possible to know what those will be until the user requests a save, so they are stored in the
            // change list as-is until the save time comes, then the adjustment is made all at once
            for (i in changes.indices) {
                val change = changes[i]
                val adjustedChange = arrayOf(change[0], change[1])
                when (adjustedChange[1] as ChangeType) {
                    ChangeType.ADD -> {
                        adjustedChange[0] = getAdjustedAddOrdinalAtChangeIndex(this, i)
                        Timber.d(
                            "getAdjustedTemplateChanges() change %s ordinal adjusted from %s to %s",
                            i,
                            change[0],
                            adjustedChange[0],
                        )
                    }
                    ChangeType.DELETE -> {}
                }
                adjustedChanges.add(adjustedChange)
            }
            return adjustedChanges
        }

    /**
     * Scan the sequence of template add/deletes, looking for the given ordinal.
     * When found, purge that ordinal and shift future changes down if they had ordinals higher than the one purged
     */
    private fun compactTemplateChanges(addedOrdinalToDelete: Int) {
        Timber.d(
            "compactTemplateChanges() merge/purge add/delete ordinal added as %s",
            addedOrdinalToDelete,
        )
        var postChange = false
        var ordinalAdjustment = 0
        var i = 0
        while (i < _templateChanges.size) {
            val change = _templateChanges[i]
            var ordinal = change[0] as Int
            val changeType = change[1] as ChangeType
            Timber.d("compactTemplateChanges() examining change entry %s / %s", ordinal, changeType)

            // Only make adjustments after the ordinal we want to delete was added
            if (!postChange) {
                if (ordinal == addedOrdinalToDelete && changeType == ChangeType.ADD) {
                    Timber.d("compactTemplateChanges() found our entry at index %s", i)
                    // Remove this entry to start compaction, then fix up the loop counter since we altered size
                    postChange = true
                    _templateChanges.removeAt(i)
                    i--
                }
                i++
                continue
            }

            // We compact all deletes with higher ordinals, so any delete is below us: shift our comparison basis
            if (changeType == ChangeType.DELETE) {
                ordinalAdjustment++
                Timber.d(
                    "compactTemplateChanges() delete affecting purged template, shifting basis, adj: %s",
                    ordinalAdjustment,
                )
            }

            // If following ordinals were higher, we move them as part of compaction
            if (ordinal + ordinalAdjustment > addedOrdinalToDelete) {
                Timber.d("compactTemplateChanges() shifting later/higher ordinal down")
                change[0] = --ordinal
            }
            i++
        }
    }

    companion object {
        const val INTENT_MODEL_FILENAME = "editedNoteTypeFilename"

        /**
         * Load the TemporaryNoteType from the filename included in a Bundle
         *
         * @param bundle a Bundle that should contain persisted JSON under INTENT_MODEL_FILENAME key
         * @return re-hydrated TemporaryNoteType or null if there was a problem, null means should reload from database
         */
        fun fromBundle(bundle: Bundle): CardTemplateNotetype? {
            val editedNoteTypeFileName = bundle.getString(INTENT_MODEL_FILENAME)
            // Bundle.getString is @Nullable, so we have to check.
            if (editedNoteTypeFileName == null) {
                Timber.d("fromBundle() - note type file name under key %s", INTENT_MODEL_FILENAME)
                return null
            }
            Timber.d("onCreate() loading saved note type file %s", editedNoteTypeFileName)
            val tempNotetypeJSON: NotetypeJson =
                try {
                    getTempNoteType(editedNoteTypeFileName)
                } catch (e: IOException) {
                    Timber.w(e, "Unable to load saved note type file")
                    return null
                }
            return CardTemplateNotetype(tempNotetypeJSON).apply {
                loadTemplateChanges(bundle)
            }
        }

        /**
         * Save the current note type to a temp file in the application internal cache directory
         * @return String representing the absolute path of the saved file, or null if there was a problem
         */
        fun saveTempNoteType(
            context: Context,
            tempNoteType: NotetypeJson,
        ): String? {
            Timber.d("saveTempNoteType() saving tempNoteType")
            var tempNoteTypeFile: File
            try {
                ByteArrayInputStream(tempNoteType.toString().toByteArray()).use { source ->
                    tempNoteTypeFile = File.createTempFile("editedTemplate", ".json", context.cacheDir)
                    compat.copyFile(source, tempNoteTypeFile.absolutePath)
                }
            } catch (ioe: IOException) {
                Timber.e(ioe, "Unable to create+write temp file for note type")
                return null
            }
            return tempNoteTypeFile.absolutePath
        }

        /**
         * Get the note type temporarily saved into the file represented by the given path
         * @return JSONObject holding the note type, or null if there was a problem
         */
        @Throws(IOException::class)
        fun getTempNoteType(tempNoteTypeFileName: String): NotetypeJson {
            Timber.d("getTempNoteType() fetching tempNoteType %s", tempNoteTypeFileName)
            try {
                ByteArrayOutputStream().use { target ->
                    compat.copyFile(tempNoteTypeFileName, target)
                    return NotetypeJson(target.toString())
                }
            } catch (e: IOException) {
                Timber.e(e, "Unable to read+parse tempNoteType from file %s", tempNoteTypeFileName)
                throw e
            }
        }

        /** Clear any temp note type files saved into internal cache directory  */
        fun clearTempNoteTypeFiles(): Int {
            var deleteCount = 0
            for (c in AnkiDroidApp.instance.cacheDir.listFiles() ?: arrayOf()) {
                val absolutePath = c.absolutePath
                if (absolutePath.contains("editedTemplate") && absolutePath.endsWith("json")) {
                    if (!c.delete()) {
                        Timber.w("Unable to delete temp file %s", c.absolutePath)
                    } else {
                        deleteCount++
                        Timber.d("Deleted temp note type file %s", c.absolutePath)
                    }
                }
            }
            return deleteCount
        }

        /**
         * Check if the given ordinal from the current UI state (which includes all pending changes) is a pending add
         *
         * @param ord int representing an ordinal in the note type, that might be an unsaved addition
         * @return boolean true if it is a pending addition from this editing session
         */
        fun isOrdinalPendingAdd(
            noteType: CardTemplateNotetype,
            ord: Int,
        ): Boolean {
            for (i in noteType.templateChanges.indices) {
                // commented out to make the code compile, why is this unused?
                // val change = noteType.templateChanges[i]
                val adjustedOrdinal = getAdjustedAddOrdinalAtChangeIndex(noteType, i)
                if (adjustedOrdinal == ord) {
                    Timber.d(
                        "isOrdinalPendingAdd() found ord %s was pending add (would adjust to %s)",
                        ord,
                        adjustedOrdinal,
                    )
                    return true
                }
            }
            Timber.d("isOrdinalPendingAdd() ord %s is not a pending add", ord)
            return false
        }

        /**
         * Check if the change at the given index in the changes array is an addition from this editing session
         * (and thus is not in the database yet, and possibly needing ordinal adjustment from subsequent deletes)
         * @param changesIndex the index of the template in the changes array
         * @return either ordinal adjusted by any pending deletes if it is a pending add, or -1 if the ordinal is not an add
         */
        fun getAdjustedAddOrdinalAtChangeIndex(
            noteType: CardTemplateNotetype,
            changesIndex: Int,
        ): Int {
            if (changesIndex >= noteType.templateChanges.size) {
                return -1
            }
            var ordinalAdjustment = 0
            val change = noteType.templateChanges[changesIndex]
            val ordinalToInspect = change[0] as Int
            for (i in noteType.templateChanges.size - 1 downTo changesIndex) {
                val oldChange = noteType.templateChanges[i]
                val currentOrdinal = change[0] as Int
                when (oldChange[1] as ChangeType) {
                    ChangeType.DELETE -> {
                        // Deleting an ordinal at or below us? Adjust our comparison basis...
                        if (currentOrdinal - ordinalAdjustment <= ordinalToInspect) {
                            ordinalAdjustment++
                            continue
                        }
                        Timber.d(
                            "getAdjustedAddOrdinalAtChangeIndex() contemplating delete at index %s, current ord adj %s",
                            i,
                            ordinalAdjustment,
                        )
                    }
                    ChangeType.ADD ->
                        if (changesIndex == i) {
                            // something we added this session
                            Timber.d(
                                "getAdjustedAddOrdinalAtChangeIndex() pending add found at at index %s, old ord/adjusted ord %s/%s",
                                i,
                                currentOrdinal,
                                currentOrdinal - ordinalAdjustment,
                            )
                            return currentOrdinal - ordinalAdjustment
                        }
                }
            }
            Timber.d(
                "getAdjustedAddOrdinalAtChangeIndex() determined changesIndex %s was not a pending add",
                changesIndex,
            )
            return -1
        }
    }
}

/**
 * Temporary file containing a [NotetypeJson]
 *
 * Useful for adding a [NotetypeJson] into a [Bundle], like when using [Intent.putExtra]
 * for sending an object to another activity.
 *
 * The notetype is written into a file because there is a
 * [limit of 1MB](https://developer.android.com/reference/android/os/TransactionTooLargeException.html)
 * for [Bundle] transactions, and notetypes can be bigger than that (#5600).
 */
class NotetypeFile(
    path: String,
) : File(path),
    Parcelable {
    /**
     * @param directory where the file will be saved
     * @param notetype to be stored
     */
    constructor(directory: File, notetype: NotetypeJson) : this(createTempFile("notetype", ".tmp", directory).absolutePath) {
        try {
            ByteArrayInputStream(notetype.toString().toByteArray()).use { source ->
                compat.copyFile(source, this.absolutePath)
            }
        } catch (ioe: IOException) {
            Timber.w(ioe, "Unable to create+write temp file for note type")
        }
    }

    /**
     * @param context for getting the cache directory
     * @param notetype to be stored
     */
    constructor(context: Context, notetype: NotetypeJson) : this(context.cacheDir, notetype)

    fun getNotetype(): NotetypeJson =
        try {
            ByteArrayOutputStream().use { target ->
                compat.copyFile(absolutePath, target)
                NotetypeJson(target.toString())
            }
        } catch (e: IOException) {
            Timber.e(e, "Unable to read+parse tempNoteType from file %s", absolutePath)
            throw e
        }

    override fun describeContents(): Int = 0

    override fun writeToParcel(
        dest: Parcel,
        flags: Int,
    ) {
        dest.writeString(path)
    }

    companion object {
        @JvmField
        @Suppress("unused")
        val CREATOR =
            object : Parcelable.Creator<NotetypeFile> {
                override fun createFromParcel(source: Parcel?): NotetypeFile = NotetypeFile(source!!.readString()!!)

                override fun newArray(size: Int): Array<NotetypeFile> = arrayOf()
            }
    }
}
