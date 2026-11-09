package com.jaywebtech.autoshorts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.jaywebtech.autoshorts.ui.AutoShortsApp
import com.jaywebtech.autoshorts.ui.AutoShortsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoShortsTheme {
                AutoShortsApp(viewModel)
            }
        }
    }
}