package com.joshuadiablito.mythos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.joshuadiablito.mythos.Mythos
import com.joshuadiablito.mythos.Router
import com.joshuadiablito.mythos.model.Language
import com.joshuadiablito.mythos.ui.library.LibraryScreen
import com.joshuadiablito.mythos.ui.onboarding.OnboardingScreen
import com.joshuadiablito.mythos.ui.reader.StoryReaderScreen
import com.joshuadiablito.mythos.ui.settings.SettingsScreen
import com.joshuadiablito.mythos.ui.theme.Theme
import com.joshuadiablito.mythos.ui.today.TodayScreen
import com.joshuadiablito.mythos.ui.words.FlashcardReview
import com.joshuadiablito.mythos.ui.words.WordsScreen

/** Opens screens that sit above the tabs. */
class Navigator(private val nav: NavHostController) {
    fun openStory(id: String) = nav.navigate("story/$id") { launchSingleTop = true }
    fun review(language: Language) = nav.navigate("review/${language.code}")
    fun back() { nav.popBackStack() }
}

@Composable
fun MythosApp() {
    val settings = Mythos.settings
    AnimatedContent(settings.hasOnboarded, label = "onboarding") { onboarded ->
        if (!onboarded) OnboardingScreen() else MainNavigation()
    }
}

@Composable
private fun MainNavigation() {
    val nav = rememberNavController()
    val navigator = Navigator(nav)

    // A tapped notification: show its story over Today.
    LaunchedEffect(Router.pendingStoryID) {
        val id = Router.pendingStoryID ?: return@LaunchedEffect
        if (Mythos.catalog.story(id) != null) {
            nav.popBackStack("tabs", inclusive = false)
            navigator.openStory(id)
        }
        Router.pendingStoryID = null
    }

    NavHost(
        nav, startDestination = "tabs",
        enterTransition = { slideInHorizontally { it } + fadeIn() },
        exitTransition = { slideOutHorizontally { -it / 4 } + fadeOut() },
        popEnterTransition = { slideInHorizontally { -it / 4 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it } + fadeOut() },
    ) {
        composable("tabs") { Tabs(navigator) }
        composable("story/{id}") { entry ->
            val story = entry.arguments?.getString("id")?.let { Mythos.catalog.story(it) }
            if (story != null) StoryReaderScreen(story, onBack = navigator::back)
        }
        composable("review/{language}") { entry ->
            val language = entry.arguments?.getString("language")?.let { Language.fromCode(it) }
            if (language != null) FlashcardReview(language, onClose = navigator::back)
        }
    }
}

@Composable
private fun Tabs(navigator: Navigator) {
    val holder = rememberSaveableStateHolder()
    val c = Theme.colors
    Scaffold(
        containerColor = c.paper,
        bottomBar = {
            NavigationBar(containerColor = c.card) {
                val items = listOf(
                    Triple(Router.Tab.TODAY, "Today", Icons.Rounded.WbTwilight),
                    Triple(Router.Tab.LIBRARY, "Stories", Icons.Rounded.LibraryBooks),
                    Triple(Router.Tab.WORDS, "Words", Icons.Rounded.Bookmark),
                    Triple(Router.Tab.SETTINGS, "Settings", Icons.Rounded.Settings),
                )
                for ((tab, label, icon) in items) {
                    NavigationBarItem(
                        selected = Router.selectedTab == tab,
                        onClick = { Router.selectedTab = tab },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accent, selectedTextColor = c.accent,
                            indicatorColor = c.accent.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        // Only the tab bar's inset is taken here; each screen pads for the status bar itself.
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        Box(Modifier.fillMaxSize().padding(bottom).consumeWindowInsets(bottom)) {
            val tab = Router.selectedTab
            holder.SaveableStateProvider(tab.name) {
                when (tab) {
                    Router.Tab.TODAY -> TodayScreen(navigator)
                    Router.Tab.LIBRARY -> LibraryScreen(navigator)
                    Router.Tab.WORDS -> WordsScreen(navigator)
                    Router.Tab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}
