#!/usr/bin/env node
// Repository hygiene checks for EDB.
//
// This script exists because several facts about this repository are stated in
// more than one place, and because publishing a release package is easy to get
// wrong in a way that leaks credentials. It fails loudly instead of trusting
// convention.
//
// Checks:
//   1. Version consistency across gradle.properties, the npm manifests, the
//      Tauri manifest, the release label and the health endpoint.
//   2. No credential-bearing or runtime path is tracked by git.
//   3. Required project files are present.
//   4. The OpenAPI resources exist and are structurally intact.
//
// Run locally:  node packaging/ci/check-repo.mjs

import { execFileSync } from 'node:child_process'
import { readFileSync, existsSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')
const failures = []
const warnings = []

const fail = (message) => failures.push(message)
const warn = (message) => warnings.push(message)
const rel = (...parts) => join(repoRoot, ...parts)
const read = (path) => readFileSync(rel(path), 'utf8')

// ---------------------------------------------------------------------------
// 1. Version consistency
// ---------------------------------------------------------------------------

const gradleProperties = read('gradle.properties')
const baseVersionMatch = gradleProperties.match(/^\s*version\s*=\s*(.+)$/m)
if (!baseVersionMatch) {
  fail('gradle.properties declares no "version=" property; it is the base project version.')
}
const baseVersion = baseVersionMatch ? baseVersionMatch[1].trim() : null

const releaseLabel = read('packaging/demo-v1/VERSION').trim()

const npmManifests = ['web-ui/package.json', 'desktop/package.json']
for (const manifest of npmManifests) {
  const version = JSON.parse(read(manifest)).version
  if (baseVersion && version !== baseVersion) {
    fail(`${manifest} declares version ${version} but gradle.properties declares ${baseVersion}.`)
  }
}

const tauriVersion = JSON.parse(read('desktop/src-tauri/tauri.conf.json')).version
if (baseVersion && tauriVersion !== baseVersion) {
  fail(`desktop/src-tauri/tauri.conf.json declares version ${tauriVersion} but gradle.properties declares ${baseVersion}.`)
}

if (baseVersion && !releaseLabel.startsWith(`${baseVersion}-`)) {
  fail(`packaging/demo-v1/VERSION is "${releaseLabel}", which is not "${baseVersion}-<label>".`)
}

// The health endpoint reports the release label users see; drift here has
// already shipped once, so it is treated as an error rather than a warning.
const applicationSource = read('web/src/main/kotlin/me/moiclathy/edb/web/Application.kt')
const healthMatch = applicationSource.match(/call\.respond\(mapOf\("status" to "UP", "version" to "([^"]+)"\)\)/)
if (!healthMatch) {
  warn('Could not locate the /api/v1/health version response in Application.kt; skipping the label comparison.')
} else if (healthMatch[1] !== releaseLabel) {
  fail(
    `The /api/v1/health endpoint reports "${healthMatch[1]}" but packaging/demo-v1/VERSION is "${releaseLabel}". ` +
      'A pretest package would report the wrong version.',
  )
}

// ---------------------------------------------------------------------------
// 2. Tracked-path guard
// ---------------------------------------------------------------------------

// Paths that must never be tracked. `build`/`dist` are matched as directory
// segments so nested Gradle and frontend outputs are covered too.
const forbiddenTracked = [
  { pattern: /(^|\/)node_modules\//, reason: 'dependency tree, never committed' },
  { pattern: /^release\//, reason: 'release packages contain real credentials and uploaded data' },
  { pattern: /^outputs\//, reason: 'runtime output' },
  { pattern: /(^|\/)\.idea\//, reason: 'per-developer IDE state, including database credentials' },
  { pattern: /^core\/logs\//, reason: 'runtime logs' },
  { pattern: /^core\/projects\//, reason: 'runtime project data' },
  { pattern: /^web\/projects\//, reason: 'runtime project data' },
  { pattern: /^core\/\.tmp\//, reason: 'temporary worker validation output' },
  { pattern: /(^|\/)\.r-library\//, reason: 'vendored R library tree' },
  { pattern: /(^|\/)build\//, reason: 'build output' },
  { pattern: /(^|\/)dist\//, reason: 'build output' },
  { pattern: /(^|\/)\.env($|\.local$)/, reason: 'environment file' },
  { pattern: /^packaging\/demo-v1\/config\/application\.yml$/, reason: 'generated configuration with credentials' },
]

// The shipped default configuration is the one application.yml that MUST be tracked.
const allowedTracked = new Set(['web/src/main/resources/application.yml'])

let trackedFiles = null
// CHECK_REPO_TRACKED_LIST_FILE points at a file holding one tracked path per
// line. It exists so this guard can be exercised (and its negative case tested)
// in environments where this process cannot spawn git - some sandboxes block
// piped child processes. In CI the git path below is used.
if (process.env.CHECK_REPO_TRACKED_LIST_FILE) {
  trackedFiles = readFileSync(process.env.CHECK_REPO_TRACKED_LIST_FILE, 'utf8')
    // Strip NULs: PowerShell's ">" redirection emits UTF-16, and a mangled list
    // would make every pattern miss while still looking like it ran.
    .replaceAll('\0', '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
} else {
  let insideWorkTree = false
  try {
    const probe = execFileSync('git', ['rev-parse', '--is-inside-work-tree'], {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    })
    insideWorkTree = probe.trim() === 'true'
  } catch {
    insideWorkTree = false
  }

  if (!insideWorkTree) {
    warn('Not a git working tree; skipping the tracked-path guard.')
  } else {
    try {
      const output = execFileSync('git', ['ls-files', '-z'], { cwd: repoRoot, encoding: 'utf8' })
      trackedFiles = output.split('\0').filter(Boolean)
    } catch (error) {
      // Being inside a work tree but unable to enumerate files is an
      // environment failure, not an absent repository. Never skip silently.
      fail(
        `Could not enumerate tracked files with "git ls-files": ${error.message}. ` +
          'Set CHECK_REPO_TRACKED_FILES to a newline-separated file list to run this check anyway.',
      )
    }
  }
}

if (trackedFiles) {
  for (const file of trackedFiles) {
    const normalized = file.replaceAll('\\', '/')
    if (allowedTracked.has(normalized)) continue
    for (const { pattern, reason } of forbiddenTracked) {
      if (pattern.test(normalized)) {
        fail(`Tracked path "${normalized}" must not be committed (${reason}).`)
        break
      }
    }
    if (/(^|\/)application\.yml$/.test(normalized)) {
      fail(
        `Tracked configuration "${normalized}" — only web/src/main/resources/application.yml may be tracked, ` +
          'because generated configuration carries real credentials.',
      )
    }
  }
}

// ---------------------------------------------------------------------------
// 3. Required files
// ---------------------------------------------------------------------------

const requiredFiles = [
  'LICENSE',
  'NOTICE',
  'THIRD-PARTY-LICENSES.md',
  'README.md',
  'CONTRIBUTING.md',
  'CHANGELOG.md',
  '.gitignore',
  '.gitattributes',
  '.editorconfig',
  'settings.gradle.kts',
  'gradle/wrapper/gradle-wrapper.jar',
  'gradle/wrapper/gradle-wrapper.properties',
  'web-ui/package-lock.json',
  'packaging/ci/check-repo.mjs',
]
for (const file of requiredFiles) {
  if (!existsSync(rel(file))) fail(`Required file is missing: ${file}`)
}

// ---------------------------------------------------------------------------
// 4. OpenAPI resources
// ---------------------------------------------------------------------------

const openApiFiles = ['task-0.25.yaml', 'datafile-0.25.yaml', 'agent-0.25.yaml']
for (const name of openApiFiles) {
  const path = `web/src/main/resources/openapi/${name}`
  if (!existsSync(rel(path))) {
    fail(`OpenAPI resource is missing: ${path}`)
    continue
  }
  const text = read(path)
  if (!/^openapi\s*:/m.test(text)) fail(`${path} does not declare "openapi:".`)
  if (!/^paths\s*:/m.test(text)) fail(`${path} declares no "paths:" section.`)
}

// ---------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------

for (const message of warnings) console.warn(`warning: ${message}`)
if (failures.length > 0) {
  console.error(`\n${failures.length} repository hygiene check(s) failed:`)
  for (const message of failures) console.error(`  - ${message}`)
  process.exit(1)
}

console.log('Repository hygiene checks passed.')
if (baseVersion) console.log(`  version: ${baseVersion} (release label ${releaseLabel})`)
if (trackedFiles) console.log(`  tracked files inspected: ${trackedFiles.length}`)
console.log(`  OpenAPI resources verified: ${openApiFiles.length}`)
