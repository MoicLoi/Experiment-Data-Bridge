import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { formatDateTime } from '../../i18n/formatters'
import type { SupportedLocale } from '../../i18n/resources'
import { ApiError } from '../../lib/api'
import { createDemoProject, listProjects } from '../tasks/taskApi'
import type { Project } from '../tasks/types'
import { SchemaForm } from '../workflows/SchemaForm'
import type { JsonSchema, JsonValue } from '../workflows/types'
import { analysisHandlers, artifacts, conversionTargets, downloadDataFile, getDataFile, listDataFiles, reportDefinitions, ResumableUploadController, submitAnalysis, submitConversion, submitReport, tabularProfile, type ResumableUploadProgress } from './dataFileApi'
import type { AnalysisHandler, ArtifactGroups, ConversionCapability, DataFile, DataFileTabularProfile, ReportDefinition, StructuredParameterValues } from './types'
import { StatisticsParameterForm } from './StatisticsParameterForm'
import { fleet, fleetAction } from '../admin/adminApi'

type Operation = 'convert' | 'analysis' | 'report'
type ArtifactKind = keyof ArtifactGroups
const EMPTY_ARTIFACTS: ArtifactGroups = { conversions: [], analyses: [], reports: [] }

export function DataFileWorkbenchPage() {
  const [searchParams] = useSearchParams(); const linkedProjectId = Number(searchParams.get('projectId')) || null; const linkedDataFileId = Number(searchParams.get('dataFileId')) || null
  const { t, i18n } = useTranslation(); const { api, user } = useAuth(); const locale = i18n.resolvedLanguage as SupportedLocale
  const zh = locale?.toLowerCase().startsWith('zh')
  const inputRef = useRef<HTMLInputElement>(null)
  const uploadController=useRef<ResumableUploadController|null>(null)
  const [projects, setProjects] = useState<Project[]>([]); const [projectId, setProjectId] = useState<number | null>(null)
  const [files, setFiles] = useState<DataFile[]>([]); const [selected, setSelected] = useState<DataFile | null>(null)
  const [targets, setTargets] = useState<ConversionCapability[]>([]); const [handlers, setHandlers] = useState<AnalysisHandler[]>([]); const [definitions, setDefinitions] = useState<ReportDefinition[]>([])
  const [derived, setDerived] = useState<ArtifactGroups>(EMPTY_ARTIFACTS); const [artifactKind, setArtifactKind] = useState<ArtifactKind>('conversions')
  const [operation, setOperation] = useState<Operation>('convert'); const [capabilityId, setCapabilityId] = useState(''); const [parameters, setParameters] = useState(''); const [priority, setPriority] = useState(50)
  const [parameterValues, setParameterValues] = useState<StructuredParameterValues>({}); const [advancedParameters, setAdvancedParameters] = useState(false); const [parameterDraft, setParameterDraft] = useState('{}'); const [parameterJsonError, setParameterJsonError] = useState<string | null>(null)
  const [tableProfile,setTableProfile]=useState<DataFileTabularProfile|null>(null)
  const [statisticsConfirmation,setStatisticsConfirmation]=useState(false)
  const [workerPrompt,setWorkerPrompt]=useState(false); const [workerStarting,setWorkerStarting]=useState(false)
  const [loading, setLoading] = useState(true); const [detailLoading, setDetailLoading] = useState(false); const [uploading, setUploading] = useState(false); const [submitting, setSubmitting] = useState(false)
  const [uploadProgress,setUploadProgress]=useState<ResumableUploadProgress|null>(null);const [uploadFile,setUploadFile]=useState<File|null>(null)
  const [cleanPrompt, setCleanPrompt] = useState<DataFile | null>(null); const [autoCleaning, setAutoCleaning] = useState(false)
  const [error, setError] = useState<string | null>(null); const [notice, setNotice] = useState<string | null>(null)
  const quickProject = async () => { setLoading(true); setError(null); try { const created = await createDemoProject(api); setProjects([created]); setProjectId(created.id) } catch (failure) { fail(failure) } finally { setLoading(false) } }

  const fail = useCallback((failure: unknown) => setError(failure instanceof ApiError ? t(`errors.${failure.code === 'FORBIDDEN' ? 'forbidden' : failure.code === 'NOT_FOUND' ? 'notFound' : failure.code === 'NETWORK_ERROR' ? 'network' : 'server'}`) : failure instanceof Error ? failure.message : t('errors.unknown')), [t])
  useEffect(() => { listProjects(api).then((items) => { setProjects(items); setProjectId(linkedProjectId ?? items[0]?.id ?? null) }).catch(fail) }, [api, fail, linkedProjectId])

  const loadFiles = useCallback(async () => {
    if (!projectId) { setFiles([]); setLoading(false); return }
    setLoading(true); setError(null)
    try { setFiles(await listDataFiles(api, projectId)) } catch (failure) { fail(failure) } finally { setLoading(false) }
  }, [api, projectId, fail])
  useEffect(() => { setSelected(null); setDerived(EMPTY_ARTIFACTS); void loadFiles() }, [loadFiles])
  useEffect(() => {
    if (!projectId) { setHandlers([]); setDefinitions([]); return }
    Promise.all([analysisHandlers(api, projectId), reportDefinitions(api, projectId)]).then(([nextHandlers, nextDefinitions]) => { setHandlers(nextHandlers); setDefinitions(nextDefinitions) }).catch(fail)
  }, [api, projectId, fail])

  const choose = useCallback(async (file: DataFile) => {
    setSelected(file); setDetailLoading(true); setError(null); setNotice(null)
    try {
      const [fresh, nextTargets, nextArtifacts] = await Promise.all([getDataFile(api, file.projectId, file.id), conversionTargets(api, file), artifacts(api, file)])
      setSelected(fresh); setTargets(nextTargets); setDerived(nextArtifacts)
    } catch (failure) { fail(failure) } finally { setDetailLoading(false) }
  }, [api, fail])
  const chooseWithPointer = (event: PointerEvent<HTMLElement>, file: DataFile) => { if (event.isPrimary) void choose(file) }
  const chooseWithKeyboard = (event: KeyboardEvent<HTMLElement>, file: DataFile) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); void choose(file) } }
  useEffect(() => {
    if (!linkedDataFileId || !projectId) return
    const linked = files.find((file) => file.id === linkedDataFileId)
    if (linked && selected?.id !== linked.id) void choose(linked)
  }, [linkedDataFileId, projectId, files, selected?.id, choose])

  const upload = async (file?: File) => {
    if (!file || !projectId) return
    setUploadFile(file);const controller=new ResumableUploadController(api,projectId,file,setUploadProgress);uploadController.current=controller
    setUploading(true); setError(null); setNotice(null)
    try { const created = await controller.start(); setNotice(t('datafile.uploadSuccess'));setUploadProgress(null);setUploadFile(null);uploadController.current=null; await loadFiles(); await choose(created); if (['CSV', 'JSON', 'XLSX'].includes(created.format.toUpperCase())) setCleanPrompt(created) }
    catch (failure) { if(!(failure instanceof DOMException&&failure.name==='AbortError'))fail(failure) } finally { setUploading(false); if(!uploadController.current&&inputRef.current) inputRef.current.value = '' }
  }
  const resumeUpload=async()=>{if(!uploadFile||!projectId)return;setUploading(true);setError(null);try{const controller=uploadController.current??new ResumableUploadController(api,projectId,uploadFile,setUploadProgress);uploadController.current=controller;const created=await controller.start();setNotice(t('datafile.uploadSuccess'));setUploadProgress(null);setUploadFile(null);uploadController.current=null;await loadFiles();await choose(created)}catch(failure){if(!(failure instanceof DOMException&&failure.name==='AbortError'))fail(failure)}finally{setUploading(false)}}
  const autoClean = async () => {
    if (!cleanPrompt) return
    const handler = handlers.find((item) => item.parameterContract?.id === 'tabular-clean')
    if (!handler) { setCleanPrompt(null); setError(zh ? '当前没有可用的表格清洗 Handler' : 'No tabular cleaning handler is currently available'); return }
    setAutoCleaning(true); setError(null)
    try {
      const outputFormat = cleanPrompt.format.toUpperCase()
      const values: StructuredParameterValues = { outputFormat, trimStrings: true, normalizeCase: 'none', normalizeColumns: '', dropMissingColumns: '', duplicateKeyColumns: '', numericColumn: '', numericMin: 0, numericMax: 0 }
      const task = await submitAnalysis(api, cleanPrompt, handler.id, {}, priority, values)
      setNotice(zh ? `自动清洗任务 #${task.id} 已提交` : `Auto-clean task #${task.id} submitted`); setCleanPrompt(null)
    } catch (failure) { fail(failure) } finally { setAutoCleaning(false) }
  }
  const options = useMemo(() => operation === 'convert' ? targets.map((item) => ({ id: item.targetFormat, label: item.targetFormat })) : operation === 'analysis' ? handlers.map((item) => ({ id: item.id, label: `${item.analysisType} · ${item.tool}` })) : definitions.map((item) => ({ id: item.id, label: `${item.title} · v${item.version}` })), [operation, targets, handlers, definitions])
  const selectedHandler = useMemo(() => operation === 'analysis' ? handlers.find((item) => item.id === capabilityId) : undefined, [operation, handlers, capabilityId])
  const parameterContract = selectedHandler?.parameterContract ?? null
  useEffect(() => { setCapabilityId(options[0]?.id ?? ''); setParameters(''); setAdvancedParameters(false) }, [operation, selected?.id, options.map((item) => item.id).join('|')])
  useEffect(() => {
    const values = parameterContract ? schemaDefaults(parameterContract.schema) : {}
    setParameterValues(values); setParameterDraft(JSON.stringify(values, null, 2)); setParameterJsonError(null)
  }, [parameterContract?.id, parameterContract?.version, capabilityId])
  useEffect(()=>{setTableProfile(null);if(selected&&parameterContract?.id==='tabular-statistics')tabularProfile(api,selected).then(setTableProfile).catch(fail)},[api,selected?.id,parameterContract?.id,fail])

  const updateParameterValues = (values: StructuredParameterValues) => {
    setParameterValues(values); setParameterDraft(JSON.stringify(values, null, 2)); setParameterJsonError(null)
  }
  const updateParameterJson = (source: string) => {
    setParameterDraft(source)
    try {
      const parsed = JSON.parse(source) as JsonValue
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error(t('datafile.parameterObjectRequired'))
      setParameterValues(parsed as StructuredParameterValues); setParameterJsonError(null)
    } catch (failure) { setParameterJsonError(failure instanceof Error ? failure.message : t('datafile.parameterJsonInvalid')) }
  }

  const submitDirect = async () => {
    if (!selected || !capabilityId) return
    setSubmitting(true); setError(null); setNotice(null)
    try {
      const values = parseParameters(parameters)
      if (parameterContract && parameterJsonError) throw new Error(t('datafile.parameterJsonInvalid'))
      const task = operation === 'convert' ? await submitConversion(api, selected, capabilityId, values, priority) : operation === 'analysis' ? await submitAnalysis(api, selected, capabilityId, values, priority, parameterContract ? parameterValues : undefined) : await submitReport(api, selected, capabilityId, values, priority)
      setNotice(t('datafile.submitted', { id: task.id }))
    } catch (failure) { fail(failure) } finally { setSubmitting(false) }
  }
  const preflightAndSubmit = async () => {
    if (operation !== 'analysis' || selectedHandler?.tool !== 'python-http' || user?.role !== 'ADMIN') { await submitDirect(); return }
    setError(null)
    try { const state = await fleet(api); if (state.members.some((member) => member.id === 'python' && member.healthy)) await submitDirect(); else setWorkerPrompt(true) }
    catch (failure) { fail(failure) }
  }
  const startWorkerAndContinue = async () => {
    setWorkerStarting(true); setError(null)
    try {
      const state = await fleetAction(api, 'start')
      if (!state.members.some((member) => member.id === 'python' && member.healthy)) throw new Error(zh ? 'Python Worker 已启动，但尚未通过健康检查。请查看 Docker Fleet 日志。' : 'The Python Worker started but did not become healthy. Review Docker Fleet logs.')
      setWorkerPrompt(false); await submitDirect()
    } catch (failure) { fail(failure) } finally { setWorkerStarting(false) }
  }
  const requestSubmit = () => parameterContract?.id === 'tabular-statistics' ? setStatisticsConfirmation(true) : void preflightAndSubmit()
  const submitNow = preflightAndSubmit
  const operationLabel = (kind: Operation) => t(`datafile.${kind === 'convert' ? 'convert' : kind}`)

  return <div className="datafile-workbench">
    <header className="page-heading"><div><span>DATA / ARTIFACTS</span><h1>{t('datafile.title')}</h1><p>{t('datafile.subtitle')}</p></div></header>
    <section className="datafile-toolbar">
      <label>{t('datafile.project')}<select value={projectId ?? ''} onChange={(event) => setProjectId(Number(event.target.value) || null)}><option value="">—</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select></label>
      <input ref={inputRef} className="visually-hidden" type="file" onChange={(event) => void upload(event.target.files?.[0])} />
      {projects.length === 0 ? <button disabled={loading} onClick={() => void quickProject()}>Create test project</button> : <button disabled={!projectId || uploading} onClick={() => inputRef.current?.click()}>{uploading ? t('datafile.uploading') : t('datafile.upload')}</button>}
      {uploadProgress&&<div className="upload-progress" role="status"><progress max={100} value={uploadProgress.percent}/><span>{uploadProgress.percent}%</span>{uploading?<button type="button" onClick={()=>uploadController.current?.pause()}>{zh?'暂停':'Pause'}</button>:uploadFile?<button type="button" onClick={()=>void resumeUpload()}>{zh?'继续':'Resume'}</button>:null}</div>}
      <button className="secondary-button" disabled={!projectId || loading} onClick={() => void loadFiles()}>{t('task.refresh')}</button>
    </section>
    {error && <div className="inline-error" role="alert">{error}</div>}{notice && <div className="inline-notice" role="status">{notice} <Link to="/tasks">{t('nav.tasks')} →</Link></div>}
    {statisticsConfirmation&&<div className="statistics-confirm-overlay" role="presentation"><section role="dialog" aria-modal="true" aria-labelledby="statistics-confirm-title"><span>STATISTICAL REVIEW</span><h2 id="statistics-confirm-title">{zh?'提交统计分析前请确认':'Review before submitting'}</h2><p>{zh?'常用模板及其预填参数基于常见的大数据分析场景，不代表绝对真实，也不保证适用于当前实验设计。':'Template parameters are based on common large-dataset scenarios. They are not guaranteed to reflect the actual study or be appropriate for its design.'}</p><ul><li>{zh?'请确认分组列、比较组和数值列选择正确。':'Verify the grouping, comparison groups, and numeric columns.'}</li><li>{zh?'请结合样本独立性、分布、缺失值和研究假设选择方法。':'Consider independence, distribution, missing values, and the research hypothesis.'}</li><li>{zh?'结果应由具备相应背景的人员复核后再用于科研结论。':'Have a qualified reviewer check results before using them in research conclusions.'}</li></ul><div><button type="button" className="secondary-button" onClick={()=>setStatisticsConfirmation(false)}>{zh?'返回检查':'Review parameters'}</button><button type="button" onClick={()=>{setStatisticsConfirmation(false);void submitNow()}}>{zh?'已确认，提交任务':'Confirm and submit'}</button></div></section></div>}
    {cleanPrompt && <div className="clean-prompt-overlay" role="presentation"><section role="dialog" aria-modal="true" aria-labelledby="clean-prompt-title"><span>AUTO CLEAN</span><h2 id="clean-prompt-title">{zh ? '是否自动清洗这份数据？' : 'Automatically clean this data?'}</h2><p>{zh ? `已导入“${cleanPrompt.originalFileName}”。可以立即创建一份安全清洗任务。` : `“${cleanPrompt.originalFileName}” was imported. You can create a safe cleaning task now.`}</p><small>{zh ? '默认策略仅去除文本首尾空白并生成派生文件；不会删除缺失行、去重、改写大小写或过滤数值。' : 'The default policy only trims surrounding whitespace and creates a derived file. It does not remove missing rows, deduplicate, change case, or filter numeric values.'}</small><div><button type="button" className="secondary-button" disabled={autoCleaning} onClick={() => setCleanPrompt(null)}>{zh ? '暂不清洗' : 'Not now'}</button><button type="button" disabled={autoCleaning} onClick={() => void autoClean()}>{autoCleaning ? (zh ? '正在提交…' : 'Submitting…') : (zh ? '自动清洗' : 'Auto-clean')}</button></div></section></div>}
    {workerPrompt&&<div className="statistics-confirm-overlay" role="presentation"><section role="dialog" aria-modal="true" aria-labelledby="worker-preflight-title"><span>WORKER PREFLIGHT</span><h2 id="worker-preflight-title">{zh?'Python Worker 未运行':'Python Worker is not running'}</h2><p>{zh?'所选分析依赖 Python Worker。启动既有托管 Worker 并在健康检查通过后继续；系统不会修改镜像或配置。':'This analysis requires the Python Worker. Start the configured managed Worker and continue after its health check passes. No image or configuration will be changed.'}</p><div><button type="button" className="secondary-button" disabled={workerStarting} onClick={()=>setWorkerPrompt(false)}>{zh?'取消':'Cancel'}</button><button type="button" disabled={workerStarting} onClick={()=>void startWorkerAndContinue()}>{workerStarting?(zh?'正在启动并检查…':'Starting and checking…'):(zh?'启动 Worker 并继续':'Start Worker and continue')}</button></div></section></div>}
    <div className="datafile-layout">
      <section className="datafile-list-panel" aria-busy={loading}>
        <div className="list-meta"><strong>{t('datafile.files')}</strong><span>{files.length}</span></div>
        {loading ? <div className="task-empty">{t('common.loading')}</div> : files.length === 0 ? <div className="task-empty">{t('datafile.empty')}</div> : <div className="datafile-list">{files.map((file) => <FileRow key={file.id} file={file} selected={selected?.id === file.id} locale={locale} onPointer={chooseWithPointer} onKeyboard={chooseWithKeyboard} />)}</div>}
      </section>
      <section className="datafile-detail-panel">
        {!selected ? <div className="task-empty">{t('datafile.selectFile')}</div> : <>
          <header><div><span>#{selected.id} · {selected.kind}</span><h2>{selected.originalFileName}</h2></div><span className="file-format">{selected.format}</span></header>
          <div className="datafile-actions"><button onClick={() => void downloadDataFile(api, selected).then(() => setNotice(t('datafile.downloadStarted'))).catch(fail)}>{t('datafile.download')}</button></div>
          <dl className="datafile-facts"><Fact label={t('datafile.status')} value={selected.status} /><Fact label={t('datafile.size')} value={formatBytes(selected.sizeBytes)} /><Fact label={t('datafile.created')} value={formatDateTime(selected.createdAt, locale)} /><Fact label={t('datafile.origin')} value={selected.origin} /><Fact label={t('datafile.checksum')} value={selected.sha256 ?? '—'} /><Fact label={t('datafile.storage')} value={selected.storageProvider ?? '—'} /></dl>
          <section className="detail-section"><h3>{t('datafile.metadata')}</h3><pre>{JSON.stringify(selected.metadata ?? {}, null, 2)}</pre></section>
          <section className="datafile-operations" aria-busy={detailLoading}><h3>{t('datafile.operations')}</h3><div className="segmented" role="tablist">{(['convert', 'analysis', 'report'] as Operation[]).map((kind) => <button key={kind} role="tab" aria-selected={operation === kind} onClick={() => setOperation(kind)}>{operationLabel(kind)}</button>)}</div>
            <label>{operation === 'convert' ? t('datafile.targetFormat') : operation === 'analysis' ? t('datafile.handler') : t('datafile.definition')}<select value={capabilityId} onChange={(event) => setCapabilityId(event.target.value)} disabled={!options.length}><option value="">{t('datafile.unavailable')}</option>{options.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}</select></label>
            {parameterContract ? <section className="algorithm-parameters"><header><div><strong>{parameterContract.title}</strong>{parameterContract.description && <p>{parameterContract.description}</p>}</div><button type="button" className="secondary-button" aria-pressed={advancedParameters} onClick={() => { setAdvancedParameters((value) => !value); setParameterDraft(JSON.stringify(parameterValues, null, 2)); setParameterJsonError(null) }}>{advancedParameters ? t('datafile.formMode') : t('datafile.advancedJson')}</button></header>{advancedParameters ? <label>{t('datafile.advancedJson')}<textarea className="parameter-json" value={parameterDraft} onChange={(event) => updateParameterJson(event.target.value)} rows={12} />{parameterJsonError && <small className="parameter-error" role="alert">{parameterJsonError}</small>}</label> : parameterContract.id==='tabular-statistics'?<StatisticsParameterForm profile={tableProfile} value={parameterValues} onChange={updateParameterValues}/>:<SchemaForm schema={parameterContract.schema} value={parameterValues} onChange={updateParameterValues} />}</section> : <label>{t('datafile.parameters')}<textarea value={parameters} onChange={(event) => setParameters(event.target.value)} placeholder={t('datafile.parameterHint')} rows={4} /></label>}
            <label>{t('datafile.priority')}<input type="number" min={0} max={100} value={priority} onChange={(event) => setPriority(Math.max(0, Math.min(100, Number(event.target.value))))} /></label>
            <button disabled={!capabilityId || submitting || detailLoading} onClick={requestSubmit}>{submitting ? t('datafile.submitting') : t('datafile.submit')}</button>
          </section>
          <section className="artifact-section"><h3>{t('datafile.artifacts')}</h3><div className="segmented" role="tablist">{(['conversions', 'analyses', 'reports'] as ArtifactKind[]).map((kind) => <button key={kind} role="tab" aria-selected={artifactKind === kind} onClick={() => setArtifactKind(kind)}>{t(`datafile.${kind}`)} <span>{derived[kind].length}</span></button>)}</div>{derived[artifactKind].length === 0 ? <p>{t('datafile.noArtifacts')}</p> : <div className="artifact-list">{derived[artifactKind].map((file) => <FileRow key={file.id} file={file} selected={false} locale={locale} compact onPointer={chooseWithPointer} onKeyboard={chooseWithKeyboard} />)}</div>}</section>
        </>}
      </section>
    </div>
  </div>
}

