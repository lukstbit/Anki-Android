/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.notes.NoteFieldsCheckResponse
import com.ichi2.testutils.JvmTest
import com.ichi2.utils.createBasicModel
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class CollectionTest : JvmTest() {
    @Test
    fun editClozeGenerateCardsInSameDeck() {
        // #7781
        // Technically, editing a card with conditional fields can also cause this, but cloze cards are much more common
        val n = addNoteUsingModelName("Cloze", "{{c1::Hello}} {{c2::World}}", "Extra")
        val did = addDeck("Testing")
        n.updateCards { this.did = did }
        assertThat("two cloze notes should be generated", n.numberOfCards(), equalTo(2))

        // create card 3
        n.setField(0, n.fields[0] + "{{c3::third}}")
        n.flush()
        assertThat("A new card should be generated", n.numberOfCards(), equalTo(3))
        assertThat("The new card should have the same did as the previous cards", n.cards()[2].did, equalTo(did))
    }

    @Test
    fun `clozeNumbersInNote is deterministic`() {
        val cloze = col.notetypes.byName("Cloze")!!
        val note = col.newNote(cloze).apply {
            setField(0, "{{c1::Hello}} {{c3::World}}")
        }

        repeat(5) {
            assertThat(col.clozeNumbersInNote(note)[0], equalTo(1))
        }
    }

    /*******************
     ** autogenerated from https://github.com/ankitects/anki/blob/2c73dcb2e547c44d9e02c20a00f3c52419dc277b/pylib/tests/test_cards.py *
     *******************/
    /*TODO
      @Test
      public void test_create_open(){
      (fd, path) = tempfile.mkstemp(suffix=".anki2", prefix="test_attachNew");
      try {
      os.close(fd);
      os.unlink(path);
      } catch (OSError) {
      }
      Collection col = aopen(path);
      // for open()
      String newPath = col.getPath();
      long newMod = col.getMod();
      col.close();

      // reopen
      col = aopen(newPath);
      assertEquals(newMod, col.getMod());
      col.close();

      // non-writeable dir
      if (isWin) {
      String dir = "c:\root.anki2";
      } else {
      String dir = "/attachroot.anki2";
      }
      assertException(Exception, lambda: aopen(dir));
      // reuse tmp file from before, test non-writeable file
      os.chmod(newPath, 0);
      assertException(Exception, lambda: aopen(newPath));
      os.chmod(newPath, 0o666);
      os.unlink(newPath);
      } */
    @Test
    fun test_noteAddDelete() {
        // add a note
        var note = col.newNote()
        note.setItem("Front", "one")
        note.setItem("Back", "two")
        var n = col.addNote(note)
        assertEquals(1, n)
        // test multiple cards - add another template
        val m = col.notetypes.current()
        val mm = col.notetypes
        val t = Notetypes.newTemplate("Reverse")
        t.put("qfmt", "{{Back}}")
        t.put("afmt", "{{Front}}")
        mm.addTemplateModChanged(m, t)
        mm.save(m)
        assertEquals(2, col.cardCount())
        // creating new notes should use both cards
        note = col.newNote()
        note.setItem("Front", "three")
        note.setItem("Back", "four")
        n = col.addNote(note)
        assertEquals(2, n)
        assertEquals(4, col.cardCount())
        // check q/a generation
        val c0 = note.cards()[0]
        assertThat(c0.question(), Matchers.containsString("three"))
        // it should not be a duplicate
        assertEquals(note.fieldsCheck(col), NoteFieldsCheckResponse.State.NORMAL)
        // now let's make a duplicate
        val note2 = col.newNote()
        note2.setItem("Front", "one")
        note2.setItem("Back", "")
        assertNotEquals(note2.fieldsCheck(col), NoteFieldsCheckResponse.State.NORMAL)
        // empty first field should not be permitted either
        note2.setItem("Front", " ")
        assertNotEquals(note2.fieldsCheck(col), NoteFieldsCheckResponse.State.NORMAL)
    }

    @Test
    @Ignore("I don't understand this csum")
    fun test_fieldChecksum() {
        val note = col.newNote()
        note.setItem("Front", "new")
        note.setItem("Back", "new2")
        col.addNote(note)
        assertEquals(-0xc2a6b03f, col.db.queryLongScalar("select csum from notes"))
        // changing the val should change the checksum
        note.setItem("Front", "newx")
        note.flush()
        assertEquals(0x302811ae, col.db.queryLongScalar("select csum from notes"))
    }

    @Test
    fun test_addDelTags() {
        val note = col.newNote()
        note.setItem("Front", "1")
        col.addNote(note)
        val note2 = col.newNote()
        note2.setItem("Front", "2")
        col.addNote(note2)
        // adding for a given id
        col.tags.bulkAdd(listOf(note.id), "foo")
        note.load()
        note2.load()
        assertTrue(note.tags.contains("foo"))
        assertFalse(note2.tags.contains("foo"))
        // should be canonified
        col.tags.bulkAdd(listOf(note.id), "foo aaa")
        note.load()
        assertEquals("aaa", note.tags[0])
        assertEquals(2, note.tags.size)
    }

    @Test
    fun test_timestamps() {
        // old code used StdModels.STD_MODELS.size for this variable. There were 6 models:
        // BASIC_MODEL, BASIC_TYPING_MODEL, FORWARD_REVERSE_MODEL, FORWARD_OPTIONAL_REVERSE_MODEL,
        // CLOZE_MODEL, IMAGE_OCCLUSION_MODEL
        val stdModelSize = 6
        assertEquals(col.notetypes.all().size, stdModelSize)
        for (i in 0..99) {
            col.createBasicModel("Basic")
        }
        assertEquals(col.notetypes.all().size, (100 + stdModelSize))
    }

    @Test
    @Ignore("Pending port of media search from Rust code")
    fun test_furigana() {
        val mm = col.notetypes
        val m = mm.current()
        // filter should work
        m.getJSONArray("tmpls").getJSONObject(0).put("qfmt", "{{kana:Front}}")
        mm.save(m)
        val n = col.newNote()
        n.setItem("Front", "foo[abc]")
        col.addNote(n)
        val c = n.cards()[0]
        assertTrue(c.question().endsWith("abc"))
        // and should avoid sound
        n.setItem("Front", "foo[sound:abc.mp3]")
        n.flush()
        val question = c.question(true)
        assertThat("Question «$question» does not contains «anki:play».", question, Matchers.containsString("anki:play"))
        // it shouldn't throw an error while people are editing
        m.getJSONArray("tmpls").getJSONObject(0).put("qfmt", "{{kana:}}")
        mm.save(m)
        c.question(true)
    }

    @Test
    fun test_filterToValidCards() {
        val cid = addNoteUsingBasicModel("foo", "bar").firstCard().id
        assertEquals(ArrayList(setOf(cid)), col.filterToValidCards(longArrayOf(cid, cid + 1)))
    }

    @Test
    fun `default card columns`() {
        assertThat(
            col.loadBrowserCardColumns(),
            equalTo(
                listOf("noteFld", "template", "cardDue", "deck")
            )
        )
    }

    @Test
    fun `default note columns`() {
        assertThat(
            col.loadBrowserNoteColumns(),
            equalTo(
                listOf("noteFld", "note", "template", "noteTags")
            )
        )
    }

    @Test
    fun `set note columns`() {
        col.setBrowserNoteColumns(listOf("noteFld"))

        assertThat(
            col.loadBrowserNoteColumns(),
            equalTo(
                listOf("noteFld")
            )
        )
    }

    @Test
    fun `set card columns`() {
        col.setBrowserCardColumns(listOf("question"))

        assertThat(
            col.loadBrowserCardColumns(),
            equalTo(
                listOf("question")
            )
        )
    }

    @Test
    fun `get browser column`() {
        kotlin.test.assertNotNull(col.getBrowserColumn("question")).also {
            assertThat(it.cardsModeLabel, equalTo("Question"))
        }

        assertThat(col.getBrowserColumn("invalid"), nullValue())
    }

    @Test
    fun `get all columns`() {
        assertThat(col.allBrowserColumns(), not(hasSize(0)))
    }
}
