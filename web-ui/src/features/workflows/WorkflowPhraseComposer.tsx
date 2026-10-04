import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../auth/AuthProvider'
import { analysisHandlers } from '../datafiles/dataFileApi'
import type { AnalysisHandler } from '../datafiles/types'
import { listProjects } from '../tasks/taskApi'
import type { Project } from '../tasks/types'
import type { JsonSchema, JsonValue, PhraseDefinition, PhrasePattern, PhrasePatternToken, StepCatalogItem, WorkflowIr } from './types'
import { deletePhraseDefinitionVersion, getStepSchema, listPhraseDefinitions, renamePhraseDefinition, savePhraseDefinition, submitPhraseDefinition } from './workflowApi'
import { SchemaForm } from './SchemaForm'
import { ACTIONS, CAPABILITY_KINDS, CONVERSION_TARGETS, PARAMETER_MODES, type PhraseEntry } from './workflowPhraseLexicon'

type Kind = 'convert' | 'analysis' | 'step'
type ParameterMode = 'template' | 'custom'

interface DraftAction {
  id: number
  kind: Kind
  target: string
  handlerId: string
  mode: ParameterMode
  stepType: string
  parameters: Record<string, JsonValue>
  patternId?: string
  enabledOptionalTokens?: string[]
}

interface PhraseSnapshot { id: string; name: string; createdAt: string; actions: Array<Omit<DraftAction, 'id'>> }

export interface ComposedWorkflowAction {
  item: StepCatalogItem
  parameters: Record<string, JsonValue>
}

let nextActionId = 1
const DRAFT_KEY = 'edb.workflow.phrase.draft.v1'
const SNAPSHOT_KEY = 'edb.workflow.phrase.snapshots.v1'
const createAction = (): DraftAction => ({ id: nextActionId++, kind: 'convert', target: 'XLSX', handlerId: '', mode: 'template', stepType: '', parameters: {} })

