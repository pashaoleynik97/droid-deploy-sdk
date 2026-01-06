# Current Project State

**Last Updated:** 2026-01-06 20:45

## Project Overview
- **Repository:** DroidDeploy Android SDK
- **Purpose:** Deploy and manage Android builds via remote server
- **Modules:**
  - `:droiddeploy` - SDK library (CONFIGURED FOR PUBLISHING)
  - `:app` - Demo application (NOT published)

## Current State
**Status:** ✅ Implementation complete - All publishing infrastructure configured and tested

## Technical Stack
- **Build System:** Gradle 8.x with Kotlin DSL
- **AGP Version:** 8.9.0
- **Kotlin:** 2.0.21
- **Min SDK:** 29 (Android 10+)
- **Compile SDK:** 35
- **Java Target:** 11

## Dependencies (droiddeploy module)
- AndroidX Core KTX
- Retrofit 2.11.0 + Moshi converter
- OkHttp 4.12.0 with logging interceptor
- Moshi 1.15.1 for JSON parsing
- Coroutines 1.10.1 (core + android)
- JUnit for testing

## Distribution Plan
- **Primary:** JitPack (automatic from git tags)
- **Secondary:** GitHub Releases (AAR + sources JAR attached)
- **Maven Coordinates:**
  - Group: `com.github.pashaoleynik97`
  - Artifact: `droiddeploy`
  - Version: Dynamic from `version.txt`

## Publishing Strategy
- **Develop branch:** CI only (tests)
- **Master branch:** Manual release workflow
- **Versioning:** MAJOR.MINOR.PATCH in `version.txt`
- **Tag format:** vX.Y.Z (annotated)
- **Auto-increment:** PATCH only
- **Manual bump:** MAJOR and MINOR

## Implemented Components
1. ✅ `version.txt` file (initial version: 0.1.0)
2. ✅ Maven publish configuration in `:droiddeploy` module
3. ✅ `jitpack.yml` configuration
4. ✅ CI workflow (`.github/workflows/ci.yml`)
5. ✅ Release workflow (`.github/workflows/release.yml`)
6. ✅ `README.md` with usage instructions
7. ✅ All work log files (PLAN, DECISIONS, TODO, WORKLOG, CONTEXT)

## Verification Results
1. ✅ `./gradlew :droiddeploy:assembleRelease` - SUCCESS (AAR: 113K)
2. ✅ `./gradlew :droiddeploy:sourcesJar` - SUCCESS (JAR: 20K)
3. ✅ `./gradlew :droiddeploy:test` - SUCCESS (all tests passing)
4. ✅ `./gradlew :droiddeploy:publishToMavenLocal --dry-run` - SUCCESS
5. ✅ Artifacts verified at expected paths

## Next Steps (Usage)
1. Push changes to repository
2. Create `develop` branch if not exists (for CI testing)
3. When ready to release:
   - Ensure on `master` branch
   - Manually trigger release workflow via GitHub Actions
   - Workflow will auto-increment patch version, create tag, and publish
4. JitPack will automatically build from the created git tag
5. Users can then add the dependency as documented in README.md

## Known Constraints
- Only publish from `master` branch
- Only manual releases (no automatic publishing on push)
- Must fail fast if release attempted from wrong branch
- Version consistency critical across: Git tag, GitHub Release, JitPack
- No snapshots, no development builds published
- `:app` module must never be published

## Critical Paths to Verify
- AAR output: `droiddeploy/build/outputs/aar/droiddeploy-release.aar`
- Sources JAR: `droiddeploy/build/libs/droiddeploy-sources.jar`
- Version file: `version.txt` in project root
