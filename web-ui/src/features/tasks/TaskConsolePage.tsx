import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { formatDateTime } from '../../i18n/formatters'
import type { SupportedLocale } from '../../i18n/resources'
import { API_BASE_URL, ApiError } from '../../lib/api'
import { streamSse } from '../../lib/sse'
import { cancelTask, getTask, getTimeline, listProjects, listTasks, requeueTask } from './taskApi'
import type { Project, Task, TaskLifecycleEvent, TaskPage, TaskStatus, TaskTimeline } from './types'

const STATUSES: TaskStatus[] = ['PENDING', 'QUEUED', 'RUNNING', 'RETRYING', 'COMPLETED', 'FAILED', 'TIMED_OUT', 'CANCELLED']
const CANCELLABLE = new Set<TaskStatus>(['PENDING', 'QUEUED', 'RUNNING', 'RETRYING'])
const RETRYABLE = new Set<TaskStatus>(['FAILED', 'TIMED_OUT'])

export function TaskConsolePage() {
  const [searchParams] = useSearchParams(); const linkedProjectId = Number(searchParams.get('projectId')) || null; const linkedTaskId = Number(searchParams.get('taskId')) || null
  const { t, i18n } = useTranslation(); const { api, token, user } = useAuth(); const locale = i18n.resolvedLanguage as SupportedLocale
  const [projects, setProjects] = useState<Project[]>([]); const [projectId, setProjectId] = useState<number | null>(null)
  const [status, setStatus] = useState<TaskStatus | ''>(''); const [page, setPage] = useState(0); const [data, setData] = useState<TaskPage | null>(null)
  const [selected, setSelected] = useState<Task | null>(null); const [timeline, setTimeline] = useState<TaskTimeline | null>(null)
  const [loading, setLoading] = useState(true); const [action, setAction] = useState<'cancel' | 'retry' | null>(null); const [error, setError] = useState<string | null>(null)
  const [live, setLive] = useState(false); const refreshTimer = useRef<number | null>(null)
  const [, setClock] = useState(0)

  useEffect(() => { const timer = window.setInterval(() => setClock((value) => value + 1), 1_000); return () => window.clearInterval(timer) }, [])

  useEffect(() => { listProjects(api).then((items) => { setProjects(items); setProjectId((current) => current ?? linkedProjectId ?? items[0]?.id ?? null) }).catch(setFailure) }, [api, linkedProjectId])

  useEffect(() => {
    if (!linkedTaskId) return
    void Promise.all([getTask(api, linkedTaskId), getTimeline(api, linkedTaskId)]).then(([task, events]) => { setProjectId(task.projectId); setSelected(task); setTimeline(events) }).catch(setFailure)
  }, [api, linkedTaskId])

  const load = useCallback(async (quiet = false) => {
    if (!projectId) { setData(null); setLoading(false); return }
    if (!quiet) setLoading(true); setError(null)
    try {
      const next = await listTasks(api, projectId, status, page)
      setData(next)
      if (selected) {
        const updated = next.items.find((task) => task.id === selected.id)
        if (updated) setSelected(updated)
      }
    } catch (failure) { setFailure(failure) } finally { if (!quiet) setLoading(false) }
  }, [api, projectId, status, page, selected?.id])
  useEffect(() => { void load() }, [projectId, status, page])

  const loadDetails = useCallback(async (task: Task) => {
    setSelected(task); setTimeline(null); setError(null)
    try { const [fresh, events] = await Promise.all([getTask(api, task.id), getTimeline(api, task.id)]); setSelected(fresh); setTimeline(events) }
    catch (failure) { setFailure(failure) }
  }, [api])

  useEffect(() => {
    if (!projectId || !token) return
    const controller = new AbortController(); let stopped = false
    const connect = async () => {
      while (!stopped) {
        try {
          setLive(true)
          await streamSse(`/api/v1/projects/${projectId}/tasks/events`, { baseUrl: API_BASE_URL, token, signal: controller.signal, onMessage: (message) => {
            const event = JSON.parse(message.data) as TaskLifecycleEvent
            setData((current) => current ? { ...current, items: current.items.map((task) => task.id === event.taskId ? applyEvent(task, event) : task) } : current)
            if (selected?.id === event.taskId) {
              if (refreshTimer.current) window.clearTimeout(refreshTimer.current)
              refreshTimer.current = window.setTimeout(() => void Promise.all([getTask(api, event.taskId).then(setSelected), getTimeline(api, event.taskId).then(setTimeline)]), 250)
            }
          } })
        } catch (failure) {
          if (controller.signal.aborted) return
          setLive(false)
          if (failure instanceof ApiError && failure.code === 'UNAUTHORIZED') return
          await new Promise((resolve) => window.setTimeout(resolve, 2_000))
        }
      }
    }
    void connect()
    return () => { stopped = true; controller.abort(); if (refreshTimer.current) window.clearTimeout(refreshTimer.current) }
  }, [projectId, token, api, selected?.id])

  const runAction = async (kind: 'cancel' | 'retry') => {
    if (!selected) return
    setAction(kind); setError(null)
    try { const updated = kind === 'cancel' ? await cancelTask(api, selected.id) : await requeueTask(api, selected.id); setSelected(updated); await load(true); setTimeline(await getTimeline(api, updated.id)) }
    catch (failure) { setFailure(failure) } finally { setAction(null) }
  }
  const setFailure = (failure: unknown) => setError(failure instanceof ApiError ? t(`errors.${failure.code === 'FORBIDDEN' ? 'forbidden' : failure.code === 'NOT_FOUND' ? 'notFound' : failure.code === 'NETWORK_ERROR' ? 'network' : 'server'}`) : t('errors.unknown'))
  const choose = (task: Task) => void loadDetails(task)
  const selectWithPointer = (event: PointerEvent<HTMLElement>, task: Task) => { if (event.pointerType || event.isPrimary) choose(task) }
  const selectWithKeyboard = (event: KeyboardEvent<HTMLElement>, task: Task) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); choose(task) } }
  const totalPages = Math.max(1, data?.totalPages ?? 1)
  const projectName = useMemo(() => projects.find((item) => item.id === projectId)?.name, [projects, projectId])

  return <div className="task-console">
    <header className="page-heading"><div><span>RUNTIME / TASKS</span><h1>{t('task.title')}</h1><p>{t('task.subtitle')}</p></div><div className={`live-state ${live ? 'connected' : ''}`}><i />{live ? t('task.live') : t('task.reconnecting')}</div></header>
    <section className="task-toolbar" aria-label={t('task.title')}>
      <label>{t('task.project')}<select value={projectId ?? ''} onChange={(event) => { setProjectId(Number(event.target.value) || null); setPage(0); setSelected(null) }}><option value="">{t('task.selectProject')}</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select></label>
      <label>{t('task.allStatuses')}<select value={status} onChange={(event) => { setStatus(event.target.value as TaskStatus | ''); setPage(0) }}><option value="">{t('task.allStatuses')}</option>{STATUSES.map((item) => <option key={item} value={item}>{t(`task.status.${item}`)}</option>)}</select></label>
      <button className="secondary-button" onClick={() => void load()}>{t('task.refresh')}</button>
    </section>
    {error && <div className="inline-error" role="alert">{error}</div>}
    <div className="task-layout">
      <section className="task-list-panel" aria-busy={loading}>
        <div className="list-meta"><strong>{projectName ?? t('task.selectProject')}</strong><span>{data?.totalElements ?? 0}</span></div>
        {loading ? <div className="task-empty">{t('common.loading')}</div> : !data?.items.length ? <div className="task-empty">{t('task.empty')}</div> : <div className="task-list">
          {data.items.map((task) => <article key={task.id} className={`task-row ${selected?.id === task.id ? 'selected' : ''}`} role="button" tabIndex={0} onPointerUp={(event) => selectWithPointer(event, task)} onKeyDown={(event) => selectWithKeyboard(event, task)}>
            <div className="task-id">#{task.id}</div><div className="task-main"><strong>{task.taskType}</strong><span>{formatDateTime(task.updatedAt, locale)}</span>{task.progress && <div className="mini-progress"><i style={{ width: `${task.progress.percent}%` }} /></div>}</div>
            <div className="task-row-meta"><StatusBadge status={task.status} /><span>P{task.priority}</span><span>{task.attemptCount}/{task.maxAttempts}</span><span>{formatDuration(task)}</span></div>
          </article>)}
        </div>}
        <footer className="pagination"><button disabled={page <= 0} onClick={() => setPage((value) => value - 1)}>{t('task.previous')}</button><span>{t('task.page', { current: page + 1, total: totalPages })}</span><button disabled={page + 1 >= totalPages} onClick={() => setPage((value) => value + 1)}>{t('task.next')}</button></footer>
      </section>
      <aside className="task-detail-panel">
        {!selected ? <div className="task-empty">{t('task.noSelection')}</div> : <>
          <header><div><span>#{selected.id}</span><h2>{selected.taskType}</h2></div><StatusBadge status={selected.status} /></header>
          <dl className="task-facts"><div><dt>{t('task.priority')}</dt><dd>{selected.priority}</dd></div><div><dt>{t('task.attempts')}</dt><dd>{selected.attemptCount} / {selected.maxAttempts}</dd></div><div><dt>{t('task.duration')}</dt><dd>{formatDuration(selected)}</dd></div><div><dt>{t('task.updated')}</dt><dd>{formatDateTime(selected.updatedAt, locale)}</dd></div></dl>
          {selected.progress && <section className="detail-section"><h3>{t('task.progress')} · {selected.progress.percent}%</h3><div className="progress-track"><i style={{ width: `${selected.progress.percent}%` }} /></div><p>{selected.progress.message}</p></section>}
          {selected.error && <section className="detail-section error-box"><h3>{t('task.error')}</h3><strong>{selected.error.category} / {selected.error.code}</strong><p>{selected.error.message}</p></section>}
          {selected.diagnostic && <section className="detail-section diagnostic-box"><h3>{selected.diagnostic.title}</h3><p>{selected.diagnostic.message}</p>{selected.diagnostic.actionLabel && <small>{selected.diagnostic.actionLabel}</small>}</section>}
          {selected.result && <section className="detail-section"><h3>{t('task.result')}</h3><p>{selected.result.outputPath ?? selected.result.contentType ?? 'OK'}</p></section>}
          <div className="task-actions">{CANCELLABLE.has(selected.status) && <button className="danger-button" disabled={action !== null || !!selected.cancellationRequestedAt} onClick={() => void runAction('cancel')}>{action === 'cancel' ? t('task.cancelling') : t('task.cancel')}</button>}{user?.role === 'ADMIN' && RETRYABLE.has(selected.status) && <button disabled={action !== null} onClick={() => void runAction('retry')}>{action === 'retry' ? t('task.retrying') : t('task.retry')}</button>}</div>
          <section className="timeline"><h3>{t('task.timeline')}</h3>{!timeline ? <p>{t('task.loadingTimeline')}</p> : timeline.events.map((event) => <article key={event.id}><i /><div><strong>{event.type}</strong><time>{formatDateTime(event.createdAt, locale)}</time>{(event.fromStatus || event.toStatus) && <p>{event.fromStatus ?? '—'} → {event.toStatus ?? '—'}</p>}{event.actorUserId && <small>{t('task.eventActor', { id: event.actorUserId })}</small>}</div></article>)}</section>
        </>}
      </aside>
    </div>
  </div>
}

