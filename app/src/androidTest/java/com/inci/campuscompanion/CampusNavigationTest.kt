package com.inci.campuscompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test

class CampusNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun toolbarBackReturnsToTasksAndCanOpenEventsAgain() {
        compose.onNodeWithText("Dark Mode").assertIsDisplayed()
        compose.onNodeWithText("Campus events").performClick()
        compose.onNodeWithText("Campus Events").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Dark Mode").assertIsDisplayed()
        compose.onNodeWithText("Campus events").performClick()
        compose.onNodeWithText("Campus Events").assertIsDisplayed()
    }

    @Test fun systemBackPopsEventsToTasks() {
        compose.onNodeWithText("Campus events").performClick()
        compose.onNodeWithText("Campus Events").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("Dark Mode").assertIsDisplayed()
    }
}
