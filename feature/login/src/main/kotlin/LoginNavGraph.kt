
package com.github.whitescent.mastify.feature.login

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.github.whitescent.mastify.core.navigation.Route

fun NavGraphBuilder.loginNavGraph(
  navController: NavController,
) = composable<Route.Login>(
  deepLinks = listOf(
    navDeepLink {
      uriPattern = "mastify://oauth?code={code}"
    }
  )
) {
  Login(navController)
}