export function WorkflowPhraseComposer({ catalog, workflow, onCompose }: { catalog: StepCatalogItem[]; workflow: WorkflowIr; onCompose: (actions: ComposedWorkflowAction[], replaceCurrent: boolean) => void }) {
  const { t } = useTranslation()
  const { api } = useAuth()
  const [handlers, setHandlers] = useState<AnalysisHandler[]>([])
  const [actions, setActions] = useState<DraftAction[]>(loadDraftActions)
  const [schemas, setSchemas] = useState<Record<string, JsonSchema>>({})
  const [replaceCurrent, setReplaceCurrent] = useState(false)
  const [liveSync, setLiveSync] = useState(false)
  const [draftError, setDraftError] = useState('')
  const [syncConflict, setSyncConflict] = useState(false)
  const [snapshots, setSnapshots] = useState<PhraseSnapshot[]>(loadSnapshots)
  const [previewSnapshotId, setPreviewSnapshotId] = useState<string | null>(null)
  const [conflictCanvasActions, setConflictCanvasActions] = useState<DraftAction[]>([])
  const [mergeChoices, setMergeChoices] = useState<Record<number, 'actions' | 'canvas'>>({})
  const [projects, setProjects] = useState<Project[]>([])
  const [projectId, setProjectId] = useState<number | ''>('')
  const [serverDefinitions, setServerDefinitions] = useState<PhraseDefinition[]>([])
  const [serverBusy, setServerBusy] = useState(false)
  const [serverNames, setServerNames] = useState<Record<number,string>>({})
  const [submittedTaskId, setSubmittedTaskId] = useState<number|null>(null)
  const importRef = useRef<HTMLInputElement>(null)
  const actionRefs = useRef(new Map<number, HTMLElement>())
  const lastSynced = useRef(workflowSignature(workflow))
  const composeRef = useRef(onCompose)
  useEffect(() => { composeRef.current = onCompose }, [onCompose])
  const convert = catalog.find(item => item.type === 'datafile.convert')
  const analysis = catalog.find(item => item.type === 'datafile.analysis.run')
  const composable = useMemo(() => catalog.filter(item => item.type !== 'datafile.convert' && item.type !== 'datafile.analysis.run'), [catalog])

  useEffect(() => {
    listProjects(api)
      .then(projects => projects[0] ? analysisHandlers(api, projects[0].id) : [])
      .then(items => {
        setHandlers(items)
        setActions(current => current.map(action => ({
          ...action,
          handlerId: action.handlerId || items[0]?.id || '',
          stepType: action.stepType || composable[0]?.type || ''
        })))
      })
      .catch(() => setHandlers([]))
  }, [api, composable])

  useEffect(()=>{listProjects(api).then(items=>{setProjects(items);setProjectId(current=>current||items[0]?.id||'')}).catch(()=>setProjects([]))},[api])
  useEffect(()=>{if(!projectId){setServerDefinitions([]);return}listPhraseDefinitions(api,projectId).then(setServerDefinitions).catch(()=>setServerDefinitions([]))},[api,projectId])

  useEffect(() => {
    const timer = window.setTimeout(() => localStorage.setItem(DRAFT_KEY, JSON.stringify({ schemaVersion: 1, actions })), 250)
    return () => window.clearTimeout(timer)
  }, [actions])

  useEffect(() => {
    actions.forEach(action => {
      if (action.kind === 'analysis') return
      const item = action.kind === 'convert' ? convert : composable.find(entry => entry.type === action.stepType)
      if (!item) return
      const key = `${item.type}@${item.version}`
      if (schemas[key] || !item.schemaAvailable) return
      void getStepSchema(api, item).then(schema => setSchemas(current => ({ ...current, [key]: schema }))).catch(() => undefined)
    })
  }, [actions, api, composable, convert, schemas])

  const label = (entry: PhraseEntry) => t(entry.labelKey, { defaultValue: entry.defaultLabel })
  const patchAction = (id: number, patch: Partial<DraftAction>) => setActions(current => current.map(action => action.id === id ? { ...action, ...patch } : action))
  const removeAction = (id: number) => setActions(current => current.length === 1 ? current : current.filter(action => action.id !== id))
  const copyAction = (id: number) => setActions(current => {
    const index = current.findIndex(action => action.id === id)
    if (index < 0) return current
    const next = [...current]
    next.splice(index + 1, 0, { ...structuredClone(current[index]!), id: nextActionId++ })
    return next
  })
  const moveAction = (id: number, direction: -1 | 1) => setActions(current => {
    const from = current.findIndex(action => action.id === id)
    const to = from + direction
    if (from < 0 || to < 0 || to >= current.length) return current
    const next = [...current]
    ;[next[from], next[to]] = [next[to]!, next[from]!]
    return next
  })

  const metadataFor = (handler: AnalysisHandler) => handler.capability ?? analysis?.capabilities?.find(entry => entry.id === handler.id)
  const commandPath = (handler: AnalysisHandler) => metadataFor(handler)?.cliPath?.length ? metadataFor(handler)!.cliPath : ['analysis', handler.id]
  const commandCandidates = (action: DraftAction, depth: number) => {
    const selected = handlers.find(item => item.id === action.handlerId)
    const prefix = selected ? commandPath(selected).slice(0, depth) : []
    return [...new Set(handlers.filter(item => commandPath(item).slice(0, depth).every((token, index) => token === prefix[index])).map(item => commandPath(item)[depth]).filter(Boolean))] as string[]
  }
  const selectCommandToken = (action: DraftAction, depth: number, token: string) => {
    const selected = handlers.find(item => item.id === action.handlerId)
    const prefix = selected ? commandPath(selected).slice(0, depth) : []
    const candidate = handlers.find(item => {
      const path = commandPath(item)
      return path[depth] === token && prefix.every((part, index) => path[index] === part)
    })
    if (candidate) patchAction(action.id, { handlerId: candidate.id })
  }

  const valid = (action: DraftAction) => action.kind === 'convert' ? Boolean(convert) : action.kind === 'analysis' ? Boolean(analysis && action.handlerId) : Boolean(action.stepType)
  const compose = () => {
    const result = actions.map(action => toComposedAction(action, convert, analysis, composable, handlers)).filter((value): value is ComposedWorkflowAction => value !== null)
    if (result.length === actions.length) onCompose(result, replaceCurrent)
  }
  const importWorkflow = () => { setActions(workflowToDraftActions(workflow, handlers, composable)); setReplaceCurrent(true) }
  const schemaFor = (action: DraftAction): JsonSchema | undefined => {
    if (action.kind === 'analysis') return handlers.find(item => item.id === action.handlerId)?.parameterContract?.schema
    const item = action.kind === 'convert' ? convert : composable.find(entry => entry.type === action.stepType)
    return item ? schemas[`${item.type}@${item.version}`] : undefined
  }
  const itemFor = (action: DraftAction) => action.kind === 'convert' ? convert : action.kind === 'analysis' ? analysis : composable.find(entry => entry.type === action.stepType)
  const patternsFor = (action: DraftAction, item?: StepCatalogItem) => {
    if (!item) return []
    if (action.kind === 'analysis') {
      const contributed = item.capabilities.find(capability => capability.id === action.handlerId)?.phrasePatterns ?? []
      if (contributed.length) return contributed
    }
    return item.phrasePatterns
  }
  const patternFor = (action: DraftAction) => {
    const item = itemFor(action)
    const patterns = patternsFor(action, item)
    return patterns.find(pattern => pattern.id === action.patternId) ?? patterns[0]
  }
  const errorsFor = (action: DraftAction): string[] => {
    if (!valid(action)) return ['Capability selection is incomplete.']
    if (action.kind === 'analysis' && action.mode === 'template' && handlers.find(item => item.id === action.handlerId)?.parameterContract?.id === 'tabular-clean') return []
    const schema = schemaFor(action)
    if (!schema) return []
    const composed = toComposedAction(action, convert, analysis, composable, handlers)
    const value = action.kind === 'analysis' ? action.parameters : composed?.parameters ?? action.parameters
    return validatePhraseParameters(schema, value)
  }
  const exportDraft = () => downloadJson('workflow-actions.phrase.json', { schemaVersion: 1, actions: actions.map(({ id: _id, ...action }) => action) })
  const clearDraft = () => { localStorage.removeItem(DRAFT_KEY); setActions([{ ...createAction(), handlerId: handlers[0]?.id ?? '', stepType: composable[0]?.type ?? '' }]); setReplaceCurrent(false); setLiveSync(false); setDraftError('') }
  const importDraft = async (file?: File) => {
    if (!file) return
    try {
      const value = JSON.parse(await file.text()) as { schemaVersion?: number; actions?: Array<Omit<DraftAction, 'id'>> }
      if (value.schemaVersion !== 1 || !Array.isArray(value.actions) || !value.actions.length || !value.actions.every(isImportedDraftAction)) throw new Error('Invalid phrase draft schemaVersion 1 document.')
      setActions(value.actions.map(action => ({ ...action, id: nextActionId++, parameters: action.parameters ?? {} })))
      setReplaceCurrent(false); setLiveSync(false); setDraftError('')
    } catch (failure) { setDraftError(failure instanceof Error ? failure.message : String(failure)) }
    finally { if (importRef.current) importRef.current.value = '' }
  }

  const saveSnapshot = () => {
    const snapshot: PhraseSnapshot = { id: crypto.randomUUID(), name: `Snapshot ${snapshots.length + 1}`, createdAt: new Date().toISOString(), actions: actions.map(({ id: _id, ...action }) => structuredClone(action)) }
    setSnapshots(current => persistSnapshots([snapshot, ...current].slice(0, 20)))
  }
  const restoreSnapshot = (snapshot: PhraseSnapshot) => { setActions(snapshot.actions.map(action => ({ ...structuredClone(action), id: nextActionId++ }))); setLiveSync(false); setSyncConflict(false) }
  const deleteSnapshot = (id: string) => setSnapshots(current => persistSnapshots(current.filter(snapshot => snapshot.id !== id)))
  const renameSnapshot = (id: string, name: string) => { const normalized = name.trim(); if (normalized) setSnapshots(current => persistSnapshots(current.map(snapshot => snapshot.id === id ? { ...snapshot, name: normalized } : snapshot))) }
  const focusFirstError = () => { const invalid = actions.find(action => errorsFor(action).length); if (invalid) { const element = actionRefs.current.get(invalid.id); element?.scrollIntoView({ behavior: 'smooth', block: 'center' }); element?.focus({ preventScroll: true }) } }
  const focusAction = (id: number) => { const element = actionRefs.current.get(id); element?.scrollIntoView({ behavior: 'smooth', block: 'center' }); element?.focus({ preventScroll: true }) }

  useEffect(() => {
    if (!liveSync || syncConflict) return
    const composed = actions.map(action => toComposedAction(action, convert, analysis, composable, handlers)).filter((value): value is ComposedWorkflowAction => value !== null)
    if (composed.length !== actions.length || actions.some(action => errorsFor(action).length)) return
    const actionSignature = composedSignature(composed), canvasSignature = workflowSignature(workflow), baseline = lastSynced.current
    if (actionSignature === canvasSignature) { lastSynced.current = actionSignature; return }
    if (canvasSignature === baseline) { const timer = window.setTimeout(() => composeRef.current(composed, true), 350); return () => window.clearTimeout(timer) }
    if (actionSignature === baseline) { setActions(workflowToDraftActions(workflow, handlers, composable)); lastSynced.current = canvasSignature; return }
    const canvasActions = workflowToDraftActions(workflow, handlers, composable)
    setConflictCanvasActions(canvasActions); setMergeChoices({}); setSyncConflict(true)
  }, [actions, liveSync, workflow, convert, analysis, composable, handlers, schemas, syncConflict])

  const enableLiveSync = (enabled: boolean) => {
    if (!enabled) { setLiveSync(false); setSyncConflict(false); return }
    const composed = actions.map(action => toComposedAction(action, convert, analysis, composable, handlers)).filter((value): value is ComposedWorkflowAction => value !== null)
    const actionSignature = composedSignature(composed), canvasSignature = workflowSignature(workflow)
    setLiveSync(true); lastSynced.current = actionSignature === canvasSignature ? actionSignature : '__CONFLICT__'; setConflictCanvasActions(workflowToDraftActions(workflow,handlers,composable)); setMergeChoices({}); setSyncConflict(actionSignature !== canvasSignature)
  }
  const resolveConflictWithActions = () => { lastSynced.current = workflowSignature(workflow); setSyncConflict(false) }
  const resolveConflictWithCanvas = () => { setActions(workflowToDraftActions(workflow, handlers, composable)); lastSynced.current = workflowSignature(workflow); setSyncConflict(false) }
  const mergeConflict = () => { const length=Math.max(actions.length,conflictCanvasActions.length);const merged=Array.from({length},(_,index)=>mergeChoices[index]==='canvas'?conflictCanvasActions[index]:actions[index]).filter((action):action is DraftAction=>Boolean(action)).map(action=>({...structuredClone(action),id:nextActionId++}));setActions(merged.length?merged:[createAction()]);lastSynced.current=workflowSignature(workflow);setSyncConflict(false) }
  const saveServerDefinition=async()=>{if(!projectId)return;setServerBusy(true);setDraftError('');try{await savePhraseDefinition(api,projectId,workflow.id||'natural-workflow',{schemaVersion:1,actions:actions.map(({id:_id,...action})=>action as unknown as Record<string,JsonValue>)});setServerDefinitions(await listPhraseDefinitions(api,projectId))}catch(failure){setDraftError(failure instanceof Error?failure.message:String(failure))}finally{setServerBusy(false)}}
  const loadServerDefinition=(definition:PhraseDefinition)=>{const imported=definition.document.actions;if(!imported.every(isImportedDraftAction)){setDraftError('Server phrase definition has an invalid action contract.');return}setActions(imported.map(action=>({...action,id:nextActionId++})));setLiveSync(false);setReplaceCurrent(false)}
  const refreshServerDefinitions=async()=>{if(projectId)setServerDefinitions(await listPhraseDefinitions(api,projectId))}
  const renameServerDefinition=async(definition:PhraseDefinition)=>{const name=(serverNames[definition.id]??definition.definitionId).trim();if(!name||name===definition.definitionId)return;setServerBusy(true);setDraftError('');try{await renamePhraseDefinition(api,definition.projectId,definition.definitionId,name);const refreshed=projectId?await listPhraseDefinitions(api,projectId):[];setServerDefinitions(refreshed);setServerNames(current=>{const next={...current};delete next[definition.id];return next})}catch(failure){setDraftError(failure instanceof Error?failure.message:String(failure))}finally{setServerBusy(false)}}
  const deleteServerDefinition=async(definition:PhraseDefinition)=>{setServerBusy(true);try{await deletePhraseDefinitionVersion(api,definition.projectId,definition.definitionId,definition.version);await refreshServerDefinitions()}catch(failure){setDraftError(failure instanceof Error?failure.message:String(failure))}finally{setServerBusy(false)}}
  const submitServerDefinition=async(definition:PhraseDefinition)=>{setServerBusy(true);try{const task=await submitPhraseDefinition(api,definition);setSubmittedTaskId(task.id)}catch(failure){setDraftError(failure instanceof Error?failure.message:String(failure))}finally{setServerBusy(false)}}

  return <section className="phrase-composer">
    <header><div><span>NATURAL WORKFLOW</span><h2>{t('workflowPhrase.title', { defaultValue: '词条式自然语言编排' })}</h2></div><small>{t('workflowPhrase.subtitle', { defaultValue: '显示词条可翻译，提交值始终使用稳定的契约枚举。' })}</small></header>
    <div className="phrase-actions">
      {actions.map((action, index) => <article className="phrase-action" key={action.id} ref={element => { if (element) actionRefs.current.set(action.id, element); else actionRefs.current.delete(action.id) }} tabIndex={-1}>
        <header><strong>{t('workflowPhrase.actionNumber', { defaultValue: `动作 ${index + 1}`, index: index + 1 })}</strong><div><button aria-label="Copy action" onClick={() => copyAction(action.id)}>⧉</button><button aria-label="Move action up" disabled={index === 0} onClick={() => moveAction(action.id, -1)}>↑</button><button aria-label="Move action down" disabled={index === actions.length - 1} onClick={() => moveAction(action.id, 1)}>↓</button><button aria-label="Delete action" disabled={actions.length === 1} onClick={() => removeAction(action.id)}>×</button></div></header>
        {patternFor(action) ? <PatternPhraseChain action={action} item={itemFor(action)!} patterns={patternsFor(action,itemFor(action))} pattern={patternFor(action)!} handlers={handlers} label={label} patch={patch=>patchAction(action.id,patch)}/> : <div className="phrase-chain">
          <label><span>1</span><select value="execute" disabled>{ACTIONS.map(item => <option key={item.value} value={item.value}>{label(item)}</option>)}</select></label><strong>→</strong>
          <label><span>2</span><select value={action.kind} onChange={event => patchAction(action.id, { kind: event.target.value as Kind })}>{CAPABILITY_KINDS.map(item => <option key={item.value} value={item.value}>{label(item)}</option>)}</select></label><strong>→</strong>
          {action.kind === 'convert' && <label><span>3</span><select value={action.target} onChange={event => patchAction(action.id, { target: event.target.value })}>{CONVERSION_TARGETS.map(item => <option key={item.value} value={item.value}>{label(item)}</option>)}</select></label>}
          {action.kind === 'analysis' && <><CommandPath action={action} handlers={handlers} candidates={commandCandidates} selectToken={selectCommandToken}/><strong>→</strong><label><span>{commandPath(handlers.find(item => item.id === action.handlerId) ?? handlers[0]!).length + 3}</span><select value={action.mode} onChange={event => patchAction(action.id, { mode: event.target.value as ParameterMode })}>{PARAMETER_MODES.map(item => <option key={item.value} value={item.value}>{label(item)}</option>)}</select></label></>}
          {action.kind === 'step' && <label><span>3</span><select value={action.stepType} onChange={event => patchAction(action.id, { stepType: event.target.value })}>{composable.map(item => <option key={`${item.type}@${item.version}`} value={item.type}>{item.title} · {item.source}</option>)}</select></label>}
        </div>}
        {schemaFor(action) && <details className="phrase-parameters"><summary>{t('workflowPhrase.parametersLabel', { defaultValue: '动作参数' })}</summary><SchemaForm schema={schemaFor(action)!} value={action.parameters} onChange={parameters => patchAction(action.id, { parameters })}/></details>}
        {errorsFor(action).length > 0 && <ul className="phrase-validation" aria-live="polite">{errorsFor(action).map(error => <li key={error}>{error}</li>)}</ul>}
      </article>)}
    </div>
    {actions.some(action=>errorsFor(action).length>0)&&<nav className="phrase-error-nav" aria-label="Action validation errors"><button className="phrase-error-jump" onClick={focusFirstError}>{t('workflowPhrase.firstError',{defaultValue:'定位第一个错误'})}</button>{actions.map((action,index)=>errorsFor(action).length?<button key={action.id} onClick={()=>focusAction(action.id)}>{t('workflowPhrase.actionNumber',{defaultValue:`动作 ${index+1}`,index:index+1})} · {errorsFor(action).length}</button>:null)}</nav>}
    {syncConflict&&<section className="phrase-sync-conflict" role="alert"><strong>{t('workflowPhrase.syncConflict',{defaultValue:'动作链和画布都发生了修改'})}</strong><p>{t('workflowPhrase.syncConflictHint',{defaultValue:'可以整体选择，也可以逐个动作合并。'})}</p><ol>{Array.from({length:Math.max(actions.length,conflictCanvasActions.length)},(_,index)=><li key={index}><span>#{index+1}</span><button className={(mergeChoices[index]??'actions')==='actions'?'active':''} disabled={!actions[index]} onClick={()=>setMergeChoices(current=>({...current,[index]:'actions'}))}>{actions[index]?actionSummary(actions[index]):'—'}</button><button className={mergeChoices[index]==='canvas'?'active':''} disabled={!conflictCanvasActions[index]} onClick={()=>setMergeChoices(current=>({...current,[index]:'canvas'}))}>{conflictCanvasActions[index]?actionSummary(conflictCanvasActions[index]):'—'}</button></li>)}</ol><div><button onClick={resolveConflictWithActions}>{t('workflowPhrase.keepActions',{defaultValue:'全部使用动作链'})}</button><button onClick={resolveConflictWithCanvas}>{t('workflowPhrase.keepCanvas',{defaultValue:'全部使用画布'})}</button><button onClick={mergeConflict}>{t('workflowPhrase.merge',{defaultValue:'应用逐项合并'})}</button></div></section>}
    <details className="phrase-snapshots"><summary>{t('workflowPhrase.snapshots',{defaultValue:'动作链快照'})} ({snapshots.length})</summary><button onClick={saveSnapshot}>{t('workflowPhrase.saveSnapshot',{defaultValue:'保存当前快照'})}</button>{snapshots.length===0?<p>{t('workflowPhrase.noSnapshots',{defaultValue:'暂无快照'})}</p>:<ul>{snapshots.map(snapshot=><li key={snapshot.id}><div><input aria-label="Snapshot name" value={snapshot.name} onChange={event=>renameSnapshot(snapshot.id,event.target.value)}/><small>{new Date(snapshot.createdAt).toLocaleString()}</small></div><button onClick={()=>setPreviewSnapshotId(current=>current===snapshot.id?null:snapshot.id)}>{t('workflowPhrase.diff',{defaultValue:'差异'})}</button><button onClick={()=>restoreSnapshot(snapshot)}>{t('workflowPhrase.restore',{defaultValue:'恢复'})}</button><button onClick={()=>deleteSnapshot(snapshot.id)}>{t('workflowPhrase.delete',{defaultValue:'删除'})}</button>{previewSnapshotId===snapshot.id&&<pre>{snapshotDiff(snapshot,actions)}</pre>}</li>)}</ul>}</details>
    <details className="phrase-server-definitions"><summary>{t('workflowPhrase.serverDefinitions',{defaultValue:'项目动作链定义'})} ({serverDefinitions.length})</summary><div><select value={projectId} onChange={event=>setProjectId(event.target.value?Number(event.target.value):'')}><option value="">{t('workflowPhrase.selectProject',{defaultValue:'选择项目'})}</option>{projects.map(project=><option key={project.id} value={project.id}>{project.name}</option>)}</select><button disabled={!projectId||serverBusy} onClick={()=>void saveServerDefinition()}>{serverBusy?t('common.saving',{defaultValue:'保存中…'}):t('workflowPhrase.saveServer',{defaultValue:'保存新版本'})}</button>{submittedTaskId&&<a href="/tasks">Task #{submittedTaskId}</a>}</div><ul>{serverDefinitions.map(definition=><li key={definition.id}><input value={serverNames[definition.id]??definition.definitionId} onChange={event=>setServerNames(current=>({...current,[definition.id]:event.target.value}))}/><span>v{definition.version}</span><code>{definition.sha256.slice(0,10)}</code><small>{new Date(definition.createdAt).toLocaleString()}</small><button disabled={serverBusy} onClick={()=>void renameServerDefinition(definition)}>{t('workflowPhrase.rename',{defaultValue:'重命名'})}</button><button onClick={()=>loadServerDefinition(definition)}>{t('workflowPhrase.load',{defaultValue:'加载'})}</button><button disabled={serverBusy} onClick={()=>void submitServerDefinition(definition)}>{t('workflowPhrase.submit',{defaultValue:'提交任务'})}</button><button disabled={serverBusy} onClick={()=>void deleteServerDefinition(definition)}>{t('workflowPhrase.delete',{defaultValue:'删除版本'})}</button></li>)}</ul></details>
    {draftError && <div className="inline-error" role="alert">{draftError}</div>}
    <footer className="phrase-actions-footer"><input ref={importRef} className="visually-hidden" type="file" accept="application/json,.json" onChange={event => void importDraft(event.target.files?.[0])}/><label className="phrase-live-sync"><input type="checkbox" checked={liveSync} onChange={event => enableLiveSync(event.target.checked)}/><span>{t('workflowPhrase.liveSync',{defaultValue:'与画布实时同步'})}</span></label><button onClick={clearDraft}>{t('workflowPhrase.clearDraft',{defaultValue:'清除草稿'})}</button><button onClick={() => importRef.current?.click()}>{t('workflowPhrase.importDraft',{defaultValue:'导入草稿'})}</button><button onClick={exportDraft}>{t('workflowPhrase.exportDraft',{defaultValue:'导出草稿'})}</button><button disabled={!workflow.steps.length} onClick={importWorkflow}>{t('workflowPhrase.importCurrent', { defaultValue: '从当前 Workflow 导入' })}</button><button onClick={() => setActions(current => [...current, { ...createAction(), handlerId: handlers[0]?.id ?? '', stepType: composable[0]?.type ?? '' }])}>{t('workflowPhrase.addAction', { defaultValue: '添加动作' })}</button><button className="primary-button" disabled={!actions.every(valid)||actions.some(action=>errorsFor(action).length>0)} onClick={compose}>{replaceCurrent?t('workflowPhrase.updateCurrent',{defaultValue:'更新当前 Workflow'}):t('workflowPhrase.generateChain', { defaultValue: '生成动作链' })}</button></footer>
    <p>{t('workflowPhrase.contextHint', { defaultValue: '动作按列表顺序生成依赖；插件词条来自 cli.path，生成后仍由服务端 Schema 校验。' })}</p>
  </section>
}

function PatternPhraseChain({action,item,patterns,pattern,handlers,label,patch}:{action:DraftAction;item:StepCatalogItem;patterns:PhrasePattern[];pattern:PhrasePattern;handlers:AnalysisHandler[];label:(entry:PhraseEntry)=>string;patch:(value:Partial<DraftAction>)=>void}) {
  const {t}=useTranslation()
  const enabled=new Set(action.enabledOptionalTokens??[])
  const selectPattern=(id:string)=>patch({patternId:id,enabledOptionalTokens:[]})
  const tokenKey=(token:PhrasePatternToken,index:number)=>token.name??token.i18nKey??`${token.type}-${index}`
  const toggle=(key:string)=>patch({enabledOptionalTokens:enabled.has(key)?[...enabled].filter(value=>value!==key):[...enabled,key]})
  const valueFor=(token:PhrasePatternToken):string=>{
    if(token.name==='targetFormat')return action.target
    if(token.name==='handlerId')return action.handlerId
    const value=token.name?action.parameters[token.name]:undefined
    return value===undefined||value===null?'':String(value)
  }
  const update=(token:PhrasePatternToken,value:string)=>{
    if(token.name==='targetFormat'){patch({target:value});return}
    if(token.name==='handlerId'){patch({handlerId:value});return}
    if(token.name)patch({parameters:{...action.parameters,[token.name]:value}})
  }
  const options=(token:PhrasePatternToken):Array<{value:string;label:string}>=>{
    if(token.source==='conversion.formats')return CONVERSION_TARGETS.map(entry=>({value:entry.value,label:label(entry)}))
    if(token.source==='analysis.handlers')return handlers.map(handler=>({value:handler.id,label:handler.id}))
    if(token.source==='step.capabilities')return item.capabilities.map(capability=>({value:capability.id,label:capability.titleKey||capability.id}))
    const schema=token.name?undefined:item
    void schema
    return []
  }
  return <>
    {patterns.length>1&&<label className="phrase-pattern-select"><span>{t('workflowPhrase.pattern',{defaultValue:'表达模板'})}</span><select value={pattern.id} onChange={event=>selectPattern(event.target.value)}>{patterns.map(value=><option key={value.id} value={value.id}>{value.id}</option>)}</select></label>}
    <div className="phrase-chain phrase-chain-v2" data-pattern-id={pattern.id}>
      {pattern.tokens.map((token,index)=>{
        const key=tokenKey(token,index)
        if(token.optional&&!enabled.has(key))return <button type="button" className="phrase-token-add" key={key} onClick={()=>toggle(key)}>+ {t(token.i18nKey??'',{defaultValue:token.name??key})}</button>
        const remove=token.optional?<button type="button" aria-label={`Remove ${key}`} onClick={()=>toggle(key)}>×</button>:null
        if(token.type==='LITERAL')return <span className="phrase-token-literal" key={key}>{t(token.i18nKey??'',{defaultValue:(token.i18nKey??key).split('.').at(-1)})}{remove}</span>
        if(token.type==='ENUM')return <label key={key}><span>{token.name}</span><span className="phrase-token-control"><select value={valueFor(token)} onChange={event=>update(token,event.target.value)}>{options(token).map(option=><option key={option.value} value={option.value}>{option.label}</option>)}</select>{remove}</span></label>
        return <span className="phrase-token-parameter" key={key}>{token.name}{remove}</span>
      })}
    </div>
  </>
}

function CommandPath({ action, handlers, candidates, selectToken }: { action: DraftAction; handlers: AnalysisHandler[]; candidates: (action: DraftAction, depth: number) => string[]; selectToken: (action: DraftAction, depth: number, token: string) => void }) {
  const handler = handlers.find(item => item.id === action.handlerId) ?? handlers[0]
  const path = handler?.capability?.cliPath?.length ? handler.capability.cliPath : handler ? ['analysis', handler.id] : []
  return <>{path.map((token, depth) => <span className="phrase-path-level" key={depth}><label><span>{depth + 3}</span><select value={token} onChange={event => selectToken(action, depth, event.target.value)}>{candidates(action, depth).map(option => <option key={option} value={option}>{option}</option>)}</select></label>{depth < path.length - 1 && <strong>→</strong>}</span>)}</>
}

function toComposedAction(action: DraftAction, convert: StepCatalogItem | undefined, analysis: StepCatalogItem | undefined, composable: StepCatalogItem[], handlers: AnalysisHandler[]): ComposedWorkflowAction | null {
  if (action.kind === 'convert' && convert) return { item: convert, parameters: { ...action.parameters, targetFormat: action.target, sourceDataFileIdContext: 'sourceDataFileId' } }
  if (action.kind === 'analysis' && analysis && action.handlerId) {
    const parameters: Record<string, JsonValue> = { handlerId: action.handlerId, sourceDataFileIdsContext: 'sourceDataFileIds' }
    Object.entries(action.parameters).forEach(([key, value]) => { parameters[`parameter.${key}`] = typeof value === 'string' ? value : JSON.stringify(value) })
    if (action.mode === 'template' && handlers.find(item => item.id === action.handlerId)?.parameterContract?.id === 'tabular-clean') {
      parameters['parameter.operation'] = 'tabular-clean'
      parameters['parameter.spec'] = JSON.stringify({ schemaVersion: 1, output: { format: 'XLSX' }, rules: [{ type: 'trimStrings' }] })
    }
    return { item: analysis, parameters }
  }
  const selected = composable.find(item => item.type === action.stepType)
  return selected ? { item: selected, parameters: action.parameters } : null
}

function workflowToDraftActions(workflow: WorkflowIr, handlers: AnalysisHandler[], composable: StepCatalogItem[]): DraftAction[] {
  const result = workflow.steps.map(step => {
    if (step.type === 'datafile.convert') return { ...createAction(), kind: 'convert' as const, target: String(step.parameters.targetFormat ?? 'XLSX'), parameters: omitKeys(step.parameters, ['targetFormat', 'sourceDataFileIdContext']) }
    if (step.type === 'datafile.analysis.run') {
      const handlerId = String(step.parameters.handlerId ?? handlers[0]?.id ?? '')
      const parameters = Object.fromEntries(Object.entries(step.parameters).filter(([key]) => key.startsWith('parameter.')).map(([key, value]) => [key.slice(10), parseEncodedValue(value)]))
      return { ...createAction(), kind: 'analysis' as const, handlerId, mode: 'custom' as const, parameters }
    }
    return { ...createAction(), kind: 'step' as const, stepType: step.type || composable[0]?.type || '', parameters: { ...step.parameters } }
  })
  return result.length ? result : [createAction()]
}

function omitKeys(value: Record<string, JsonValue>, keys: string[]): Record<string, JsonValue> {
  return Object.fromEntries(Object.entries(value).filter(([key]) => !keys.includes(key)))
}

function parseEncodedValue(value: JsonValue): JsonValue {
  if (typeof value !== 'string') return value
  try { return JSON.parse(value) as JsonValue } catch { return value }
}

/** Treats a missing pattern id as a legacy v1 action and selects the first registered template. */
export function resolvePhrasePattern(item: StepCatalogItem, patternId?: string): PhrasePattern | undefined {
  return item.phrasePatterns.find(pattern => pattern.id === patternId) ?? item.phrasePatterns[0]
}

function loadDraftActions(): DraftAction[] {
  try {
    const value = JSON.parse(localStorage.getItem(DRAFT_KEY) ?? 'null') as { schemaVersion?: number; actions?: DraftAction[] } | null
    if (value?.schemaVersion === 1 && Array.isArray(value.actions) && value.actions.length) {
      const actions = value.actions.map(action => ({ ...action, id: nextActionId++, parameters: action.parameters ?? {} }))
      return actions
    }
  } catch { /* start with a clean phrase */ }
  return [createAction()]
}

export function validatePhraseParameters(schema: JsonSchema, value: Record<string, JsonValue>): string[] {
  return validateValue(schema, value, 'parameters')
}

function validateValue(schema: JsonSchema, value: JsonValue, path: string): string[] {
  const errors: string[] = []
  for (const part of schema.allOf ?? []) errors.push(...validateValue(part, value, path))
  if (schema.anyOf?.length && !schema.anyOf.some(part => validateValue(part, value, path).length === 0)) errors.push(`${path}: must match at least one allowed schema`)
  if (schema.oneOf?.length) { const matches = schema.oneOf.filter(part => validateValue(part, value, path).length === 0).length; if (matches !== 1) errors.push(`${path}: must match exactly one allowed schema`) }
  const type = Array.isArray(schema.type) ? schema.type.find(item => item !== 'null') : schema.type
  if (type === 'object' || schema.properties || schema.required) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return [...errors, `${path}: must be an object`]
    const object = value as Record<string, JsonValue>
    for (const name of schema.required ?? []) if (object[name] === undefined || object[name] === null || object[name] === '') errors.push(`${schema.properties?.[name]?.title ?? `${path}.${name}`}: required`)
    for (const [name, field] of Object.entries(schema.properties ?? {})) if (object[name] !== undefined) errors.push(...validateValue(field, object[name]!, field.title ?? `${path}.${name}`))
  } else if (type === 'array' || schema.items) {
    if (!Array.isArray(value)) errors.push(`${path}: must be an array`)
    else if (schema.items) value.forEach((item, index) => errors.push(...validateValue(schema.items!, item, `${path}[${index}]`)))
  } else {
    if (type === 'string' && typeof value !== 'string') errors.push(`${path}: must be text`)
    if ((type === 'number' || type === 'integer') && (typeof value !== 'number' || (type === 'integer' && !Number.isInteger(value)))) errors.push(`${path}: must be ${type}`)
    if (type === 'boolean' && typeof value !== 'boolean') errors.push(`${path}: must be true or false`)
  }
  if (schema.enum && !schema.enum.some(option => JSON.stringify(option) === JSON.stringify(value))) errors.push(`${path}: unsupported value`)
  if (typeof value === 'number' && schema.minimum !== undefined && value < schema.minimum) errors.push(`${path}: minimum ${schema.minimum}`)
  if (typeof value === 'number' && schema.maximum !== undefined && value > schema.maximum) errors.push(`${path}: maximum ${schema.maximum}`)
  if (typeof value === 'string' && schema.minLength !== undefined && value.length < schema.minLength) errors.push(`${path}: minimum length ${schema.minLength}`)
  return [...new Set(errors)]
}

function composedSignature(actions: ComposedWorkflowAction[]) { return JSON.stringify(actions.map(action => [action.item.type, action.item.version, action.parameters])) }
function workflowSignature(workflow: WorkflowIr) { return JSON.stringify(workflow.steps.map(step => [step.type, step.version, step.parameters])) }
function downloadJson(name: string, value: unknown) { const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' })); const anchor = document.createElement('a'); anchor.href = url; anchor.download = name; anchor.click(); window.setTimeout(() => URL.revokeObjectURL(url), 500) }
function isImportedDraftAction(value: unknown): value is Omit<DraftAction, 'id'> { if (!value || typeof value !== 'object') return false; const action = value as Partial<DraftAction>; return ['convert','analysis','step'].includes(action.kind ?? '') && ['template','custom'].includes(action.mode ?? '') && typeof action.target === 'string' && typeof action.handlerId === 'string' && typeof action.stepType === 'string' && Boolean(action.parameters) && typeof action.parameters === 'object' && !Array.isArray(action.parameters) }
function loadSnapshots(): PhraseSnapshot[] { try { const value = JSON.parse(localStorage.getItem(SNAPSHOT_KEY) ?? '[]') as PhraseSnapshot[]; return Array.isArray(value) ? value.filter(snapshot => snapshot && typeof snapshot.id === 'string' && Array.isArray(snapshot.actions)).slice(0, 20) : [] } catch { return [] } }
function persistSnapshots(snapshots: PhraseSnapshot[]) { localStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshots)); return snapshots }
function actionSummary(action: Pick<DraftAction,'kind'|'target'|'handlerId'|'stepType'>) { return action.kind==='convert'?`convert → ${action.target}`:action.kind==='analysis'?`analysis → ${action.handlerId}`:`step → ${action.stepType}` }
function snapshotDiff(snapshot: PhraseSnapshot, actions: DraftAction[]) { const current=actions.map(actionSummary),stored=snapshot.actions.map(actionSummary),length=Math.max(current.length,stored.length);return Array.from({length},(_,index)=>current[index]===stored[index]?`  ${current[index]??''}`:`- ${stored[index]??'∅'}\n+ ${current[index]??'∅'}`).join('\n') }
