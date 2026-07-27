package io.github.quinnjr.sidekey.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.quinnjr.sidekey.bootstrap.BootstrapState
import io.github.quinnjr.sidekey.bootstrap.Bootstrapper
import io.github.quinnjr.sidekey.core.Behavior
import io.github.quinnjr.sidekey.core.WriteResult

private val PRESETS = listOf(
    Behavior.PowerMenu to "Power menu",
    Behavior.Assistant to "Assistant",
    Behavior.Nothing to "Nothing",
    Behavior.SamsungAi to "Samsung AI (restore default)",
)

@Composable
fun SetupScreen(state: SetupUiState, viewModel: SetupViewModel) {
    val context = LocalContext.current
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("SideKey", style = MaterialTheme.typography.headlineMedium)

            StatusCard(state)
            WizardCard(state, viewModel, context)
            BehaviorCard(state, viewModel)
            ResultCard(state)

            Button(onClick = viewModel::applyNow, modifier = Modifier.fillMaxWidth()) {
                Text("Fix now")
            }
            OutlinedButton(onClick = viewModel::openReport, modifier = Modifier.fillMaxWidth()) {
                Text("Report my device")
            }
        }
    }
}

@Composable
private fun StatusCard(state: SetupUiState) = Section("Status") {
    Mono("power_button_long_press = ${state.observed?.pblp ?: "unset"}")
    Mono("wanted                  = ${state.desired.pblp}")
    val matches = state.observed?.pblp == state.desired.pblp
    Text(
        if (matches) "Matches — the side key is doing what you asked."
        else "Does not match — press Fix now.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        when (state.bootstrap) {
            BootstrapState.Granted -> "Permission held. Nothing else is needed, ever."
            BootstrapState.ShizukuReady -> "Shizuku is ready — run the one-time grant below."
            BootstrapState.ShizukuMissing -> "No permission, and Shizuku is not running."
            BootstrapState.ShizukuDenied -> "Shizuku is running but has not authorised SideKey."
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun WizardCard(state: SetupUiState, viewModel: SetupViewModel, context: Context) {
    if (state.bootstrap == BootstrapState.Granted) return
    Section("Setup") {
        when (state.bootstrap) {
            BootstrapState.ShizukuReady -> {
                Text("Shizuku is authorised. This grants SideKey the permission permanently — afterwards Shizuku can be uninstalled.")
                Button(onClick = viewModel::selfGrant) { Text("Grant permission via Shizuku") }
            }

            BootstrapState.ShizukuDenied -> {
                Text("Shizuku is running but has not authorised SideKey yet.")
                Button(onClick = viewModel::requestShizukuPermission) { Text("Ask Shizuku for access") }
            }

            else -> Text(
                "Either start Shizuku (no PC needed: enable Wireless debugging, then pair Shizuku on-device), " +
                    "or run the adb command below once from a computer."
            )
        }

        val adb = "adb shell ${Bootstrapper.grantCommand(context.packageName)}"
        Mono(adb)
        TextButton(onClick = { context.copyToClipboard("adb command", adb) }) {
            Text("Copy adb command")
        }
        Text(
            "There is no path without developer options — WRITE_SECURE_SETTINGS is " +
                "signature|privileged|development, so no app can obtain it otherwise.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BehaviorCard(state: SetupUiState, viewModel: SetupViewModel) = Section("Long-press does") {
    PRESETS.forEach { (behavior, label) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = state.desired.pblp == behavior.pblp,
                    onClick = { viewModel.choose(behavior) },
                ),
        ) {
            RadioButton(
                selected = state.desired.pblp == behavior.pblp,
                onClick = { viewModel.choose(behavior) },
            )
            Text("$label  (${behavior.pblp})")
        }
    }
    OutlinedTextField(
        value = state.rawInput,
        onValueChange = viewModel::onRawInput,
        label = { Text("Raw value for other devices") },
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = viewModel::applyRaw) { Text("Apply raw value") }
}

@Composable
private fun ResultCard(state: SetupUiState) {
    val result = state.lastResult ?: return
    Section("Last write") {
        Text(
            when (result) {
                WriteResult.Ok -> "Written and verified."
                WriteResult.NoPermission -> "Refused: SideKey does not hold WRITE_SECURE_SETTINGS."
                is WriteResult.Rejected -> "Rejected: ${result.reason}"
                is WriteResult.Overridden -> "Written, but the system immediately set it back to ${result.observed}."
            }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun Mono(text: String) = Text(
    text = text,
    fontFamily = FontFamily.Monospace,
    style = MaterialTheme.typography.bodySmall,
)

internal fun Context.copyToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, text))
}
