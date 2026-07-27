package io.github.quinnjr.sidekey.ui

import android.content.Intent
import androidx.core.net.toUri
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.quinnjr.sidekey.BuildConfig
import io.github.quinnjr.sidekey.report.FixOutcome
import io.github.quinnjr.sidekey.report.IssueUrl
import io.github.quinnjr.sidekey.report.IssueUrlBuilder

private val OUTCOMES = listOf(
    FixOutcome.Worked to "It worked — I get the power menu",
    FixOutcome.DidNotWork to "It did not work",
    FixOutcome.NotTried to "I have not tried yet",
)

/**
 * Two explicit consent gates: this preview, then GitHub's own compose screen. Nothing is
 * ever sent in the background or on first launch.
 */
@Composable
fun ReportScreen(state: SetupUiState, viewModel: SetupViewModel) {
    val context = LocalContext.current
    val report = remember(state.outcome, state.observed) { viewModel.report() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Report my device", style = MaterialTheme.typography.headlineMedium)
            Text(
                "101 is only known to mean \"launch Bixby\" on one model and one One UI version. " +
                    "Reports build the value table for everyone else.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Did the fix work?", style = MaterialTheme.typography.titleMedium)
                    OUTCOMES.forEach { (outcome, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.outcome == outcome,
                                    onClick = { viewModel.setOutcome(outcome) },
                                ),
                        ) {
                            RadioButton(
                                selected = state.outcome == outcome,
                                onClick = { viewModel.setOutcome(outcome) },
                            )
                            Text(label)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Exactly what will be sent", style = MaterialTheme.typography.titleMedium)
                    Mono(report.toMarkdown())
                    Text(
                        "No serial, IMEI, advertising or Android ID, accounts, installed apps, or location.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val target = when (val url = IssueUrlBuilder.build(BuildConfig.REPORT_REPO, report)) {
                        is IssueUrl.Ready -> url.uri
                        is IssueUrl.TooLong -> {
                            context.copyToClipboard("device report", url.body)
                            url.blankIssueUri
                        }
                    }
                    context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
                },
            ) {
                Text("Open GitHub issue")
            }

            TextButton(onClick = viewModel::closeReport, modifier = Modifier.fillMaxWidth()) {
                Text("Back")
            }
        }
    }
}
