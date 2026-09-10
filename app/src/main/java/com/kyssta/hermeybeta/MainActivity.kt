package com.kyssta.hermeybeta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kyssta.hermeybeta.navigation.AppNavigation
import com.kyssta.hermeybeta.ui.theme.HermeyBetaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HermeyBetaTheme {
                AppNavigation()
            }
        }
    }
}
