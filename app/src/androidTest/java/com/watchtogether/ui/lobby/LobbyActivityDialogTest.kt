package com.watchtogether.ui.lobby

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
import com.watchtogether.ui.discovery.DiscoveryActivity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for the role swap dialog in LobbyActivity.
 *
 * Tests verify:
 * - Role swap dialog displays with correct title and message
 * - Clicking "OK" updates UI state (role becomes viewer)
 * - Clicking "Cancel" dismisses the dialog without state change
 * - Dialog is not cancelable (setCancelable(false))
 * - Dialog can be triggered for both host and viewer scenarios
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LobbyActivityDialogTest {

    private var scenario: ActivityScenario<LobbyActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launchAsHost(): ActivityScenario<LobbyActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), LobbyActivity::class.java).apply {
            putExtra(DiscoveryActivity.EXTRA_IS_HOST, true)
            putExtra(DiscoveryActivity.EXTRA_HOST_ADDRESS, "192.168.49.1")
        }
        return ActivityScenario.launch<LobbyActivity>(intent).also { scenario = it }
    }

    private fun launchAsViewer(): ActivityScenario<LobbyActivity> {
        val intent = Intent(ApplicationProvider.getApplicationContext(), LobbyActivity::class.java).apply {
            putExtra(DiscoveryActivity.EXTRA_IS_HOST, false)
            putExtra(DiscoveryActivity.EXTRA_HOST_ADDRESS, "192.168.49.1")
        }
        return ActivityScenario.launch<LobbyActivity>(intent).also { scenario = it }
    }

    private fun triggerRoleSwapDialog(scenario: ActivityScenario<LobbyActivity>, requesterName: String) {
        scenario.onActivity { activity ->
            val method = LobbyActivity::class.java.getDeclaredMethod("showRoleSwapDialog", String::class.java)
            method.isAccessible = true
            method.invoke(activity, requesterName)
        }
    }

    @Test
    fun hostLobby_showsSelectVideoButton() {
        launchAsHost()

        onView(withText(R.string.lobby_select_video))
            .check(matches(isDisplayed()))
    }

    @Test
    fun hostLobby_showsHostRole() {
        launchAsHost()

        onView(withText(R.string.role_host))
            .check(matches(isDisplayed()))
    }

    @Test
    fun hostLobby_showsHostReadyStatus() {
        launchAsHost()

        onView(withText(R.string.lobby_host_ready))
            .check(matches(isDisplayed()))
    }

    @Test
    fun viewerLobby_showsBecomeHostButton() {
        launchAsViewer()

        onView(withText(R.string.lobby_become_host))
            .check(matches(isDisplayed()))
    }

    @Test
    fun viewerLobby_showsViewerRole() {
        launchAsViewer()

        onView(withText(R.string.role_viewer))
            .check(matches(isDisplayed()))
    }

    @Test
    fun roleSwapDialog_displaysCorrectTitleAndMessage() {
        val sc = launchAsHost()
        val requesterName = "TestDevice"

        triggerRoleSwapDialog(sc, requesterName)

        onView(withText(R.string.lobby_role_swap_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        val expectedMessage = ApplicationProvider.getApplicationContext<android.app.Application>()
            .getString(R.string.lobby_role_swap_message, requesterName)
        onView(withText(expectedMessage))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun roleSwapDialog_clickOk_updatesUIToViewer() {
        val sc = launchAsHost()

        triggerRoleSwapDialog(sc, "TestDevice")

        onView(withText(android.R.string.ok))
            .inRoot(isDialog())
            .perform(click())

        // After accepting, role should change to viewer
        onView(withText(R.string.role_viewer))
            .check(matches(isDisplayed()))

        // "Become Host" button should now be visible
        onView(withText(R.string.lobby_become_host))
            .check(matches(isDisplayed()))

        // Status should show waiting for host
        onView(withText(R.string.lobby_waiting_for_host))
            .check(matches(isDisplayed()))
    }

    @Test
    fun roleSwapDialog_clickCancel_dismissesDialog() {
        val sc = launchAsHost()

        triggerRoleSwapDialog(sc, "TestDevice")

        onView(withText(android.R.string.cancel))
            .inRoot(isDialog())
            .perform(click())

        // Dialog should be dismissed
        onView(withText(R.string.lobby_role_swap_title))
            .check(doesNotExist())

        // Host role should remain unchanged
        onView(withText(R.string.role_host))
            .check(matches(isDisplayed()))

        // "Select a Video" button should still be visible (host UI unchanged)
        onView(withText(R.string.lobby_select_video))
            .check(matches(isDisplayed()))
    }

    @Test
    fun roleSwapDialog_isNotCancelable() {
        val sc = launchAsHost()

        triggerRoleSwapDialog(sc, "TestDevice")

        // Verify dialog is showing
        onView(withText(R.string.lobby_role_swap_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        // Press back — dialog should NOT be dismissed since setCancelable(false)
        androidx.test.espresso.Espresso.pressBack()

        // Dialog should still be visible
        onView(withText(R.string.lobby_role_swap_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun roleSwapDialog_showsCorrectRequesterName() {
        val sc = launchAsHost()
        val customName = "Galaxy S24 Ultra"

        triggerRoleSwapDialog(sc, customName)

        val expectedMessage = ApplicationProvider.getApplicationContext<android.app.Application>()
            .getString(R.string.lobby_role_swap_message, customName)
        onView(withText(expectedMessage))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }
}
