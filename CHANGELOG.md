# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project intends to follow [Semantic Versioning](https://semver.org/).
The current release label is stored in `packaging/demo-v1/VERSION`.

## [Unreleased]

### Added

- Open-source project skeleton: `LICENSE` (Apache-2.0), `NOTICE`,
  `THIRD-PARTY-LICENSES.md`, `CONTRIBUTING.md`, this changelog, and a rewritten
  `README.md` that documents the project's real maturity instead of the Gradle
  template text.
- `.gitattributes` and `.editorconfig` so line endings and indentation are
  consistent across platforms. `gradlew` is pinned to LF because a CRLF wrapper
  script fails on Linux and macOS.
- CI (`.github/workflows/ci.yml`): web console type-check/build/test, the
  documented backend compile check, the backend test suite, and repository
  hygiene.
- `packaging/ci/check-repo.mjs`: fails when the project version drifts between
  `gradle.properties`, the npm manifests, the Tauri manifest and the release
  label; fails when a credential or runtime path is committed; checks that the
  OpenAPI resources are present.
- `packaging/demo-v1/collect-licenses.ps1`: generates
  `THIRD-PARTY-LICENSES.md` from the shipped artifacts (CLI lib JARs and the
  server fat JAR) instead of a hand-maintained list.

### Changed

- `.gitignore` rewritten and hardened. It previously missed `desktop/node_modules`,
  `desktop/dist`, `desktop/src-tauri/target`, `outputs/`, `core/logs`,
  `core/projects`, `core/.tmp`, `web/projects` and the whole `.idea/` directory —
  all of which would have been committed. `web/src/main/resources/application.yml`
  is deliberately still tracked: it is the shipped default configuration.
- `web-ui/package.json`: dependency versions changed from `latest` to caret
  ranges matching `package-lock.json`, so installs are reproducible and
  intentional. `latest` meant a fresh install could silently pick up a new major.
- `cli/.../MinecraftCliOutput.kt` renamed to `EdbCliOutput.kt` (class
  `MinecraftCliOutput` → `EdbCliOutput`). The old name was copy-paste residue
  from an unrelated project; the class only renders EDB CLI output.

### Deprecated

- `web/src/main/resources/application.yml` still contains the pre-rename
  `ktor.application.modules` entry `com.experiment.bridge.web.ApplicationKt`.
  The entry is inert (the server starts through `embeddedServer`), but it should
  be removed or corrected.

### Known issues

- 46 test sources are disabled via a `.disabled` suffix, including duplicates
  left behind by an interrupted refactor (`ConfigurationCenterTest`,
  `ConfigurationCompareServiceTest`, `ConfigurationMergeServiceTest` and
  `ConfigurationMergeServiceTest1` each appear more than once or under more than
  one package). They are kept in the tree so the intent is visible; re-enabling
  them is a known work item and must not be done by weakening assertions.
- The project version is duplicated across five files. CI now guards the
  duplication, but it has not yet been collapsed to a single source.
- `/api/v1/health` returns a hard-coded version string from
  `web/.../Application.kt`, so a `-pretest` package still reports the `-demo`
  label.
- `THIRD-PARTY-LICENSES.md` cannot resolve licence metadata for a large share of
  the distributed JARs (common in the Kotlin/JetBrains ecosystem). Those entries
  are listed explicitly and must be resolved before publishing binaries.
- `gradle/libs.versions.toml` is only partially authoritative: several module
  build files still pin versions inline, and `core` declares jgit
  7.2.0.202503040940-r while the catalog declares 7.2.1.202505142326-r (Gradle
  resolves 7.2.1). Consolidating this is a known work item.
- The packaging task is Windows-only (`powershell.exe`, `start.bat`), so Linux
  and macOS contributors cannot produce a release package without changes.
- The Tauri desktop shell is not part of `settings.gradle.kts` and its README
  contains a machine-specific path.
- The `core` main source set contains a package called
  `me.moiclathy.edb.core.config.test` holding four scratch files
  (`test.kt`, `testRound.kt`, `test_full.kt`, `valiTest.kt`). Three are
  standalone `fun main()` experiments and one asserts with `kotlin.test`. They
  compile into `core.jar`.
- `core/build.gradle.kts` declares `kotlin("test")` in the `implementation`
  configuration rather than `testImplementation`, so the test framework ships as
  a production dependency — `kotlin-test-2.2.0.jar` is present in the release fat
  JAR. This is presumably what lets the scratch files above compile.
- The disabled-test suffixes are inconsistent: 46 files use `.disabled`, and
  three use misspellings (`.disabeld`, `.disalbed`, `.dsiabled`). They are
  excluded from the build either way, because Kotlin only compiles `*.kt`, but
  the spellings should be normalised when the tests are re-enabled.
