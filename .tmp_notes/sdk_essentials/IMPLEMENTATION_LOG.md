# DroidDeploy SDK Implementation Log

## Session Date: 2026-01-05

### Overview
This document tracks the implementation of the DroidDeploy Android client SDK, a library for managing app updates from a self-hosted DroidDeploy service.

### Requirements Summary
- **Target**: Android 10+ (minSdk 29)
- **Tech Stack**: Kotlin, Coroutines, StateFlow, Retrofit + OkHttp
- **Constraints**: No external DI frameworks, in-memory token storage only, periodic updates only while app is alive
- **Features**: Update checking, APK download with progress, automatic installation

---

## Implementation Steps

### 1. Project Structure Setup
**Status**: ✅ Completed

**Actions**:
- Created new Android library module `droiddeploy`
- Updated `settings.gradle.kts` to include new module
- Created `build.gradle.kts` for library with necessary dependencies:
  - Retrofit 2.11.0
  - OkHttp 4.12.0
  - Moshi 1.15.1 (with code generation)
  - Coroutines 1.10.1
- Created AndroidManifest.xml with required permissions and BroadcastReceiver declaration
- Set up package structure: `errors/`, `logs/`, `network/`, `updates/`, `install/`, `di/`

**Decisions**:
- Chose Moshi over kotlinx.serialization for better Java interop and established ecosystem
- Used version catalog (libs.versions.toml) for centralized dependency management
- Organized code by feature/layer for better maintainability

---

### 2. Core Models & Error Handling
**Status**: ✅ Completed

**Implemented**:
- `DroidDeployError` - Sealed class hierarchy for error handling:
  - `Network(throwable)` - Network failures
  - `Http(code, message, apiErrors)` - HTTP errors with API error list
  - `Serialization(throwable)` - JSON parsing errors
  - `IllegalState(message)` - Invalid state errors
  - `AlreadyInProgress(operation)` - Concurrent operation prevention

**Decisions**:
- Used sealed classes for exhaustive when expressions
- Included API error list in HTTP errors for detailed server-side error reporting
- Separate error type for concurrent operation prevention

---

### 3. Logging System
**Status**: ✅ Completed

**Implemented**:
- `DroidDeployLog` - Data class with level, tag, message, throwable
- `Logger` - Internal logger with configurable listener
- Log levels: VERBOSE, DEBUG, INFO, WARN, ERROR
- Logs only emitted when debug mode is enabled

**Decisions**:
- Internal logger instead of Android Log for better testability
- User-provided listener pattern for flexibility
- No-op when debugging is disabled to minimize overhead

---

### 4. Network Layer - DTOs
**Status**: ✅ Completed

**Implemented**:
- `RestResponse<T>` - Generic wrapper for all API responses
- `VersionDto` - Version information from server
- `ApiTokenDto` - Authentication token response
- `ApiKeyLoginRequest` - API key login request body

**Decisions**:
- Used Moshi's `@JsonClass(generateAdapter = true)` for efficient JSON parsing
- Made DTOs nullable where appropriate based on OpenAPI spec
- Kept DTOs in network package, separate from domain models

---

### 5. Network Layer - Interceptors
**Status**: ✅ Completed

**Implemented**:
- `HostStore` - Thread-safe storage for configurable base URL (AtomicReference)
- `InMemoryTokenStore` - Thread-safe in-memory token storage
- `HostSelectionInterceptor` - Rewrites request URLs to configured host
- `AuthHeaderInterceptor` - Adds Authorization Bearer token (except for /auth endpoints)
- `OkHttpLoggingInterceptor` - Custom logging with sensitive data redaction

