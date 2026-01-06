# SDK Publication Implementation Plan

## Goal
Configure build, CI/CD, and publishing for the DroidDeploy Android SDK with JitPack integration and GitHub Releases.

## High-Level Steps

### 1. Versioning Setup
- Create `version.txt` in project root with format `MAJOR.MINOR.PATCH`
- Initial version: `0.1.0`
- Patch auto-increments on release, major/minor manual

### 2. Gradle Publishing Configuration
- Configure `maven-publish` plugin in `:droiddeploy` module only
- Publish release variant only (AAR + sources JAR)
- Maven coordinates:
  - groupId: `com.github.pashaoleynik97`
  - artifactId: `droiddeploy`
  - version: dynamically read from `version.txt`
- Generate sources JAR task
- Do NOT publish `:app` module

### 3. JitPack Configuration
- Create `jitpack.yml` in project root
- Configure JDK 17 (required for AGP 8.9.0)
- JitPack will automatically build from Git tags

### 4. CI Workflow (develop branch)
- Create `.github/workflows/ci.yml`
- Triggers: push to develop, PRs targeting develop
- Steps:
  - Checkout code
  - Setup Java 17
  - Cache Gradle dependencies
  - Run tests: `./gradlew :droiddeploy:test`

### 5. Release Workflow (master branch)
- Create `.github/workflows/release.yml`
- Trigger: `workflow_dispatch` only
- Hard validation: must be on `master` branch
- Steps:
  1. Checkout with full history
  2. Read `version.txt`
  3. Increment PATCH version
  4. Save new version to `version.txt`
  5. Commit version change
  6. Create annotated Git tag `vX.Y.Z`
  7. Push commit + tag
  8. Build release artifacts
  9. Create GitHub Release with artifacts attached
- Artifacts to attach:
  - `droiddeploy-release.aar`
  - `droiddeploy-sources.jar`

### 6. Documentation
- Create/update `README.md` with:
  - JitPack repository configuration
  - Dependency declaration example
  - Version reference

## Critical Requirements
- Version consistency: Git tag = GitHub Release = JitPack artifact
- Only publish from `master` branch
- Only manual releases (no automatic publishing)
- No snapshots, no development builds
- Repeatable, deterministic builds

## Expected Artifacts
1. `version.txt`
2. Modified `droiddeploy/build.gradle.kts` (with maven-publish)
3. `jitpack.yml`
4. `.github/workflows/ci.yml`
5. `.github/workflows/release.yml`
6. `README.md` (created/updated)

## Implementation Order
1. Work logs (this file and siblings)
2. Version file
3. Gradle publishing config
4. JitPack config
5. CI workflow
6. Release workflow
7. README
8. Verification
