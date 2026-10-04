import type { ApiClient } from '../../lib/api'
import type { JsonSchema,JsonValue,PhraseDefinition,StepCatalogItem,WorkflowCompilation,WorkflowIr,WorkflowTemplate } from './types'
import type { Task } from '../tasks/types'
export const getStepCatalog=async(api:ApiClient)=>(await api.get<{items:StepCatalogItem[]}>('/api/v1/workflow-steps')).items
export const getStepSchema=(api:ApiClient,item:StepCatalogItem)=>item.schemaUrl?api.get<JsonSchema>(item.schemaUrl):Promise.resolve({type:'object',properties:{}} as JsonSchema)
export const listPhraseDefinitions=async(api:ApiClient,projectId:number,page=0,size=50)=>(await api.get<{items:PhraseDefinition[];page:number;size:number;total:number}>(`/api/v1/projects/${projectId}/phrase-definitions?page=${page}&size=${size}`)).items
export const savePhraseDefinition=(api:ApiClient,projectId:number,definitionId:string,document:{schemaVersion:1;actions:Array<Record<string,JsonValue>>})=>api.post<PhraseDefinition>(`/api/v1/projects/${projectId}/phrase-definitions`,{definitionId,document})
export const renamePhraseDefinition=(api:ApiClient,projectId:number,definitionId:string,newDefinitionId:string)=>api.request<{renamedVersions:number}>(`/api/v1/projects/${projectId}/phrase-definitions/${encodeURIComponent(definitionId)}`,{method:'PATCH',body:JSON.stringify({newDefinitionId})})
export const deletePhraseDefinitionVersion=(api:ApiClient,projectId:number,definitionId:string,version:number)=>api.request<void>(`/api/v1/projects/${projectId}/phrase-definitions/${encodeURIComponent(definitionId)}/versions/${version}`,{method:'DELETE'})
export const submitPhraseDefinition=(api:ApiClient,definition:PhraseDefinition)=>api.post<Task>(`/api/v1/projects/${definition.projectId}/phrase-definitions/${encodeURIComponent(definition.definitionId)}/submit`,{version:definition.version,expectedSha256:definition.sha256,context:{}})
const send=(api:ApiClient,path:string,ir:WorkflowIr)=>api.request<WorkflowCompilation>(path,{method:'POST',body:JSON.stringify(ir)})
export const validateWorkflow=(api:ApiClient,ir:WorkflowIr)=>send(api,'/api/v1/workflows/validate',ir)
export const compileWorkflow=(api:ApiClient,ir:WorkflowIr)=>send(api,'/api/v1/workflows/compile',ir)
export const getWorkflowTemplates=async(api:ApiClient)=>(await api.get<{items:WorkflowTemplate[]}>('/api/v1/workflow-templates')).items
