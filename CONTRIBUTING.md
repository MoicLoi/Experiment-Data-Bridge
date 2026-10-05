# Contributing to EDB

Thanks for taking the time to contribute. This project values changes that keep
the published contracts stable, so a few rules matter more here than in a typical
repository.

## Before you start

- Read [develop.md](develop.md). It is the authoritative description of the
  module boundary rules, HTTP contracts and the Plugin SPI.
- For anything larger than a bug fix, open an issue first so the approach can be
  agreed before code is written.

## Environment

- JDK 21
- MySQL 8 (see the quick start in [README.md](README.md))
- Node.js 20+ for the web console

## Repository rules

These are enforced by review, and partly by CI:

1. **Never commit credentials, generated configuration or runtime state.**
   `config/application.yml`, `release/`, `outputs/`, `core/logs/`,
   `core/projects/` and `.idea/` are gitignored. If you find a secret in the
   repository, report it privately rather than opening a public issue.
2. **Respect the module boundaries.** `plugin-api` must not depend on `core`,
   `web` or `agent`; plugins may only use the published SPI. Business rules
   belong in application services, not in `web` routes.
3. **HTTP contract changes follow the evolution policy** in
   [develop.md](develop.md) § 5: additive changes only within a major version,
   rename means dual-read for at least one minor version, and breaking changes
   require a new API major or path. Update the DTOs, the OpenAPI resources, the
   CLI and the frontend types in the same commit.
4. **Plugin API changes follow § 6.8.** Adding an abstract method to a published
   interface is a breaking change; adding a method requires a default
   implementation, and the compatibility test must load the previously released
   plugin JAR rather than just recompiling the plugin source.
5. **Workflow IR keeps `schemaVersion` and identifies steps by `type + version`**,
   never by display name. Editor coordinates are editor metadata and must not be
   written into Workflow IR v1.

## Checks to run before opening a pull request

```bash
# Backend: the core compile check plus the plugin API tests
./gradlew :plugin-api:test :core:compileKotlin :agent:compileKotlin :web:compileKotlin :cli:compileKotlin

# Full backend test suite
./gradlew test

# Web console
cd web-ui
npm ci
npm run typecheck
npm run test
npm run build
```

CI additionally runs `packaging/ci/check-repo.mjs`, which verifies that the
project version is consistent across its declared sources, that no ignored
credential or runtime path has been committed, and that the OpenAPI resources
are structurally present. Run it locally with:

```bash
node packaging/ci/check-repo.mjs
```

## Version policy

The base project version lives in `gradle.properties` (`version=`). It must match
`web-ui/package.json`, `desktop/package.json` and
`desktop/src-tauri/tauri.conf.json`. The release label in
`packaging/demo-v1/VERSION` must be exactly the version reported by
`/api/v1/health`. `check-repo.mjs` fails the build when these drift.

Note that `core/src/main/kotlin/.../config/packages/EdbVersion.kt` is a separate
concern: it is the `.edb` package manifest format version, not the application
version. Do not tie it to the release version.

## Commit and pull request style

- Keep commits focused; a commit that changes a contract and its four consumers
  is one commit, not five.
- Describe the *observable* effect of the change, and say which module boundary
  the change touches.
- Update the documentation in the same pull request: `develop.md` for contracts,
  `frontend-manual-test.md` for new user-visible flows, `CHANGELOG.md` for
  anything a user of a release package would notice.

## Disabled tests

Some test sources carry a `.disabled` suffix. They are disabled deliberately
after a refactor and are tracked in [CHANGELOG.md](CHANGELOG.md). If you
re-enable one, delete the `.disabled` suffix and make sure it passes on a clean
checkout; do not re-enable them by weakening their assertions.
