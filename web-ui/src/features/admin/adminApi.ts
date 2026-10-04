import type{ApiClient}from'../../lib/api'
import type{AgentMetrics,DockerImageCatalogItem,DockerRebuildPreview,DockerRebuildProgress,DockerRebuildResult,DockerRegistryProfile,ModelMetrics,ProviderStatus,RetentionEstimate,RetentionOverview,SchedulerState,TaskMetrics,WorkerFleet,WorkerLogs}from'./types'
export const scheduler=(api:ApiClient)=>api.get<SchedulerState>('/api/v1/admin/task-scheduler')
export const schedulerAction=(api:ApiClient,action:'start'|'stop')=>api.post<SchedulerState>(`/api/v1/admin/task-scheduler/${action}`)
export const setWorkers=(api:ApiClient,workerCount:number)=>api.request<SchedulerState>('/api/v1/admin/task-scheduler/workers',{method:'PUT',body:JSON.stringify({workerCount})})
export const fleet=(api:ApiClient)=>api.get<WorkerFleet>('/api/v1/admin/docker-workers')
export const fleetAction=(api:ApiClient,action:'start'|'stop')=>api.post<WorkerFleet>(`/api/v1/admin/docker-workers/${action}`)
export const restartWorker=(api:ApiClient,id:string)=>api.post(`/api/v1/admin/docker-workers/${encodeURIComponent(id)}/restart`)
export const workerLogs=(api:ApiClient,id:string)=>api.get<WorkerLogs>(`/api/v1/admin/docker-workers/${encodeURIComponent(id)}/logs?tail=200`)
export const dockerImages=async(api:ApiClient)=>(await api.get<{items:DockerImageCatalogItem[]}>('/api/v1/admin/docker-workers/images')).items
export const dockerRegistries=async(api:ApiClient)=>(await api.get<{items:DockerRegistryProfile[]}>('/api/v1/admin/docker-registries')).items
export const saveDockerRegistry=(api:ApiClient,value:{id:string;displayName:string;registryUrl:string;namespace?:string;credentialRef?:string;allowInsecure:boolean;isDefault:boolean})=>api.post<DockerRegistryProfile>('/api/v1/admin/docker-registries',value)
export const defaultDockerRegistry=(api:ApiClient,id:string)=>api.post<DockerRegistryProfile>(`/api/v1/admin/docker-registries/${encodeURIComponent(id)}/default`)
export const deleteDockerRegistry=(api:ApiClient,id:string)=>api.request<void>(`/api/v1/admin/docker-registries/${encodeURIComponent(id)}?confirm=true`,{method:'DELETE'})
export const previewDockerRebuild=(api:ApiClient,workerId:string,value:{registryProfileId:string;repository:string;tag:string;useLocalImage?:boolean})=>api.post<DockerRebuildPreview>(`/api/v1/admin/docker-workers/${encodeURIComponent(workerId)}/rebuild/preview`,value)
export const executeDockerRebuild=(api:ApiClient,workerId:string,value:{registryProfileId:string;repository:string;tag:string;useLocalImage?:boolean;confirmationToken:string})=>api.post<DockerRebuildResult>(`/api/v1/admin/docker-workers/${encodeURIComponent(workerId)}/rebuild`,value)
export const dockerRebuildProgress=(api:ApiClient,workerId:string)=>api.get<DockerRebuildProgress>(`/api/v1/admin/docker-workers/${encodeURIComponent(workerId)}/rebuild/progress`)
export const taskMetrics=(api:ApiClient)=>api.get<TaskMetrics>('/api/v1/admin/task-metrics')
export const agentMetrics=(api:ApiClient)=>api.get<AgentMetrics>('/api/v1/admin/agent-metrics')
export const providers=async(api:ApiClient)=>(await api.get<{items:ProviderStatus[]}>('/api/v1/admin/agent-providers')).items
export const modelMetrics=async(api:ApiClient)=>(await api.get<{items:ModelMetrics[]}>('/api/v1/admin/agent-model-metrics')).items
export type RetentionKind='task-retention'|'data-file-retention'|'data-file-orphan-cleanup'|'agent-retention'
export const retention=(api:ApiClient,kind:RetentionKind)=>api.get<RetentionOverview>(`/api/v1/admin/${kind}`)
export const retentionEstimate=(api:ApiClient,kind:RetentionKind)=>api.get<RetentionEstimate>(`/api/v1/admin/${kind}/estimate`)
export const retentionCleanup=(api:ApiClient,kind:RetentionKind)=>api.post<Record<string,unknown>>(`/api/v1/admin/${kind}/cleanup`)
