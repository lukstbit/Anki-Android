/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.getAllClozeTextFields
import com.ichi2.anki.utils.ext.templates
import com.ichi2.libanki.Collection
import com.ichi2.libanki.NotetypeJson
import com.ichi2.libanki.Notetypes
import junit.framework.TestCase.assertEquals
import kotlin.test.Test

// link to a method in `NoteType.kt` for navigation as it contains no classes
/** Test of [NoteType][templates] */
class NoteTypeTest {

    private val noteType = """
        {
          "type":1,
          "tmpls":[
               {
                 "name":"Cloze",
                 "ord":0,
                 "qfmt":"{{type:cloze:Text}} {{type:cloze:Text2}} {{cloze:Text3}} {{Added field}}",
                 "afmt":"{{cloze:Text}}<br>\n{{Back Extra}}",
                 "bqfmt":"",
                 "bafmt":"",
                 "did":null,
                 "bfont":"",
                 "bsize":0,
                 "id":1716321740
              }
           ]
        }
    """

    @Test
    fun testQfmtField() {
        val notetypeJson = NotetypeJson(noteType)

        val expectedQfmt = "{{type:cloze:Text}} {{type:cloze:Text2}} {{cloze:Text3}} {{Added field}}"
        assertEquals(expectedQfmt, notetypeJson.templates[0].qfmt)
    }

    @Test
    fun testGetAllClozeTexts() {
        val notetypeJson = NotetypeJson(noteType)

        val expectedClozeTexts = listOf("Text", "Text2", "Text3")
        assertEquals(expectedClozeTexts, notetypeJson.getAllClozeTextFields())
    }

    @Test
    fun testNameField() {
        val notetypeJson = NotetypeJson(noteType)
        val expectedName = "Cloze"
        assertEquals(expectedName, notetypeJson.templates[0].name)
    }

    @Test
    fun testOrdField() {
        val notetypeJson = NotetypeJson(noteType)
        val expectedOrd = 0
        assertEquals(expectedOrd, notetypeJson.templates[0].ord)
    }

    @Test
    fun testAfmtField() {
        val notetypeJson = NotetypeJson(noteType)
        val expectedAfmt = "{{cloze:Text}}<br>\n{{Back Extra}}"
        assertEquals(expectedAfmt, notetypeJson.templates[0].afmt)
    }
}

const val BASIC_MODEL_NAME = "Basic"

/**
 * Creates and returns a basic model.
 *
 * @param name name of the new model
 * @return the new model
 */
fun Collection.createBasicModel(name: String): NotetypeJson {
    val m = notetypes.new(name)
    val frontName = AnkiDroidApp.appResources.getString(R.string.front_field_name)
    var fm = notetypes.newField(frontName)
    notetypes.addFieldInNewModel(m, fm)
    val backName = AnkiDroidApp.appResources.getString(R.string.back_field_name)
    fm = notetypes.newField(backName)
    notetypes.addFieldInNewModel(m, fm)
    val cardOneName = CollectionManager.TR.cardTemplatesCard(1)
    val t = Notetypes.newTemplate(cardOneName)
    t.put("qfmt", "{{$frontName}}")
    t.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{$backName}}")
    notetypes.addTemplateInNewModel(m, t)
    notetypes.save(m)
    return m
}

/**
 * Creates a basic typing model by adding on top of a basic model.
 *
 * @see createBasicModel
 */
fun Collection.createBasicTypingModel(name: String): NotetypeJson {
    val m = createBasicModel(name)
    val t = m.getJSONArray("tmpls").getJSONObject(0)
    val frontName = m.getJSONArray("flds").getJSONObject(0).getString("name")
    val backName = m.getJSONArray("flds").getJSONObject(1).getString("name")
    t.put("qfmt", "{{$frontName}}\n\n{{type:$backName}}")
    t.put("afmt", "{{$frontName}}\n\n<hr id=answer>\n\n{{type:$backName}}")
    notetypes.save(m)
    return m
}
