package com.andrewmacheret.i5.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.andrewmacheret.i5.Lanes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            I5Theme {
                DetailScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app is a good moment to check WSDOT, unless it just did
        Lanes.refreshInBackground(opening = true)
    }
}
