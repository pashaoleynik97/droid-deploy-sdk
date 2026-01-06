# Task List

## Setup and Configuration
- [x] Create work log files (PLAN, DECISIONS, TODO, WORKLOG, CONTEXT)
- [x] Create `version.txt` in project root
- [x] Configure maven-publish in `:droiddeploy/build.gradle.kts`
- [x] Create `jitpack.yml` in project root
- [x] Create `.github/workflows/ci.yml`
- [x] Create `.github/workflows/release.yml`
- [x] Create/update `README.md` with dependency instructions

## Verification
- [x] Test local build: `./gradlew :droiddeploy:assembleRelease`
- [x] Test local sources JAR generation: `./gradlew :droiddeploy:sourcesJar`
- [x] Test local test execution: `./gradlew :droiddeploy:test`
- [x] Verify version.txt is read correctly by Gradle
- [x] Review all workflow files for correctness
- [x] Verify artifact paths match expected locations

## Post-Implementation
- [x] Update CONTEXT.md with final state
- [x] Document any issues or edge cases discovered
- [x] Ensure all work logs are up to date
