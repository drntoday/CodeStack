package com.somnath.codestack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // This is the "Theme" (Colors and Styles)
            MaterialTheme {
                // A surface is like a piece of paper the app is drawn on
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // This is the actual text on the screen
                    Text(
                        text = "Welcome to CodeStack, Somnath!",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }
    }
}
