# DroidDeploy SDK

DroidDeploy is an Android client SDK for managing app updates from a self-hosted DroidDeploy service.

## Features

- Periodic update checking
- APK download with progress tracking
- Automatic installation using Android PackageInstaller
- State-based reactive API using StateFlow
- API key authentication with automatic token refresh
- Configurable logging
- No external DI framework dependencies

## Requirements

- Android API 29+ (Android 10+)
- Kotlin with Coroutines support

## Installation

Add the library module to your project:

```gradle
dependencies {
    implementation(project(":droiddeploy"))
}
```

## Usage

### 1. Initialize in Application.onCreate()

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        DroidDeploy.init(this) {
            setApiKey("your-api-key")
            setApplicationId("your-app-id-from-server")
            setHost("https://your-droiddeploy-server.com")
            setFetchInterval(TimeUnit.MINUTES.toMillis(30))

            // Optional: Enable debug logging
            setDebugLogsEnabled(true)
            setDebugLogsListener { log ->
                Log.d(log.tag, log.message, log.throwable)
            }

            // Optional: Custom version code provider
            setVersionCodeProvider { context ->
                // Return your custom version code logic
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .longVersionCode
            }
        }
    }
}
```

### 2. Display Update Status in UI

```kotlin
@Composable
fun UpdateScreen() {
    val updateState by DroidDeploy.updates.collectAsState()
    val installState by DroidDeploy.installState.collectAsState()

    Column {
        // Show update availability
        if (updateState.available) {
            Text("Update available: ${updateState.latest?.versionName}")
            Button(onClick = {
                DroidDeploy.installLatest(activity)
            }) {
                Text("Install Update")
            }
        } else {
            Text("You're up to date!")
        }

        // Show installation progress
        when (val state = installState) {
            is DroidInstallState.Idle -> {}
            is DroidInstallState.Preparing -> {
                Text("Preparing...")
            }
            is DroidInstallState.Downloading -> {
                Text("Downloading: ${state.progressPercent ?: "Unknown"}%")
                LinearProgressIndicator(
                    progress = (state.progressPercent ?: 0) / 100f
                )
            }
            is DroidInstallState.Downloaded -> {
                Text("Downloaded")
            }
            is DroidInstallState.Installing -> {
                Text("Installing...")
            }
            is DroidInstallState.Installed -> {
                Text("Installed! Restart the app.")
            }
            is DroidInstallState.Cancelled -> {
                Text("Installation cancelled: ${state.reason}")
            }
            is DroidInstallState.Failed -> {
                Text("Installation failed: ${state.error}")
            }
        }

        // Error display
        updateState.error?.let { error ->
            Text("Error: $error", color = Color.Red)
        }
    }
}
```

### 3. Manual Update Check

```kotlin
lifecycleScope.launch {
    when (val result = DroidDeploy.forceFetch()) {
        is FetchResult.Success -> {
            // Update info fetched successfully
            val update = result.updated
            if (update.available) {
                // Show update prompt
            }
        }
        is FetchResult.InProgress -> {
            // Fetch already in progress
        }
        is FetchResult.Failed -> {
            // Handle error
            Log.e("Update", "Failed to check for updates: ${result.error}")
        }
    }
}
```

### 4. Install Latest Update

```kotlin
fun installUpdate(activity: Activity) {
    val options = InstallOptions(
        deleteApkAfterInstallAttempt = true,
        apkFileNamePrefix = "myapp"
    )

    DroidDeploy.installLatest(activity, options)
}
```

## API Reference

### DroidDeploy Object

#### Functions

- `init(context: Context, block: DroidDeployConfig.() -> Unit)` - Initialize the SDK (call once in Application.onCreate)
- `suspend fun forceFetch(): FetchResult` - Manually trigger an update check
- `fun installLatest(activity: Activity, options: InstallOptions = InstallOptions())` - Start download and installation

#### Properties

- `val updates: StateFlow<DroidUpdate>` - Observable update state
- `val installState: StateFlow<DroidInstallState>` - Observable installation state

### Models

#### DroidUpdate
```kotlin
data class DroidUpdate(
    val available: Boolean,
    val installedVersionCode: Long?,
    val latest: VersionDto?,
    val lastCheckedAtMillis: Long?,
    val error: DroidDeployError?
)
```

#### DroidInstallState
- `Idle` - No installation in progress
- `Preparing` - Preparing for download
- `Downloading(progressPercent, bytesRead, totalBytes)` - Download in progress
- `Downloaded(file)` - Download complete
- `Installing` - Installation in progress
- `Installed` - Installation complete
- `Cancelled(reason)` - Installation cancelled
- `Failed(error)` - Installation failed

#### InstallOptions
```kotlin
data class InstallOptions(
    val deleteApkAfterInstallAttempt: Boolean = true,
    val apkFileNamePrefix: String = "droiddeploy"
)
```

## Permissions

The SDK requires the following permissions (automatically merged from library manifest):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

## Architecture

The SDK uses a clean architecture with the following layers:

- **Public API** - `DroidDeploy` object (entry point)
- **Domain** - Update logic, state management
- **Network** - Retrofit + OkHttp with custom interceptors and authenticator
- **DI** - Lightweight internal service locator pattern

## License

Apache 2.0
