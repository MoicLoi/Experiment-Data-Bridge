import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/AuthProvider'
import { API_BASE_URL } from '../lib/api'
import { streamSse } from '../lib/sse'
import { listProjects } from '../features/tasks/taskApi'
import type { TaskLifecycleEvent } from '../features/tasks/types'

const TERMINAL = new Set(['COMPLETED', 'FAILED', 'TIMED_OUT', 'CANCELLED'])
const SEEN_KEY = 'edb.task-notifications.seen.v1'
interface Notice { key: string; taskId: number; projectId: number; status: string; taskType: string; durationMs?: number; dataFileId?: number }

export function TaskNotifications() {
  const { api, token } = useAuth(); const { i18n } = useTranslation()
  const [notices, setNotices] = useState<Notice[]>([]); const timers = useRef<number[]>([])
  const seen = useRef(loadSeen()); const zh = i18n.resolvedLanguage?.toLowerCase().startsWith('zh')
  useEffect(() => {
    if (!token) return
    const controller = new AbortController(); let stopped = false
    const receive = (event: TaskLifecycleEvent) => {
      if (!TERMINAL.has(event.status)) return
      const key = `${event.taskId}:${event.type}:${event.occurredAt}`
      if (seen.current.has(key)) return
      seen.current.add(key); persistSeen(seen.current)
      const notice: Notice = { key, taskId: event.taskId, projectId: event.projectId, status: event.status, taskType: event.details.taskType ?? 'task', durationMs: numberValue(event.details.durationMs), dataFileId: numberValue(event.details.dataFileId) }
      setNotices((current) => [...current.slice(-3), notice])
      timers.current.push(window.setTimeout(() => dismiss(key), event.status === 'COMPLETED' ? 5_000 : 12_000))
      if (document.hidden && window.Notification?.permission === 'granted') new Notification(title(notice, zh), { body: `Task #${notice.taskId}${notice.durationMs == null ? '' : ` · ${formatDuration(notice.durationMs)}`}`, tag: key, silent: true })
    }
    const dismiss = (key: string) => setNotices((current) => current.filter((item) => item.key !== key))
    const connect = async (projectId: number) => { while (!stopped) { try { await streamSse(`/api/v1/projects/${projectId}/tasks/events`, { baseUrl: API_BASE_URL, token, signal: controller.signal, onMessage: (message) => receive(JSON.parse(message.data) as TaskLifecycleEvent) }) } catch { if (controller.signal.aborted) return; await new Promise((resolve) => window.setTimeout(resolve, 2_000)) } } }
    listProjects(api).then((projects) => projects.forEach((project) => void connect(project.id))).catch(() => undefined)
    return () => { stopped = true; controller.abort(); timers.current.forEach(window.clearTimeout); timers.current = [] }
  }, [api, token, zh])
  const dismiss = (key: string) => setNotices((current) => current.filter((item) => item.key !== key))
  if (!notices.length) return null
  return <aside className="task-toast-stack" aria-live="polite">{notices.map((notice) => <section key={notice.key} className={`task-toast task-toast-${notice.status.toLowerCase()}`} role={notice.status === 'COMPLETED' ? 'status' : 'alert'}><div><strong>{title(notice, zh)}</strong>{notice.durationMs != null && <small>{formatDuration(notice.durationMs)}</small>}</div><div className="task-toast-links"><Link to={`/tasks?projectId=${notice.projectId}&taskId=${notice.taskId}`}>{zh ? '查看时间线' : 'Timeline'}</Link>{notice.dataFileId && <Link to={`/datafiles?projectId=${notice.projectId}&dataFileId=${notice.dataFileId}`}>{zh ? '查看结果' : 'Result'}</Link>}</div><button type="button" aria-label={zh ? '忽略通知' : 'Dismiss notification'} onClick={() => dismiss(notice.key)}>×</button></section>)}</aside>
}

export function TaskNotificationPermissionButton() {
  const { i18n } = useTranslation(); const zh = i18n.resolvedLanguage?.toLowerCase().startsWith('zh')
  const supported = typeof window !== 'undefined' && 'Notification' in window
  const [permission, setPermission] = useState<NotificationPermission>(supported ? Notification.permission : 'denied')
  if (!supported || permission !== 'default') return null
  return <button type="button" className="notification-permission-button" onClick={() => void Notification.requestPermission().then(setPermission)}>{zh ? '启用后台任务通知' : 'Enable task notifications'}</button>
}

function numberValue(value?: string) { const parsed = value == null ? NaN : Number(value); return Number.isFinite(parsed) ? parsed : undefined }
function title(notice: Notice, zh?: boolean) { const type = zh ? ({ analysis:'统计分析', 'data-file-conversion':'文件转换', pipeline:'工作流' }[notice.taskType] ?? notice.taskType) : notice.taskType; const state = zh ? ({ COMPLETED:'已完成', FAILED:'失败', TIMED_OUT:'超时', CANCELLED:'已取消' }[notice.status] ?? notice.status) : notice.status.toLowerCase().replace('_', ' '); return `${notice.status === 'COMPLETED' ? '✓ ' : ''}${type} ${state}` }
function formatDuration(value: number) { if (value < 1_000) return `${value} ms`; if (value < 60_000) return `${(value / 1_000).toFixed(value < 10_000 ? 1 : 0)}s`; const seconds = Math.floor(value / 1_000); return `${String(Math.floor(seconds / 60)).padStart(2,'0')}:${String(seconds % 60).padStart(2,'0')}` }
function loadSeen() { try { return new Set<string>(JSON.parse(sessionStorage.getItem(SEEN_KEY) ?? '[]') as string[]) } catch { return new Set<string>() } }
function persistSeen(seen: Set<string>) { try { sessionStorage.setItem(SEEN_KEY, JSON.stringify([...seen].slice(-200))) } catch { /* storage may be disabled */ } }
