package uk.ac.cardiff.trainerhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import uk.ac.cardiff.trainerhub.ui.TrainerHubApp
import uk.ac.cardiff.trainerhub.ui.theme.TrainerHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as TrainerHubApplication).appContainer

        setContent {
            TrainerHubTheme {
                val darkTheme = isSystemInDarkTheme()
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                TrainerHubApp(
                    mobileRepository = container.mobileRepository,
                    reminderScheduler = container.reminderScheduler,
                )
            }
        }
    }
}