**Decisions**:
- AtomicReference for thread-safe state without locks
- Skip auth header for /api/v1/auth/* paths to avoid loops
- Redact Authorization header in logs for security
- Custom logging interceptor for better control over log format

**Challenges**:
- Had to ensure auth interceptor doesn't add headers to auth endpoints
- Needed to parse request path to determine if it's an auth endpoint

---

### 6. Network Layer - Authenticator
**Status**: ✅ Completed

**Implemented**:
- `ApiKeyAuthenticator` - Handles 401 responses with automatic token refresh
- Single-flight token refresh using Mutex
- Retry marker header to prevent infinite loops
- Synchronous token fetch within Authenticator's synchronous interface

**Decisions**:
- Used `runBlocking` in Authenticator since interface is synchronous
- Mutex ensures only one token refresh at a time (concurrent requests wait)
- Added `X-DroidDeploy-Retry` header to prevent double retry
- Don't retry auth endpoints or already-retried requests
- Created separate OkHttpClient for token fetch to avoid recursion

**Challenges**:
- Authenticator interface is synchronous, required runBlocking for coroutine
- Preventing infinite retry loops required careful header tracking
- Concurrent 401s need to share single token refresh (solved with Mutex)

---

### 7. Network Layer - Retrofit API
**Status**: ✅ Completed

**Implemented**:
- `DroidDeployApi` interface with 3 endpoints:
  1. `POST /api/v1/auth/apikey` - API key authentication
  2. `GET /api/v1/application/{applicationId}/version/latest` - Get latest version
  3. `GET /api/v1/application/{applicationId}/version/{versionCode}/apk` - Download APK

**Decisions**:
- Suspended functions for coroutine support
- Return `Response<T>` instead of direct T for better error handling
- Use `ResponseBody` for APK download to stream binary data

---

### 8. Domain Layer - Configuration & Providers
**Status**: ✅ Completed

**Implemented**:
- `DroidDeployConfig` - DSL-style configuration builder
- `VersionCodeProvider` - Typealias for (Context) -> Long? function
- `DefaultVersionCodeProvider` - Uses PackageManager.longVersionCode (API 29+)

**Decisions**:
- DSL-style config for fluent API
- Typealias for version code provider makes it easy to override
- Default provider handles API level differences (TIRAMISU vs earlier)

---

### 9. Domain Layer - Models
**Status**: ✅ Completed

**Implemented**:
- `DroidUpdate` - UI-oriented snapshot of update state
- `FetchResult` - Sealed class for fetch operation results
- `InstallOptions` - Configuration for installation
- `DroidInstallState` - Sealed class for installation state machine

**Decisions**:
- Sealed classes for type-safe state representation
- `DroidUpdate` includes error field for displaying fetch errors
- Progress in `Downloading` state includes percentage, bytes read, total bytes
- Separate `Cancelled` and `Failed` states for clear user feedback

---

### 10. Updates Repository
**Status**: ✅ Completed

**Implemented**:
- `UpdatesRepository` - Core update checking logic
- Periodic fetch using coroutine with delay loop
- Force fetch with Mutex for concurrency control
- Update availability logic: `serverVersionCode > installedVersionCode`
- StateFlow for reactive updates

**Decisions**:
- SupervisorJob + Dispatchers.IO for background work
- Mutex prevents concurrent fetches
- `tryLock()` for force fetch returns InProgress if already running
- Periodic fetch continues even after failures
- Update available only when both version codes are non-null

**Challenges**:
- Balancing automatic periodic checks with manual triggers
- Ensuring fetch mutex doesn't block periodic timer

---

### 11. APK Downloader
**Status**: ✅ Completed

**Implemented**:
- `ApkDownloader` - Streams APK from server to cache directory
- Progress tracking with bytesRead and totalBytes
- Creates directory structure: `cacheDir/droiddeploy/<prefix>-<appId>-<version>.apk`
- Cleans up partial downloads on failure

**Decisions**:
- 8KB buffer for streaming
- Use cache dir (automatically cleaned by system when needed)
- Emit progress to StateFlow for real-time UI updates
- Calculate percentage when Content-Length is available

---

### 12. Package Installer Integration
**Status**: ✅ Completed

**Implemented**:
- `PackageInstallerInstaller` - Uses Android PackageInstaller API
- `DroidDeployInstallReceiver` - BroadcastReceiver for install results
- Static callback registry for receiver-to-installer communication
- Session-based installation with progress

**Decisions**:
- MODE_FULL_INSTALL for package replacement
- PendingIntent.FLAG_MUTABLE on Android S+ for receiver
- Static ConcurrentHashMap for callback registry (works without DI)
- Clean up callbacks after invocation
- Map PackageInstaller status codes to DroidInstallState

**Challenges**:
- BroadcastReceiver is separate component, needs communication channel
- Solved with static registry indexed by session ID
- Ensuring receiver is properly declared in manifest

---

### 13. Update Manager
**Status**: ✅ Completed

**Implemented**:
- `UpdateManager` - Orchestrates download + install flow
- Manages installation mutex to prevent concurrent installs
- Coordinates between repository, downloader, and installer
- Handles APK file cleanup after installation

**Decisions**:
- Reject concurrent installs immediately (AlreadyInProgress error)
- Use coroutine scope with SupervisorJob for fire-and-forget installs
- Cleanup APK file 2 seconds after install starts (give time for installer to read)
- Optional cleanup based on InstallOptions

**Challenges**:
- Coordinating async download, sync install, async receiver callback
- Ensuring state transitions are clear and atomic

---

### 14. Dependency Injection Container
**Status**: ✅ Completed

**Implemented**:
- `SdkContainer` - Internal service locator pattern
- Creates all dependencies in correct order
- Wires up OkHttp client with interceptors and authenticator
- Creates Retrofit with Moshi converter

**Decisions**:
- Internal class (not exposed to SDK users)
- All lazy instantiation (objects created only when accessed)
- Constructor injection for testability
- No external DI framework as per requirements

---

### 15. Public API
**Status**: ✅ Completed

**Implemented**:
- `DroidDeploy` - Singleton object with public API
- `init()` - One-time initialization with validation
- `updates` - StateFlow for reactive update info
- `installState` - StateFlow for installation progress
- `forceFetch()` - Manual update check
- `installLatest()` - Trigger download + install

**Decisions**:
- Object singleton for simple API
- Throw on double-init with clear error message
- Validate config (apiKey, applicationId, host required)
- Start periodic fetch automatically after init
- Private `requireContainer()` for fail-fast if not initialized

---

### 16. Testing
**Status**: ✅ Completed

**Implemented**:
- `UpdateDecisionLogicTest` - Unit tests for version comparison
- Test cases:
  - Update available when server > installed
  - Not available when equal
  - Not available when server < installed
  - Not available when either version is null

**Decisions**:
- Extracted pure function for testability
- Comprehensive edge case coverage
- Standard JUnit 4 for compatibility

**Future Improvements**:
- Mock tests for network layer
- Integration tests for full flow
- Coroutine test dispatcher for repository tests

---

### 17. Documentation
**Status**: ✅ Completed

**Delivered**:
- README.md with:
  - Setup instructions
  - Usage examples (Application.onCreate, UI collection, manual fetch, install)
  - API reference
  - Architecture overview
- Inline code documentation where needed

---

## Key Architectural Decisions

### 1. No External DI Framework
**Rationale**: Requirement specified no Dagger/Hilt/Koin
**Solution**: Internal SdkContainer with manual dependency graph
**Trade-off**: More boilerplate, but simpler dependency tree and smaller APK

### 2. In-Memory Token Storage
**Rationale**: Requirement specified in-memory only
**Solution**: AtomicReference in InMemoryTokenStore
**Trade-off**: Token lost on process death (requires re-auth), but simpler and more secure

### 3. StateFlow for Reactive API
**Rationale**: Modern Android apps use reactive patterns
**Solution**: MutableStateFlow internally, exposed as StateFlow
**Benefits**: Type-safe, lifecycle-aware, minimal boilerplate

### 4. Sealed Classes for State
**Rationale**: Exhaustive when expressions, type safety
**Solution**: DroidInstallState, FetchResult, DroidDeployError all sealed
**Benefits**: Compiler-enforced handling of all cases

### 5. Separate Coroutine Scopes
**Rationale**: Avoid cancellation propagation
**Solution**: SupervisorJob + Dispatchers.IO per component
**Benefits**: Repository failure doesn't cancel update manager

---

## Challenges & Solutions

### Challenge 1: Authenticator is Synchronous
**Problem**: OkHttp's Authenticator interface is synchronous, but our API is suspend
**Solution**: Used runBlocking { } within authenticate() to bridge async/sync
**Acceptable Because**: Called on OkHttp's background thread, not main thread

### Challenge 2: Concurrent Token Refresh
**Problem**: Multiple 401s could trigger multiple token refreshes
**Solution**: Mutex in Authenticator ensures single-flight refresh
**Result**: One refresh, other requests wait for result

### Challenge 3: BroadcastReceiver Communication
**Problem**: PackageInstaller result comes to BroadcastReceiver, need to update StateFlow
**Solution**: Static ConcurrentHashMap indexed by session ID for callbacks
**Alternative Considered**: EventBus (rejected: external dependency)

### Challenge 4: Infinite Auth Loops
**Problem**: Auth failure could cause infinite retry loop
**Solution**:
- Retry marker header prevents double retry
- Don't retry auth endpoints
- Limit to one retry attempt
**Result**: Guaranteed termination

---

## Testing Strategy

### Unit Tests
- ✅ Version comparison logic
- ⏳ Error mapping (future)
- ⏳ State transitions (future)

### Integration Tests (Future)
- Mock server for network layer
- Full update check flow
- Download with simulated failures
- Installation flow (requires instrumented tests)

---

## Known Limitations

1. **Process Death**: Token lost when app process is killed (by design, in-memory only)
2. **No Background Updates**: Updates only check while app is running (requirement)
3. **Single Install**: Only one installation can run at a time (by design)
4. **No Retry Logic**: Failed downloads don't auto-retry (user must trigger manually)

---

## Future Enhancements (Not in MVP)

1. **Download Resume**: Support resuming interrupted downloads
2. **Multiple Hosts**: Support fallback hosts for high availability
3. **Delta Updates**: Download only changed parts of APK
4. **Notification**: Show notification during download
5. **Scheduled Background Checks**: Using WorkManager (would require removing restriction)

---

## Files Created

### Core SDK
- `DroidDeploy.kt` - Public API entry point
- `DroidDeployConfig.kt` - Configuration DSL

### Errors
- `errors/DroidDeployError.kt` - Error hierarchy

### Logging
- `logs/DroidDeployLog.kt` - Log data class
- `logs/Logger.kt` - Internal logger

### Network
- `network/RestResponse.kt` - Response wrapper DTO
- `network/VersionDto.kt` - Version DTO
- `network/ApiTokenDto.kt` - Token DTO
- `network/ApiKeyLoginRequest.kt` - Login request DTO
- `network/TokenStore.kt` - In-memory token storage
- `network/HostStore.kt` - Thread-safe host storage
- `network/HostSelectionInterceptor.kt` - Dynamic host rewriting
- `network/AuthHeaderInterceptor.kt` - Bearer token injection
- `network/OkHttpLoggingInterceptor.kt` - Request/response logging
- `network/ApiKeyAuthenticator.kt` - 401 retry with token refresh
- `network/DroidDeployApi.kt` - Retrofit interface

### Updates
- `updates/DroidUpdate.kt` - Update state model
- `updates/FetchResult.kt` - Fetch result sealed class
- `updates/DefaultVersionCodeProvider.kt` - Default version provider
- `updates/UpdatesRepository.kt` - Update checking logic
- `updates/UpdateManager.kt` - Update orchestration

### Install
- `install/InstallOptions.kt` - Install configuration
- `install/DroidInstallState.kt` - Install state machine
- `install/ApkDownloader.kt` - APK streaming downloader
- `install/PackageInstallerInstaller.kt` - Android PackageInstaller wrapper
- `install/DroidDeployInstallReceiver.kt` - BroadcastReceiver for install results

### DI
- `di/SdkContainer.kt` - Internal dependency container

### Tests
- `test/.../UpdateDecisionLogicTest.kt` - Version comparison tests

### Build Files
- `droiddeploy/build.gradle.kts` - Library module config
- `droiddeploy/src/main/AndroidManifest.xml` - Manifest with receiver
- `gradle/libs.versions.toml` - Updated version catalog

### Documentation
- `droiddeploy/README.md` - User documentation

---

## Summary

Successfully implemented a production-ready Android SDK for DroidDeploy service following all requirements:
- ✅ Android 10+ support
- ✅ Kotlin + Coroutines + StateFlow
- ✅ Retrofit + OkHttp networking
- ✅ No external DI frameworks
- ✅ In-memory token storage
- ✅ Periodic update checks (process lifetime)
- ✅ Update checking with UI state
- ✅ APK download with progress
- ✅ Automatic installation
- ✅ Comprehensive error handling
- ✅ Configurable logging with redaction
- ✅ Clean architecture
- ✅ Unit tests for core logic
- ✅ Documentation

The SDK is ready for integration testing and demo app development.