function StatusBadge({ status }: { status: TaskStatus }) { const { t } = useTranslation(); return <span className={`status-badge status-${status.toLowerCase()}`}>{t(`task.status.${status}`)}</span> }
function applyEvent(task: Task, event: TaskLifecycleEvent): Task { return { ...task, status: event.status, attemptCount: event.attemptCount, updatedAt: event.occurredAt, completedAt: ['COMPLETED','FAILED','TIMED_OUT','CANCELLED'].includes(event.status) ? event.occurredAt : task.completedAt, durationMs: event.details.durationMs ? Number(event.details.durationMs) : task.durationMs, progress: event.details.progressPercent ? { percent: Number(event.details.progressPercent), message: event.details.progressMessage, updatedAt: event.occurredAt } : task.progress } }
function formatDuration(task: Task) { const elapsed = task.durationMs ?? (task.startedAt && task.status === 'RUNNING' ? Math.max(0, Date.now() - Date.parse(task.startedAt)) : null); if (elapsed == null) return '—'; if (elapsed < 1_000) return `${elapsed} ms`; if (elapsed < 60_000) return `${(elapsed / 1_000).toFixed(elapsed < 10_000 ? 1 : 0)}s`; const seconds = Math.floor(elapsed / 1_000); const hours = Math.floor(seconds / 3_600); const minutes = Math.floor((seconds % 3_600) / 60); const tail = `${String(minutes).padStart(2,'0')}:${String(seconds % 60).padStart(2,'0')}`; return hours ? `${String(hours).padStart(2,'0')}:${tail}` : tail }
