package com.pashaoleynik97.droiddeploysdk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pashaoleynik.droiddeploy.DroidDeploy
import com.pashaoleynik.droiddeploy.install.DroidInstallState
import com.pashaoleynik.droiddeploy.updates.FetchResult
import com.pashaoleynik97.droiddeploysdk.ui.theme.DroidDeployTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroidDeployTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    UpdateScreen(
                        activity = this,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun UpdateScreen(activity: MainActivity, modifier: Modifier = Modifier) {
    val updateState by DroidDeploy.updates.collectAsState()
    val installState by DroidDeploy.installState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "DroidDeploy SDK Demo",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Update Status Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Update Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("Installed Version: ${updateState.installedVersionCode ?: "Unknown"}")
                Text("Latest Version: ${updateState.latest?.versionCode ?: "Unknown"}")
                Text("Latest Version Name: ${updateState.latest?.versionName ?: "Unknown"}")

                Spacer(modifier = Modifier.height(8.dp))

                if (updateState.available) {
                    Text(
                        text = "✅ Update Available!",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall
                    )
                } else {
                    Text(
                        text = "✓ Up to date",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                updateState.lastCheckedAtMillis?.let {
                    Text("Last checked: ${java.text.SimpleDateFormat.getInstance().format(it)}")
                }

                updateState.error?.let { error ->
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Install Status Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Installation Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (val state = installState) {
                    is DroidInstallState.Idle -> Text("Idle")
                    is DroidInstallState.Preparing -> {
                        Text("Preparing...")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is DroidInstallState.Downloading -> {
                        val percent = state.progressPercent
                        if (percent != null) {
                            Text("Downloading: $percent%")
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("Downloading: ${state.bytesRead} bytes")
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is DroidInstallState.Downloaded -> Text("Downloaded: ${state.file.name}")
                    is DroidInstallState.Installing -> {
                        Text("Installing...")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is DroidInstallState.Installed -> Text("✅ Installed! Please restart the app.")
                    is DroidInstallState.Cancelled -> Text("⚠️ Cancelled: ${state.reason}")
                    is DroidInstallState.Failed -> Text("❌ Failed: ${state.error}")
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    activity.lifecycleScope.launch {
                        when (val result = DroidDeploy.forceFetch()) {
                            is FetchResult.Success -> {}
                            is FetchResult.InProgress -> {}
                            is FetchResult.Failed -> {}
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Check for Updates")
            }

            Button(
                onClick = {
                    DroidDeploy.installLatest(activity)
                },
                modifier = Modifier.weight(1f),
                enabled = updateState.available && installState is DroidInstallState.Idle
            ) {
                Text("Install Update")
            }
        }
    }
}
