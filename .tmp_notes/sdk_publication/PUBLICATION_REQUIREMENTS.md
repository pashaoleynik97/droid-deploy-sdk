You are configuring build, CI/CD, and publishing for an Android SDK project.

WORK LOGGING & CONTEXT PERSISTENCE (MANDATORY)

You MUST maintain a persistent work log inside the repository at:

.tmp_notes/sdk_publication/

Purpose:
- Preserve full context across multiple sessions
- Make all decisions auditable
- Allow future adjustments without losing reasoning
- Enable recovery if work is interrupted

Logging rules:
- For EVERY meaningful action (decision, design choice, implementation step, refactor, fix, or workflow change),
  you MUST write an entry to the log.
- Logging is REQUIRED before and/or immediately after each logical step.
- Never batch multiple unrelated steps into a single log entry.

Required structure (you may extend, but not remove):

.tmp_notes/sdk_publication/
├── PLAN.md                # High-level execution plan, updated as plan evolves
├── DECISIONS.md           # All technical decisions with rationale and alternatives considered
├── TODO.md                # Remaining and completed tasks (checkbox style preferred)
├── WORKLOG.md             # Chronological log of actions performed
├── CONTEXT.md             # Current project state summary (for session restore)
└── NOTES/                 # Optional deep-dive notes, experiments, or drafts

Content requirements:

1) PLAN.md
- Initial step-by-step plan derived from the prompt
- Updated whenever scope or sequencing changes

2) DECISIONS.md
   For each decision, include:
- Date/time
- Decision made
- Reasoning
- Alternatives considered
- Why alternatives were rejected

3) TODO.md
- Use clear task granularity
- Mark tasks as [ ] pending or [x] completed
- Reflect real progress continuously

4) WORKLOG.md
- Append-only, chronological
- Each entry must include:
    - Timestamp
    - What was done
    - Why it was done
    - Files affected

5) CONTEXT.md
- Always reflect the CURRENT state of the work
- Must be sufficient to resume work in a new session with no prior context
- Update after any significant milestone

Behavioral rules:
- Do NOT skip logging, even for “small” changes
- Do NOT overwrite logs; append or update appropriately
- Treat logs as first-class deliverables, not auxiliary notes
- If unsure whether a step is “log-worthy” — log it

Failure to follow these logging requirements is considered an incomplete implementation.

PROJECT CONTEXT
- Repository contains:
    - :droiddeploy → Android library module (this is the ONLY module to be published)
    - :app → demo/sample app (must NOT be published)
- Android SDK minSdk = 29 (Android 10+)
- Gradle Kotlin DSL everywhere
- Kotlin + Coroutines
- Unit tests only
- No external DI frameworks
- Repo already builds successfully locally

GOAL
1) Build and test SDK on push to develop
2) Publish SDK ONLY via manual workflow_dispatch on master
3) Publish artifacts via JitPack (AAR + sources)
4) Upload SAME-VERSION AAR + sources JAR to GitHub Releases
5) Patch version auto-increments, major/minor manual
6) Ensure GitHub Release version == JitPack version

DISTRIBUTION MODEL (IMPORTANT)
- JitPack builds artifacts automatically when a Git tag exists
- GitHub Actions MUST NOT upload to JitPack
- Publish workflow responsibility:
    - validate branch
    - calculate version
    - create and push Git tag
    - build AAR locally
    - upload AAR + sources to GitHub Release
- JitPack will detect tag and build independently

VERSIONING STRATEGY
- Introduce a root file: version.txt
    - Format: MAJOR.MINOR.PATCH (e.g. 1.2.0)
- Patch is auto-incremented during publish workflow
- Major/minor bumped manually by editing version.txt
- Tag format: vX.Y.Z
- Gradle version must be set dynamically from version.txt

MAVEN COORDINATES
- groupId: com.github.pashaoleynik97
- artifactId: droiddeploy
- version: derived from version.txt (same everywhere)

GRADLE REQUIREMENTS
- Use maven-publish
- Publishing must be configured ONLY in :droiddeploy module
- Publish ONLY release variant
- Generate:
    - release AAR
    - sources JAR
- Do NOT publish :app
- Root project must NOT define publications

JITPACK CONFIGURATION
- Add jitpack.yml
- Configure correct JDK based on AGP used in project
- Ensure JitPack can execute Gradle publish tasks
- No credentials needed

CI PIPELINES

1) develop branch CI (ci.yml)
   Trigger:
- push to develop
- pull_request targeting develop

Steps:
- checkout
- setup Java (appropriate version inferred from project)
- cache Gradle
- run:
  ./gradlew :droiddeploy:test

Fail on any error.

2) Publish workflow (release.yml)
   Trigger:
- workflow_dispatch ONLY

Hard rules:
- MUST fail if branch != master
- MUST fail if version.txt missing or malformed

Workflow steps:
1. Checkout with full git history
2. Read version.txt
3. Increment PATCH segment
4. Save new version back to version.txt
5. Commit version.txt change (commit message: "Release vX.Y.Z")
6. Create annotated git tag vX.Y.Z
7. Push commit + tag
8. Build artifacts:
   ./gradlew :droiddeploy:assembleRelease
   ./gradlew :droiddeploy:sourcesJar
9. Create GitHub Release:
    - tag: vX.Y.Z
    - name: vX.Y.Z
    - attach:
        - droiddeploy-release.aar
        - droiddeploy-sources.jar

IMPORTANT:
- GitHub Release artifacts MUST match version used in tag
- JitPack artifact version MUST match tag version
- No version mismatches allowed

ARTIFACT PATHS
Assume default Android outputs:
- AAR:
  droiddeploy/build/outputs/aar/droiddeploy-release.aar
- Sources JAR:
  droiddeploy/build/libs/droiddeploy-sources.jar

DOCUMENTATION
- Update README.md with dependency example:

repositories {
maven { url = uri("https://jitpack.io") }
}

dependencies {
implementation("com.github.pashaoleynik97:droiddeploy:X.Y.Z")
}

SAFETY & QUALITY
- Do not publish snapshots
- Do not publish from develop
- Do not publish without manual trigger
- Do not publish demo app
- Ensure repeatable builds
- Ensure version consistency across:
    - Gradle
    - Git tag
    - GitHub Release
    - JitPack

DELIVERABLES
1) Gradle publishing configuration for :droiddeploy
2) jitpack.yml
3) .github/workflows/ci.yml
4) .github/workflows/release.yml
5) version.txt
6) README update snippet

Implement everything cleanly and idiomatically using Kotlin DSL.
