# Technical Decisions Log

## 2026-01-06 20:30 - Initial Project Analysis

### Decision: Use JDK 17 for JitPack builds
**Reasoning:**
- Project uses AGP 8.9.0
- AGP 8.x requires JDK 17 or higher
- JDK 17 is LTS and widely supported

**Alternatives Considered:**
- JDK 11: Rejected - too old for AGP 8.9.0
- JDK 21: Rejected - not necessary, might have compatibility issues

### Decision: Initial version 0.1.0
**Reasoning:**
- Project is in initial development phase
- Semantic versioning convention for pre-1.0 releases
- Allows room for API changes before 1.0

**Alternatives Considered:**
- Start with 1.0.0: Rejected - implies API stability
- Start with 0.0.1: Rejected - too granular for initial release

### Decision: Use standard Android artifact output paths
**Reasoning:**
- Default Android Gradle Plugin outputs:
  - AAR: `droiddeploy/build/outputs/aar/droiddeploy-release.aar`
  - Sources JAR: `droiddeploy/build/libs/droiddeploy-sources.jar`
- No need to customize paths
- Predictable locations for CI/CD

**Alternatives Considered:**
- Custom output paths: Rejected - unnecessary complexity

### Decision: Git tag format vX.Y.Z (with v prefix)
**Reasoning:**
- Common convention in open source projects
- Clear visual distinction from version numbers
- JitPack supports this format natively

**Alternatives Considered:**
- No prefix (X.Y.Z): Rejected - less clear in git history
- release/X.Y.Z: Rejected - unnecessarily verbose

### Decision: Annotated git tags instead of lightweight tags
**Reasoning:**
- Annotated tags store tagger info and date
- Better for auditing and history
- Can include release notes in tag message

**Alternatives Considered:**
- Lightweight tags: Rejected - less information stored

### Decision: Separate CI and Release workflows
**Reasoning:**
- Different triggers and purposes
- CI runs automatically on develop
- Release requires manual trigger on master
- Clearer separation of concerns

**Alternatives Considered:**
- Single workflow with multiple jobs: Rejected - more complex, harder to maintain

### Decision: Only test task in CI (not assembleRelease)
**Reasoning:**
- Requirements specify `:droiddeploy:test` only
- Faster CI feedback
- Assembly happens in release workflow

**Alternatives Considered:**
- Include assembleRelease in CI: Rejected - not required, slows down CI

### Decision: Commit version.txt change before creating tag
**Reasoning:**
- Version file serves as source of truth
- Tag points to commit with correct version
- Allows tracking version history in git

**Alternatives Considered:**
- Tag without committing: Rejected - version file would be out of sync

### Decision: Auto-increment PATCH version only
**Reasoning:**
- PATCH changes are typically non-breaking
- Safe to automate
- MAJOR/MINOR require conscious decision

**Alternatives Considered:**
- Auto-increment MINOR: Rejected - could signify API changes unintentionally
- No auto-increment: Rejected - manual overhead for every release
