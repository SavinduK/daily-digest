package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailydigest.ui.navigation.Screen
import com.example.dailydigest.ui.screens.AddTopicScreen
import com.example.dailydigest.ui.screens.AnalyticsScreen
import com.example.dailydigest.ui.screens.AskTopicScreen
import com.example.dailydigest.ui.screens.HomeScreen
import com.example.dailydigest.ui.screens.SettingsScreen
import com.example.dailydigest.ui.viewmodel.DigestViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DigestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: DigestViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isManagingTopics by viewModel.isManagingTopics.collectAsStateWithLifecycle()
    val selectedTopicForHistory by viewModel.selectedTopicForHistory.collectAsStateWithLifecycle()
    val selectedTopicForChat by viewModel.selectedTopicForChat.collectAsStateWithLifecycle()

    // BackHandler: if managing topics, return to previous screen
    BackHandler(enabled = isManagingTopics) {
        viewModel.setManagingTopics(false)
    }

    // BackHandler: if viewing a specific topic's news history, return to topic list
    BackHandler(enabled = !isManagingTopics && selectedTopicForHistory != null) {
        viewModel.closeTopicHistory()
    }

    // BackHandler: if in a topic chat, return to chat topic list
    BackHandler(enabled = !isManagingTopics && selectedTopicForHistory == null && selectedTopicForChat != null) {
        viewModel.closeTopicChat()
    }

    // BackHandler: if on Settings, Ask AI, or Digest tab, pressing back returns to Home
    BackHandler(enabled = !isManagingTopics && selectedTopicForHistory == null && selectedTopicForChat == null && selectedTab != 0) {
        viewModel.selectTab(0)
    }

    val showBottomBar = !isManagingTopics && selectedTopicForChat == null && selectedTopicForHistory == null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_navigation_bar"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Screen.items.forEachIndexed { index, screen ->
                        val isSelected = selectedTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                viewModel.selectTab(index)
                                if (index != 1) viewModel.closeTopicHistory()
                                if (index != 2) viewModel.closeTopicChat()
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_tab_${screen.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isManagingTopics) {
                AddTopicScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.setManagingTopics(false) }
                )
            } else {
                when (selectedTab) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToSettings = { viewModel.selectTab(3) },
                        onNavigateToTopics = { viewModel.setManagingTopics(true) },
                        onNavigateToAnalytics = { viewModel.selectTab(1) }
                    )
                    1 -> AnalyticsScreen(viewModel = viewModel)
                    2 -> AskTopicScreen(viewModel = viewModel)
                    3 -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
