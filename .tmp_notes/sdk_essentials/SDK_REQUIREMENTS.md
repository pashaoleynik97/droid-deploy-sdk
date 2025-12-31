You are implementing an Android client SDK for the DroidDeploy service.

TARGET
- Android 10+ (minSdk 29).
- Kotlin, Coroutines, StateFlow.
- Retrofit + OkHttp networking.
- No external DI frameworks (NO Dagger/Hilt/Koin). Use an internal lightweight container/service-locator.
- Token persistence: in-memory only.
- Periodic update checking: only while app process is alive (no WorkManager).
- Provide ability to check updates (for UI) AND download+install latest stable APK (with progress + cancel reporting).

SERVICE API (consumer):
1) POST /api/v1/auth/apikey
   request body: { "apiKey": "..." }
   response body: RestResponse<ApiTokenDto>
   ApiTokenDto: { accessToken: String }

2) GET /api/v1/application/{applicationId}/version/latest
   response body: RestResponse<VersionDto>
   VersionDto: { versionName: String?, versionCode: Long?, stable: Boolean }

3) GET /api/v1/application/{applicationId}/version/{versionCode}/apk
   response: binary APK stream (application/octet-stream, etc.)

LIBRARY PUBLIC API
Create a single public entry point: object DroidDeploy

Initialization (called once in Application.onCreate):
DroidDeploy.init(applicationContext) {
setApiKey("SOME")
setApplicationId("my-app-id")
setHost("http://myhost.com")               // base url
setFetchInterval(TimeUnit.MINUTES.toMillis(30))

setDebugLogsEnabled(true)
setDebugLogsListener { log: DroidDeployLog ->
println(log)
}

// optional override for tests / advanced cases:
setVersionCodeProvider { ctx: Context -> Long? }
}

Public flows and methods:
- val updates: StateFlow<DroidUpdate>
  UI-oriented snapshot. The app will collect it to display “update available”.

- suspend fun forceFetch(): FetchResult
  Immediately triggers update check outside interval.

- val installState: StateFlow<DroidInstallState>
  Hot state machine for download+install progress.

- fun installLatest(activity: Activity, options: InstallOptions = InstallOptions())
  Starts download+install flow for latest stable version (user initiated).
  MUST reject if an install is already in progress.

CONCURRENCY RULES
- If a fetch is already executing, new fetch triggers (interval or forceFetch) must be ignored or return a result indicating “in progress”.
- If installLatest is called while an install is executing, reject with DroidInstallState.Failed(AlreadyInProgress).

MODELS (SDK domain)
DroidUpdate (UI snapshot):
- val available: Boolean
- val installedVersionCode: Long?
- val latest: VersionDto?              // include server DTO or map to domain
- val lastCheckedAtMillis: Long?
- val error: DroidDeployError?         // optional; null when OK

FetchResult:
- Success(updated: DroidUpdate)
- InProgress
- Failed(error: DroidDeployError)

InstallOptions:
- val deleteApkAfterInstallAttempt: Boolean = true
- val apkFileNamePrefix: String = "droiddeploy"
  (Keep minimal; add more only if needed.)

DroidInstallState (hot state machine):
- Idle
- Preparing
- Downloading(
  progressPercent: Int?,            // null if total unknown
  bytesRead: Long,
  totalBytes: Long?
  )
- Downloaded(file: File)
- Installing
- Installed
- Cancelled(reason: String? = null)   // map system cancel/abort to this state
- Failed(error: DroidDeployError)

Error model:
sealed class DroidDeployError {
data class Network(val throwable: Throwable) : DroidDeployError()
data class Http(val code: Int, val message: String?, val apiErrors: List<String>) : DroidDeployError()
data class Serialization(val throwable: Throwable) : DroidDeployError()
data class IllegalState(val message: String) : DroidDeployError()
data class AlreadyInProgress(val operation: String) : DroidDeployError()
}

LOGGING
- Provide a small logging abstraction:
  data class DroidDeployLog(level: Level, tag: String, message: String, throwable: Throwable? = null)
- Debug logs can be enabled/disabled via config.
- When enabled: OkHttp logging interceptor + internal logs go to listener.

VERSION CODE PROVIDER (Android 10+)
- Provide default: use PackageManager to get installed versionCode (Long).
  For API 29+, PackageInfo.longVersionCode.
