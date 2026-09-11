package com.satpam.piket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.satpam.piket.ui.screen.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "splash"
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onNavigateToDashboard = {
                                    navController.navigate("dashboard") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("dashboard") {
                            DashboardScreen(
                                onNavigateToFaceLogin = { navController.navigate("face_login") },
                                onNavigateToHistory = { navController.navigate("history") },
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }

                        composable("face_login") {
                            FaceLoginScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onCheckInSuccess = { absensiId ->
                                    navController.navigate("checkin_success/$absensiId") {
                                        popUpTo("dashboard")
                                    }
                                }
                            )
                        }

                        composable(
                            route = "checkin_success/{absensiId}",
                            arguments = listOf(navArgument("absensiId") { type = NavType.StringType })
                        ) { backStack ->
                            val absensiId = backStack.arguments?.getString("absensiId") ?: ""
                            CheckInSuccessScreen(
                                absensiId = absensiId,
                                onNavigateHome = {
                                    navController.navigate("dashboard") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("history") {
                            HistoryScreen(onNavigateBack = { navController.popBackStack() })
                        }

                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToEnrolFace = { nama, jabatan, lokasi ->
                                    val encodedNama    = java.net.URLEncoder.encode(nama, "UTF-8")
                                    val encodedJabatan = java.net.URLEncoder.encode(jabatan, "UTF-8")
                                    val encodedLokasi  = java.net.URLEncoder.encode(lokasi, "UTF-8")
                                    navController.navigate("enrol_face/$encodedNama/$encodedJabatan/$encodedLokasi")
                                }
                            )
                        }

                        composable(
                            route = "enrol_face/{nama}/{jabatan}/{lokasi}",
                            arguments = listOf(
                                navArgument("nama")    { type = NavType.StringType },
                                navArgument("jabatan") { type = NavType.StringType },
                                navArgument("lokasi")  { type = NavType.StringType }
                            )
                        ) { backStack ->
                            val nama    = java.net.URLDecoder.decode(backStack.arguments?.getString("nama")    ?: "", "UTF-8")
                            val jabatan = java.net.URLDecoder.decode(backStack.arguments?.getString("jabatan") ?: "", "UTF-8")
                            val lokasi  = java.net.URLDecoder.decode(backStack.arguments?.getString("lokasi")  ?: "", "UTF-8")
                            EnrolFaceScreen(
                                nama = nama,
                                jabatan = jabatan,
                                lokasi = lokasi,
                                onNavigateBack = { navController.popBackStack() },
                                onEnrolSuccess = {
                                    navController.popBackStack("settings", inclusive = false)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
