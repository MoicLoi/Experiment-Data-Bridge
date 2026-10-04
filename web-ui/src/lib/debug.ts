type DebugLevel = 'INFO' | 'WARN' | 'ERROR'

interface DebugEvent {
  level: DebugLevel
  category: string
  message: string
  path?: string
  details?: Record<string, string>
  occurredAt: string
}

let enabled = false
let initialized = false

const sensitive = /password|passwd|token|authorization|cookie|secret|api[-_]?key/i
const clean = (value: unknown, max = 300) => String(value ?? '').replace(/[\r\n\t]+/g, ' ').slice(0, max)
const safeDetails = (details: Record<string, unknown> = {}) => Object.fromEntries(
  Object.entries(details).slice(0, 20).map(([key, value]) => [clean(key, 64), sensitive.test(key) ? '[REDACTED]' : clean(value)]),
)

export function debugEvent(category: string, message: string, details: Record<string, unknown> = {}, level: DebugLevel = 'INFO') {
  if (!enabled) return
  const event: DebugEvent = { level, category: clean(category, 64), message: clean(message, 1_000), path: location.pathname, details: safeDetails(details), occurredAt: new Date().toISOString() }
  void fetch('/api/v1/debug/events', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(event), keepalive: true,
  }).catch(() => undefined)
}

export async function initializeDebugLogging() {
  if (initialized) return
  initialized = true
  try {
    const response = await fetch('/api/v1/debug/config', { headers: { Accept: 'application/json' } })
    enabled = response.ok && Boolean((await response.json() as { enabled?: boolean }).enabled)
  } catch { enabled = false }
  if (!enabled) return

  debugEvent('lifecycle', 'frontend debug logging initialized', { userAgent: navigator.userAgent })
  window.addEventListener('error', event => debugEvent('window.error', event.message, { source: event.filename, line: event.lineno, column: event.colno }, 'ERROR'))
  window.addEventListener('unhandledrejection', event => debugEvent('unhandledrejection', event.reason instanceof Error ? event.reason.message : event.reason, {}, 'ERROR'))
  document.addEventListener('pointerup', event => {
    const element = (event.target as Element | null)?.closest('button,a,[role="button"],input,select,textarea') as HTMLElement | null
    if (!element) return
    debugEvent('interaction', 'pointerup', {
      element: element.tagName.toLowerCase(), type: element.getAttribute('type') ?? '',
      label: element.getAttribute('aria-label') ?? element.innerText ?? '', disabled: 'disabled' in element ? String((element as HTMLButtonElement).disabled) : 'false',
    })
  }, true)
}
