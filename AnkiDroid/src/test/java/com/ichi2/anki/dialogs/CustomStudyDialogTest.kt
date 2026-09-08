// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.os.Looper
import android.widget.AdapterView
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CustomStudyRequest.Cram.CramKind
import anki.scheduler.customStudyDefaultsResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.customstudy.CustomStudyCardState
import com.ichi2.anki.dialogs.customstudy.CustomStudyDefaults
import com.ichi2.anki.dialogs.customstudy.CustomStudyDefaults.Companion.toDomainModel
import com.ichi2.anki.dialogs.customstudy.CustomStudyViewModel
import com.ichi2.anki.dialogs.tags.TagsDialogListener.Companion.ON_SELECTED_TAGS_KEY
import com.ichi2.anki.dialogs.tags.TagsDialogListener.Companion.ON_SELECTED_TAGS__SELECTED_TAGS
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.QueueType
import com.ichi2.testutils.AnkiFragmentScenario
import com.ichi2.testutils.isJsonEqual
import com.ichi2.testutils.uninitializeField
import com.ichi2.utils.positiveButton
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.CoreMatchers.allOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.fail
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class CustomStudyDialogTest : RobolectricTest() {
    @Test
    fun `new custom study decks have expected structure - issue 6289`() =
        runTest {
            // we need a non-empty deck to custom study
            addBasicNote()

            onCustomStudyFragment { fragment ->
                fragment.binding.optionPreviewNewCards.isChecked = true
                fragment.binding.btnAction.performClick()
            }

            val customStudy = col.decks.current()
            assertThat("Custom Study should be filtered", customStudy.isFiltered)

            // remove timestamps to allow us to compare JSON
            customStudy.remove("id")
            customStudy.remove("mod")
            customStudy.remove("name")

            // compare JSON
            @Language("json")
            val expected =
                """
                {
                    "browserCollapsed": true,
                    "collapsed": true,
                    "delays": null,
                    "desc": "",
                    "dyn": 1,
                    "lrnToday": [0, 0],
                    "newToday": [0, 0],
                    "previewDelay": 10,
                    "previewAgainSecs": 60,
                    "previewHardSecs": 600,
                    "previewGoodSecs": 0,
                    "resched": false,
                    "revToday": [0, 0],
                    "separate": true,
                    "terms": [
                        ["is:new added:1 deck:Default", 99999, 5]
                    ],
                    "timeToday": [0, 0],
                    "usn": -1
                }
                """.trimIndent()
            assertThat(customStudy, isJsonEqual(expected))
        }

    @Test
    fun `previous value for 'increase new card limit' is suggested`() {
        // add cards to be sure we can extend successfully. Needs to be > 20
        repeat(23) {
            addBasicNote()
        }
        val newExtendByValue = 1

        assertThat("'new' default value", defaultsOfDefaultDeck.extendNew.initialValue, equalTo(0))

        // extend limits with a value of '1'
        onCustomStudyFragment { fragment ->
            fragment.binding.optionNew.isChecked = true
            fragment.binding.input.setText(newExtendByValue.toString())
            fragment.binding.btnAction.performClick()
        }

        // ensure backend is updated
        assertThat(
            "'new' updated value",
            defaultsOfDefaultDeck.extendNew.initialValue,
            equalTo(newExtendByValue),
        )

        // ensure 'newExtendByValue' is used by our UI
        onCustomStudyFragment { fragment ->
            fragment.binding.optionNew.isChecked = true
            assertThat(
                fragment.binding.input.text
                    ?.toString(),
                equalTo(newExtendByValue.toString()),
            )
        }
    }

    @Test
    fun `previous value for 'increase review card limit' is suggested`() {
        // Reduce review limit to 0, so we can successfully extend with just 1 review card.
        updateDeckConfig(Consts.DEFAULT_DECK_ID) { rev.perDay = 0 }
        addRevBasicNoteDueToday("Review", "Today")

        val reviewExtendByValue = 1
        assertThat(
            "'review' default value",
            defaultsOfDefaultDeck.extendReview.initialValue,
            equalTo(0),
        )

        // Extend reviews by 'reviewExtendByValue'.
        onCustomStudyFragment { fragment ->
            fragment.binding.optionReview.isChecked = true
            fragment.binding.input.setText(reviewExtendByValue.toString())
            fragment.binding.btnAction.performClick()
        }

        // Ensure backend is updated.
        assertThat(
            "'review' updated value",
            defaultsOfDefaultDeck.extendReview.initialValue,
            equalTo(reviewExtendByValue),
        )

        // Ensure 'reviewExtendByValue' is used in our UI.
        onCustomStudyFragment { fragment ->
            fragment.binding.optionReview.isChecked = true
            assertThat(
                fragment.binding.input.text
                    ?.toString(),
                equalTo(reviewExtendByValue.toString()),
            )
        }
    }

    @Test
    fun `creating a tags custom session uses selected card state`() =
        runTest {
            val testDeckId = addDeck("A")
            val n1 = addNoteToDeckA { addTag("testTag") }
            val n2 = addNoteToDeckA { addTag("anotherTag") }
            val n3 = addNoteToDeckA { addTag("testTag") }
            // target this specific card for custom studying
            // TODO use Card.moveToReviewQueue when it's refactored
            n3.firstCard().update {
                due = col.sched.today
                queue = QueueType.Rev
                type = CardType.Rev
            }
            col.updateCard(n3.firstCard())
            val dueNow = col.findCards("is:due")
            assertThat(dueNow.size, equalTo(1))
            assertThat(dueNow[0], equalTo(n3.firstCard().id))
            // make sure there isn't a 'Custom Study Session' already present
            assertNull(col.decks.customStudySession)
            onCustomStudyFragment(testDeckId) { studyDialog ->
                // the first item is automatically selected at start
                studyDialog.binding.optionCardStateOrTags.isChecked = true
                assertTrue(studyDialog.binding.optionCardStateOrTags.isChecked)
                assertTrue(studyDialog.binding.cardStateNew.isChecked)
                assertThat(
                    studyDialog.viewModel.dataState.cardState,
                    equalTo(CustomStudyCardState.NewCardsOnly),
                )
                // set card state to due
                studyDialog.binding.cardStateDue.isChecked = true
                assertThat(
                    studyDialog.viewModel.dataState.cardState,
                    equalTo(CustomStudyCardState.DueCardsOnly),
                )

                // create list of selected tags
                // Note: using an ArrayList because that is how tags are stored in the Bundle
                val selectedTags = ArrayList<String>(1).apply { add("testTag") }
                // simulate tag selection
                studyDialog.parentFragmentManager.setFragmentResult(
                    ON_SELECTED_TAGS_KEY,
                    Bundle().apply { putStringArrayList(ON_SELECTED_TAGS__SELECTED_TAGS, selectedTags) },
                )
                advanceUntilIdle()
                val customStudyDeckId = col.decks.customStudySession?.id
                assertNotNull(customStudyDeckId)
                assertThat(col.decks.cardCount(customStudyDeckId!!), equalTo(1))
                assertThat(n1.firstCard().did, equalTo(testDeckId))
                assertThat(n2.firstCard().did, equalTo(testDeckId))
                assertThat(n3.firstCard().did, equalTo(customStudyDeckId))
                assertThat(n3.firstCard().oDid, equalTo(testDeckId))
            }
        }

    private fun addNoteToDeckA(setup: Note.() -> Unit): Note =
        addBasicNote().update {
            moveToDeck("A", false)
            setup()
        }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    @Config(qualifiers = "en")
    fun `'increase new limit' is shown when there are new cards`() {
//        val studyDefaults = customStudyDefaultsResponse { availableNew = 1 }
//        CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(studyDefaults))
//
//        onCustomStudyFragment(args = argumentsDisplayingMainScreen()) {
//            onView(withText(TR.customStudyIncreaseTodaysNewCardLimit()))
//                .inRoot(isDialog())
//                .check(matches(isEnabled()))
//        }
    }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    @Config(qualifiers = "en")
    fun `'increase new limit' is not shown when there are no new cards`() {
//        val studyDefaults = customStudyDefaultsResponse { availableNew = 0 }
//        CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(studyDefaults))
//
//        onCustomStudyFragment(args = argumentsDisplayingMainScreen()) {
//            onView(withText(TR.customStudyIncreaseTodaysNewCardLimit()))
//                .inRoot(isDialog())
//                .check(matches(not(isEnabled())))
//        }
    }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    @Config(qualifiers = "en")
    fun `'increase review limit' is shown when there are new cards`() {
//        val studyDefaults = customStudyDefaultsResponse { availableReview = 1 }
//        CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(studyDefaults))
//
//        onCustomStudyFragment(args = argumentsDisplayingMainScreen()) {
//            onView(withText(TR.customStudyIncreaseTodaysReviewCardLimit()))
//                .inRoot(isDialog())
//                .check(matches(isEnabled()))
//        }
    }

    @Test
    @Ignore("disabled while we confirm/diagnose issues")
    @Config(qualifiers = "en")
    fun `'increase review limit' is not shown when there are no new cards`() {
        val studyDefaults = customStudyDefaultsResponse { availableReview = 0 }
        CollectionManager.setColForTests(mockCollectionWithSchedulerReturning(studyDefaults))

        withCustomStudyFragment(args = argumentsDisplayingMainScreen()) {
            onView(withText(TR.customStudyIncreaseTodaysReviewCardLimit()))
                .inRoot(isDialog())
                .check(matches(not(isEnabled())))
        }
    }

    @Test
    fun `subscreens are ignored when restoring from process death`() {
        withCustomStudyFragment(args = argumentsDisplayingSubscreen(CustomStudyOption.EXTEND_REV, restoreFromProcessDeath = true)) {
            // ensure we're on the main screen
            onView(withText(TR.customStudyIncreaseTodaysReviewCardLimit()))
                .inRoot(isDialog())
                .check(matches(isEnabled()))
        }
    }

    @Test
    fun `selectedKind maps selected card state index to cram kind`() {
        val viewModel = CustomStudyViewModel(SavedStateHandle())

        CustomStudyDialog.CustomStudyCardState.entries.forEachIndexed { index, cardState ->
            viewModel.selectedCardStateIndex = index
            assertThat(viewModel.selectedKind, equalTo(cardState.kind))
        }
    }

    @Test
    fun `selectedKind defaults to new cards when no card state is selected`() {
        val viewModel = CustomStudyViewModel(SavedStateHandle())

        viewModel.selectedCardStateIndex = AdapterView.INVALID_POSITION

        assertThat(viewModel.selectedKind, equalTo(CramKind.CRAM_KIND_NEW))
    }

    @Test
    @Config(qualifiers = "en")
    fun `'review ahead' shows a day or days suffix`() {
        withStudyAheadDialog {
            val layout = binding.detailsEditText2Layout
            assertThat("default of 1 day", layout.suffixText.toString(), equalTo("day"))

            onSubscreenEditText().perform(replaceText("2"))
            assertThat(layout.suffixText.toString(), equalTo("days"))

            onSubscreenEditText().perform(replaceText("1"))
            assertThat(layout.suffixText.toString(), equalTo("day"))
        }
    }

    @Test
    fun `'review ahead' rejects leading zeros and more than 5 digits`() {
        withStudyAheadDialog {
            val editText = binding.detailsEditText2

            onSubscreenEditText().perform(replaceText("0"))
            editText.append("5")
            assertThat("a digit typed after '0' is rejected", editText.text.toString(), equalTo("0"))

            onSubscreenEditText().perform(replaceText("123456"))
            assertThat(editText.text.toString(), equalTo("12345"))
        }
    }

    @Test
    fun `'review ahead' disables Create when no cards are due in the period`() =
        runTest {
            // a card due tomorrow matches 'review ahead by 1 day'
            val card = addBasicNote().firstCard()
            card.update {
                queue = QueueType.Rev
                type = CardType.Rev
                due = col.sched.today + 1
            }

            withStudyAheadDialog { dialog ->
                val layout = binding.detailsEditText2Layout

                onSubscreenEditText().perform(replaceText("1"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat("enabled when a card matches", dialog.positiveButton.isEnabled, equalTo(true))
                assertNull(layout.error)

                onSubscreenEditText().perform(replaceText("0"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat("disabled for 0 days", dialog.positiveButton.isEnabled, equalTo(false))

                card.update { due = col.sched.today + 30 }

                onSubscreenEditText().perform(replaceText("1"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat("disabled when nothing matches", dialog.positiveButton.isEnabled, equalTo(false))
                assertThat(layout.error?.toString(), equalTo(TR.customStudyNoCardsMatchedTheCriteriaYou()))
            }
        }

    @Test
    fun `'review ahead' search does not treat the deck name as a pattern`() =
        runTest {
            // '_' is a single-character wildcard in a search: "A_B" must not match "AXB"
            val emptyDeckId = addDeck("A_B")
            addDeck("AXB")
            addNoteDueTomorrow(deckName = "AXB")

            withStudyAheadDialog(deckId = emptyDeckId) { dialog ->
                onSubscreenEditText().perform(replaceText("1"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat("A_B has no cards", dialog.positiveButton.isEnabled, equalTo(false))
            }
        }

    @Test
    fun `'review ahead' search escapes the deck name`() =
        runTest {
            // '\' starts an escape sequence in a search
            val deckId = addDeck("""A\B""")
            addNoteDueTomorrow(deckName = """A\B""")

            withStudyAheadDialog(deckId = deckId) { dialog ->
                onSubscreenEditText().perform(replaceText("1"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat(dialog.positiveButton.isEnabled, equalTo(true))
            }
        }

    @Test
    @Config(qualifiers = "en")
    fun `'review ahead' warns when 0 days is entered`() =
        runTest {
            withStudyAheadDialog {
                val layout = binding.detailsEditText2Layout

                onSubscreenEditText().perform(replaceText("0"))
                shadowOf(Looper.getMainLooper()).idle()
                assertThat(layout.error?.toString(), equalTo("Minimum value is 1"))

                onSubscreenEditText().perform(replaceText(""))
                shadowOf(Looper.getMainLooper()).idle()
                assertNull(layout.error, "an empty field is not an error yet")
            }
        }

    @Test
    fun `'review ahead' validates the default value on open`() =
        runTest {
            // the default is 1 day; the only card is due in 30 days
            addBasicNote().firstCard().update {
                queue = QueueType.Rev
                type = CardType.Rev
                due = col.sched.today + 30
            }

            withStudyAheadDialog { dialog ->
                assertThat("nothing to review ahead", dialog.positiveButton.isEnabled, equalTo(false))
                assertThat(binding.detailsEditText2Layout.error?.toString(), equalTo(TR.customStudyNoCardsMatchedTheCriteriaYou()))
            }
        }

    /** Adds a note to [deckName] whose card is due tomorrow */
    private fun addNoteDueTomorrow(deckName: String) {
        addBasicNote().update { moveToDeck(deckName, false) }.firstCard().update {
            queue = QueueType.Rev
            type = CardType.Rev
            due = col.sched.today + 1
        }
    }

    /**
     * Runs [block] on a [CustomStudyDialog]
     */
    private fun withCustomStudyFragment(
        args: Bundle,
        block: (CustomStudyDialog) -> Unit,
    ) {
        AnkiFragmentScenario.launch(CustomStudyDialog::class.java, args).use { scenario ->
            scenario.onFragment { dialogFragment: CustomStudyDialog ->
                block(dialogFragment)
            }
        }
    }

    /** Opens the 'review ahead' subscreen for [deckId] and runs [block] once the dialog is shown and its buttons exist */
    private fun withStudyAheadDialog(
        deckId: DeckId = Consts.DEFAULT_DECK_ID,
        block: CustomStudyDialog.(dialog: AlertDialog) -> Unit,
    ) = withCustomStudyFragment(args = argumentsDisplayingSubscreen(ContextMenuOption.STUDY_AHEAD, deckId = deckId)) { fragment ->
        shadowOf(Looper.getMainLooper()).idle()
        fragment.block(fragment.dialog as AlertDialog)
    }

    private fun mockCollectionWithSchedulerReturning(response: CustomStudyDefaultsResponse) =
        mockk<Collection>(relaxed = true) {
            every { sched } returns
                mockk<Scheduler> {
                    every { customStudyDefaults(Consts.DEFAULT_DECK_ID) } returns response
                }
        }

    private fun argumentsDisplayingSubscreen(
        subscreen: CustomStudyOption,
    private fun onCustomStudyFragment(
        deckId: DeckId = Consts.DEFAULT_DECK_ID,
        block: (CustomStudyFragment) -> Unit,
    ) {
        val args =
            Bundle().apply {
                putLong(CustomStudyViewModel.KEY_DID, deckId)
            }
        AnkiFragmentScenario.launch(CustomStudyFragment::class.java, args).use { scenario ->
            scenario.onFragment { dialogFragment: CustomStudyFragment -> block(dialogFragment) }
        }
    }

    private val CustomStudyViewModel.dataState: CustomStudyState.Data
        get() =
            this.state.value as? CustomStudyState.Data
                ?: fail("Expected current ViewModel state to be CustomStudyState.Data, found: $this")

//    private fun mockCollectionWithSchedulerReturning(response: CustomStudyDefaultsResponse) =
//        mockk<Collection>(relaxed = true) {
//            every { sched } returns
//                    mockk<Scheduler> {
//                        every { customStudyDefaults(Consts.DEFAULT_DECK_ID) } returns response
//                    }
//        }

    /**
     * The current backend value of [CustomStudyDefaults] for the default deck
     * */
    private val defaultsOfDefaultDeck
        get() = col.sched.customStudyDefaults(Consts.DEFAULT_DECK_ID).toDomainModel()
}
