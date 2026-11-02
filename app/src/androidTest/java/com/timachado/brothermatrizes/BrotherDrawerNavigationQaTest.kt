package com.timachado.brothermatrizes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.timachado.brothermatrizes.ui.theme.BrotherMatrizesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Suite isolada: valida navegacao sem inicializar login, compras ou API. */
class BrotherDrawerNavigationQaTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun drawerGroupsAreReachableAndNavigationDispatchesSelection() {
        var selectedDestination: BrotherDrawerDestination? = null
        composeRule.setContent {
            BrotherMatrizesTheme {
                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(DrawerValue.Open),
                    drawerContent = {
                        BrotherNavigationDrawerContent(
                            selected = BrotherDrawerDestination.HOME,
                            hasActiveMatrix = true,
                            onSelect = { selectedDestination = it }
                        )
                    }
                ) { Box(Modifier.fillMaxSize()) }
            }
        }

        composeRule.onNodeWithTag("brother_drawer_home").assertIsDisplayed()
        composeRule.onNodeWithTag("brother_drawer_create_name").assertExists()
        composeRule.onNodeWithTag("brother_drawer_monogram").assertExists()
        composeRule.onNodeWithTag("brother_drawer_library").assertExists()
        composeRule.onNodeWithTag("brother_drawer_convert")
            .performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(BrotherDrawerDestination.CONVERTER, selectedDestination)
        }
    }

    @Test
    fun smallScreenAndLargeTextStillReachAccountAndSupport() {
        var destination: BrotherDrawerDestination? = null
        composeRule.setContent {
            BrotherMatrizesTheme(textScaleMultiplier = 1.30f) {
                ModalNavigationDrawer(
                    drawerState = rememberDrawerState(DrawerValue.Open),
                    drawerContent = {
                        BrotherNavigationDrawerContent(
                            selected = BrotherDrawerDestination.HOME,
                            hasActiveMatrix = false,
                            onSelect = { destination = it }
                        )
                    }
                ) { Box(Modifier.fillMaxSize()) }
            }
        }
        composeRule.onNodeWithTag("brother_drawer_transfer")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("brother_drawer_community")
            .performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(BrotherDrawerDestination.COMMUNITY, destination)
        }
        composeRule.onNodeWithTag("brother_drawer_account")
            .performScrollTo().assertExists()
    }
}
