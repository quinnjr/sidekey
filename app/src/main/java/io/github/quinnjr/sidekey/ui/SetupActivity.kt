package io.github.quinnjr.sidekey.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.quinnjr.sidekey.ui.theme.SideKeyTheme

class SetupActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SideKeyTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                if (state.showReport) {
                    ReportScreen(state = state, viewModel = viewModel)
                } else {
                    SetupScreen(state = state, viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted the permission or started Shizuku while we were away.
        viewModel.refresh()
    }
}