function FileRow({ file, selected, locale, compact, onPointer, onKeyboard }: { file: DataFile; selected: boolean; locale: SupportedLocale; compact?: boolean; onPointer: (event: PointerEvent<HTMLElement>, file: DataFile) => void; onKeyboard: (event: KeyboardEvent<HTMLElement>, file: DataFile) => void }) {
  return <article className={`datafile-row ${selected ? 'selected' : ''} ${compact ? 'compact' : ''}`} role="button" tabIndex={0} onPointerUp={(event) => onPointer(event, file)} onKeyDown={(event) => onKeyboard(event, file)}><span className="file-icon">{file.format.slice(0, 4)}</span><div><strong>{file.originalFileName}</strong><small>#{file.id} · {formatDateTime(file.createdAt, locale)}</small></div><div><span>{formatBytes(file.sizeBytes)}</span><em>{file.status}</em></div></article>
}
function Fact({ label, value }: { label: string; value: string }) { return <div><dt>{label}</dt><dd>{value}</dd></div> }
function formatBytes(value?: number | null) { if (value == null) return '—'; if (value < 1024) return `${value} B`; if (value < 1024 ** 2) return `${(value / 1024).toFixed(1)} KB`; if (value < 1024 ** 3) return `${(value / 1024 ** 2).toFixed(1)} MB`; return `${(value / 1024 ** 3).toFixed(1)} GB` }
function parseParameters(source: string) { return source.split(/\r?\n/).reduce<Record<string, string>>((result, line) => { const separator = line.indexOf('='); if (separator > 0) result[line.slice(0, separator).trim()] = line.slice(separator + 1).trim(); return result }, {}) }
function schemaDefaults(schema: JsonSchema): StructuredParameterValues {
  return Object.entries(schema.properties ?? {}).reduce<StructuredParameterValues>((result, [name, field]) => {
    if (field.default !== undefined) result[name] = field.default
    return result
  }, {})
}
