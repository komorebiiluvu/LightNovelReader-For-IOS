package indi.dmzz_yyhyy.lightnovelreader.ui.home.reading.stats

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import io.nightfish.lightnovelreader.api.ui.LocalNavController
import io.nightfish.lightnovelreader.api.Route
import indi.dmzz_yyhyy.lightnovelreader.utils.isResumed
import indi.dmzz_yyhyy.lightnovelreader.utils.popBackStackIfResumed

fun NavGraphBuilder.readingStatsNavigation() {
    navigation<Route.Main.Reading.Stats>(
        startDestination = Route.Main.Reading.Stats.Overview
    ) {
        readingStatsOverviewDestination()
    }
}

fun NavGraphBuilder.readingStatsOverviewDestination() {
    composable<Route.Main.Reading.Stats.Overview> {
        val navController = LocalNavController.current
        val statsOverviewViewModel = hiltViewModel<StatsOverviewViewModel>()
        StatsOverviewScreen(
            onClickBack = navController::popBackStackIfResumed,
            viewModel = statsOverviewViewModel,
        )
    }
}

fun NavController.navigateToReadingStatsDestination() {
    if (!this.isResumed()) return
    navigate(Route.Main.Reading.Stats)
}
