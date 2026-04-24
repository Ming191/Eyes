package com.example.eyes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.eyes.ui.navigation.AppNavGraph
import com.example.eyes.ui.theme.EyesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EyesTheme {
                AppNavGraph()
            }
        }
    }
}
