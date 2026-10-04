import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../auth/AuthProvider'
import { analysisHandlers } from '../datafiles/dataFileApi'
import type { AnalysisHandler } from '../datafiles/types'
import { listProjects } from '../tasks/taskApi'
import { getStepCatalog } from '../workflows/workflowApi'
import type { StepCatalogItem } from '../workflows/types'

type Capability={id:string;version:string;source:string;title:string;type:string;description:string;schemaAvailable:boolean;kind:'STEP'|'ANALYSIS'}

export function AlgorithmLibraryPage(){
 const{t}=useTranslation();const{api}=useAuth();const navigate=useNavigate();const[steps,setSteps]=useState<StepCatalogItem[]>([]);const[handlers,setHandlers]=useState<AnalysisHandler[]>([]);const[query,setQuery]=useState('');const[source,setSource]=useState('ALL');const[error,setError]=useState('')
 useEffect(()=>{Promise.all([getStepCatalog(api),listProjects(api)]).then(async([catalog,projects])=>{setSteps(catalog);setHandlers(projects[0]?await analysisHandlers(api,projects[0].id):[])}).catch(failure=>setError(failure instanceof Error?failure.message:String(failure)))},[api])
 const items=useMemo<Capability[]>(()=>[...steps.map(item=>({id:`step:${item.type}@${item.version}`,version:item.version,source:item.source,title:item.title,type:item.type,description:item.description??'',schemaAvailable:item.schemaAvailable,kind:'STEP' as const})),...handlers.map(item=>({id:`handler:${item.id}`,version:String(item.parameterContract?.version??1),source:`HANDLER · ${item.tool}`,title:item.analysisType,type:item.id,description:item.parameterContract?.description??`Analysis Handler exposed by ${item.tool}.`,schemaAvailable:Boolean(item.parameterContract),kind:'ANALYSIS' as const}))],[steps,handlers])
 const sources=useMemo(()=>['ALL',...new Set(items.map(item=>item.source))],[items]);const visible=useMemo(()=>items.filter(item=>(source==='ALL'||item.source===source)&&`${item.title} ${item.type} ${item.description??''}`.toLowerCase().includes(query.toLowerCase())),[items,query,source])
 return <div className="catalog-page"><header className="page-heading"><div><span>CAPABILITY / CATALOG</span><h1>{t('operations.algorithms')}</h1><p>{t('operations.algorithmSubtitle')}</p></div><button onClick={()=>navigate('/workflows')}>{t('operations.openEditor')}</button></header><section className="catalog-toolbar"><input value={query} onChange={event=>setQuery(event.target.value)} placeholder={t('operations.searchAlgorithms')}/><select value={source} onChange={event=>setSource(event.target.value)}>{sources.map(value=><option key={value}>{value}</option>)}</select><span>{visible.length} / {items.length}</span></section>{error&&<div className="section-error"><strong>{t('operations.unavailable')}</strong><p>{error}</p></div>}<section className="algorithm-grid">{visible.map(item=><article key={item.id}><header><span>{item.source}</span><code>v{item.version}</code></header><h2>{item.title}</h2><code>{item.type}</code><p>{item.description||t('operations.noDescription')}</p><footer><span>{item.schemaAvailable?t('operations.schemaReady'):t('operations.noSchema')}</span><button onClick={()=>navigate(item.kind==='ANALYSIS'?'/datafiles':'/workflows')}>{item.kind==='ANALYSIS'?'Use on data':t('operations.useInWorkflow')}</button></footer></article>)}</section></div>
}
