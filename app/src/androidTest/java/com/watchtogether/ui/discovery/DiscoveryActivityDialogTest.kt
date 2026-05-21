package com.watchtogether.ui.discovery

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.watchtogether.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the exit confirmation dialog in DiscoveryActivity.
 *
 * Tests verify:
 * - Back press triggers the exit dialog
 * - Dialog displays correct title and message
 * - "Yes" finishes the activity
 * - "No" dismisses the dialog and keeps the activity alive
 * - Dialog is not duplicated on rapid back presses
 * - Dialog is cancelable (dismissible by tapping outside)
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DiscoveryActivityDialogTest {

    private lateinit var scenario: ActivityScenario<DiscoveryActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(DiscoveryActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun backPress_showsExitDialog() {
        Espresso.pressBack()

        onView(withText(R.string.exit_dialog_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun exitDialog_displaysCorrectTitleAndMessage() {
        Espresso.pressBack()

        onView(withText(R.string.exit_dialog_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText(R.string.exit_dialog_message))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun exitDialog_clickYes_finishesActivity() {
        Espresso.pressBack()

        onView(withText(android.R.string.yes))
            .inRoot(isDialog())
            .perform(click())

        // Activity should be finished/destroyed after clicking Yes
        scenario.onActivity { activity ->
            assert(activity.isFinishing) { "Activity should be finishing after clicking Yes" }
        }
    }

    @Test
    fun exitDialog_clickNo_dismissesDialogAndKeepsActivity() {
        Espresso.pressBack()

        onView(withText(android.R.string.no))
            .inRoot(isDialog())
            .perform(click())

        // Dialog should be dismissed
        onView(withText(R.string.exit_dialog_title))
            .check(doesNotExist())

        // Activity should still be alive
        scenario.onActivity { activity ->
            assert(!activity.isFinishing) { "Activity should NOT be finishing after clicking No" }
        }
    }

    @Test
    fun exitDialog_notDuplicated_onRapidBackPresses() {
        // First back press — dialog appears
        Espresso.pressBack()

        onView(withText(R.string.exit_dialog_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        // Second back press while dialog is showing — should not create a second dialog
        // (the OnBackPressedCallback fires again, but showExitConfirmationDialog guards
        // against showing if exitDialog?.isShowing == true)
        Espresso.pressBack()

        // There should still be exactly one dialog visible
        onView(withText(R.string.exit_dialog_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun exitDialog_isCancelable_byPressingBack() {
        Espresso.pressBack()

        onView(withText(R.string.exit_dialog_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        // Press back again to dismiss the cancelable dialog
        Espresso.pressBack()

        // Activity should still be alive (dialog was just dismissed)
        scenario.onActivity { activity ->
            assert(!activity.isFinishing) { "Activity should NOT be finishing after canceling dialog" }
        }
    }

    @Test
    fun exitDialog_afterDismiss_canBeShownAgain() {
        // Show and dismiss
        Espresso.pressBack()
        onView(withText(android.R.string.no))
            .inRoot(isDialog())
            .perform(click())

        // Show again
        Espresso.pressBack()
        onView(withText(R.string.exit_dialog_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }
}