- Allow override via config (VersionCodeProvider = (Context) -> Long?).

UPDATE DECISION LOGIC
- If server versionCode == null OR installedVersionCode == null => available = false, but store error? No, keep available false and keep latest in snapshot for debugging.
- available = (serverVersionCode > installedVersionCode)
- latest endpoint returns “latest stable”; assume stable=true but don’t rely on it.

NETWORK LAYER REQUIREMENTS
Use Retrofit + OkHttp with the following components:

1) HostSelectionInterceptor
- Base URL is configurable at SDK init.
- Interceptor must rewrite request URL host/scheme/port according to current host setting.
- Provide thread-safe host store (AtomicReference<HttpUrl>).

2) LoggingInterceptor
- Enabled only when debug logs enabled.
- Either OkHttp HttpLoggingInterceptor or custom logger that routes to DroidDeployLog listener.

3) Signing/Authorization Interceptor
- For all requests EXCEPT /api/v1/auth/*, add:
  Authorization: Bearer <accessToken>
- Token comes from in-memory TokenStore.
- If no token yet, do not set header; rely on Authenticator to handle 401.

4) OkHttp Authenticator (retry mechanism)
- When server returns 401 on non-auth request:
    - Call POST /api/v1/auth/apikey with API key to obtain new access token.
    - Store token in TokenStore (in-memory).
    - Retry the original request once with new Authorization header.
- Must avoid infinite retry loops:
    - If request already has a marker header like "X-DroidDeploy-Retry: 1", do not retry again.
    - Never authenticate requests targeting /api/v1/auth/*.
- Must be single-flight: if multiple requests hit 401 concurrently, only one token request should execute and others should wait for the result (Mutex + shared deferred).

IMPORTANT: Retrofit instance should use the OkHttpClient with all interceptors + authenticator set.

REST RESPONSE ENVELOPE
Most endpoints return:
RestResponse<T> { data: T?; message: String?; errors: List<String>; success: Boolean }
Implement:
data class RestResponse<T>(val data: T?, val message: String?, val errors: List<String> = emptyList(), val success: Boolean)

On non-2xx responses, parse error body if possible; if not, set generic message.

INSTALLATION (DOWNLOAD + SYSTEM INSTALL)
We must provide installLatest(activity) that:
1) Reads latest version snapshot (or calls fetch if needed).
2) Downloads APK to a temporary file in easiest location:
   context.cacheDir / "droiddeploy" / "<prefix>-<applicationId>-<versionCode>.apk"
   Ensure directory exists.
3) Emits installState transitions:
   Preparing -> Downloading(progress...) -> Downloaded(file) -> Installing -> Installed/Cancelled/Failed

Download implementation:
- Use OkHttp call (Retrofit can return ResponseBody) and stream to file.
- Emit progress updates (bytesRead + totalBytes from Content-Length if present).

Install implementation:
- Use Android PackageInstaller session API (API 29+).
- Steps:
  a) val packageInstaller = context.packageManager.packageInstaller
  b) Create session with SessionParams(MODE_FULL_INSTALL)
  c) Open OutputStream via session.openWrite("base.apk", 0, -1)
  d) Copy file bytes into session stream (or stream directly from download to session, but simplest is download->file then file->session)
  e) session.fsync(out)
  f) Commit using PendingIntent IntentSender.
  g) Receive status result in a BroadcastReceiver registered in manifest by the library.
- Map statuses:
    - STATUS_SUCCESS => Installed
    - STATUS_FAILURE / STATUS_FAILURE_ABORTED / etc. => Failed or Cancelled depending on status
    - If user cancels in UI => Cancelled
- After completion: if deleteApkAfterInstallAttempt=true, delete temp file.

BroadcastReceiver:
- Provide a receiver class (e.g., DroidDeployInstallReceiver) in library manifest (consumer app merges it).
- It should forward results into the Installer component (e.g., via a static callback registry or shared StateFlow in singleton).
- Ensure it works without DI frameworks.

STATE MANAGEMENT
- DroidDeploy has internal coroutine scope (SupervisorJob + Dispatchers.IO).
- updates: MutableStateFlow<DroidUpdate> with initial state:
  available=false, installedVersionCode=defaultProvider(context), latest=null, lastCheckedAt=null, error=null
- installState: MutableStateFlow<DroidInstallState> initial Idle.

Periodic fetch:
- Start after init() in a coroutine:
    - ticker loop (delay fetchInterval)
    - call fetchLatest() protected by Mutex to avoid concurrent fetch
- forceFetch():
    - triggers immediate fetch (also protected by Mutex)
    - returns FetchResult

REJECT CONCURRENT INSTALLS
- installLatest() uses a Mutex or AtomicBoolean to ensure only one install at a time.
- If already running, set installState = Failed(AlreadyInProgress("install")) and return.

NO EXTERNAL DI - INTERNAL CONTAINER EXAMPLE
Implement a tiny internal container, something like:

class SdkContainer(private val appContext: Context, private val config: DroidDeployConfig) {
val logger: Logger = Logger(config.debugLogsEnabled, config.debugLogsListener)

val hostStore = HostStore(config.host)                // AtomicReference<HttpUrl>
val tokenStore = InMemoryTokenStore()

val okHttpClient: OkHttpClient = OkHttpClient.Builder()
.addInterceptor(HostSelectionInterceptor(hostStore))
.addInterceptor(AuthHeaderInterceptor(tokenStore))
.apply { if (config.debugLogsEnabled) addInterceptor(OkHttpLogging(logger)) }
.authenticator(ApiKeyAuthenticator(
baseUrlProvider = { hostStore.get() },
apiKeyProvider = { config.apiKey },
tokenStore = tokenStore,
logger = logger
))
.build()

val retrofit: Retrofit = Retrofit.Builder()
.baseUrl(hostStore.get())
.client(okHttpClient)
.addConverterFactory(MoshiConverterFactory.create()) // or kotlinx.serialization
.build()

val api: DroidDeployApi = retrofit.create(DroidDeployApi::class.java)

val versionCodeProvider: VersionCodeProvider = config.versionCodeProvider ?: DefaultVersionCodeProvider()

val updatesRepository = UpdatesRepository(
appContext = appContext,
api = api,
applicationIdProvider = { config.applicationId },
versionCodeProvider = { versionCodeProvider(appContext) },
logger = logger,
fetchIntervalMillis = config.fetchIntervalMillis
)

val downloader = ApkDownloader(appContext, api, logger)
val installer = PackageInstallerInstaller(appContext, logger) // uses receiver mechanism

val updateManager = UpdateManager(updatesRepository, downloader, installer, logger)
}

Also implement the “service locator” in DroidDeploy:
object DroidDeploy {
private var container: SdkContainer? = null
fun init(context: Context, block: DroidDeployConfig.() -> Unit) { ... create config ... container = SdkContainer(...) ... }
val updates: StateFlow<DroidUpdate> get() = requireContainer().updateManager.updates
val installState: StateFlow<DroidInstallState> get() = requireContainer().updateManager.installState
suspend fun forceFetch(): FetchResult = requireContainer().updateManager.forceFetch()
fun installLatest(activity: Activity, options: InstallOptions) = requireContainer().updateManager.installLatest(activity, options)
}

Ensure this works without any DI framework.

PACKAGE STRUCTURE
Use a clean structure:
com.pashaoleynik.droiddeploy
- DroidDeploy.kt
- DroidDeployConfig.kt
- logs/
- di/
- network/
- updates/
- install/
- errors/

DELIVERABLES
1) Production-ready Kotlin source code for the SDK, no DI frameworks.
2) README usage example showing init + collecting updates + calling installLatest.
3) Unit tests:
    - Version comparator logic.
    - Update decision logic (available true/false).
    - (Optional) token retry guard logic (can be tested with fake authenticator).
4) Ensure no background tasks when app is killed (only process alive).

IMPLEMENTATION NOTES / ACCEPTANCE
- Host can be changed only during init; dynamic change support is optional unless trivial with hostStore.
- Must not crash if init is called twice: either no-op or throw IllegalState with clear message.
- Must ensure that logging does not leak sensitive tokens (redact Authorization header).

Now implement the library according to this plan.

### AI Logging

Log your work steps to `.tmp_notes/sdk_essentials`. Including your planning, decisions, challenges, and solutions.
You can split logging into separate files if needed. The goal is to have ability to proceed implementation in multiple sessions while keeping context.

### Detailed svc API specs
Detailed specifications for the API endpoints could be found in `.tmp_notes/sdk_essentials/open-api-spec.json`
Note, that description in the spec may be not up to date, so rely on requirements in this file and on raw models structure defined in json file.