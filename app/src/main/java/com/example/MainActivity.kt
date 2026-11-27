package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.configureAppCheck
import com.example.ui.GrassSshApp
import com.example.ui.theme.GrassSshTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
  private val appContainer by lazy { AppContainer(applicationContext) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
    configureAppCheck(this, intent)

    enableEdgeToEdge()
    setContent {
      GrassSshTheme {
        val mainViewModel: MainViewModel = viewModel(
          factory = MainViewModel.factory(appContainer),
        )
        GrassSshApp(viewModel = mainViewModel)
      }
    }
  }
}
