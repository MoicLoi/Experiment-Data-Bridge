import { ApiError, type ApiClient } from '../../lib/api'
import type { AgentAuditEvent, AgentMessage, AgentModelProfileCatalog, AgentProposal, AgentRun, AgentSession, ProposalDecision, ProposalSubmission } from './types'

export const createSession=(api:ApiClient,projectId:number)=>api.post<AgentSession>(`/api/v1/projects/${projectId}/agent-sessions`,{agentId:'workflow-planner',agentVersion:'1'})
export const getSession=(api:ApiClient,id:string)=>api.get<AgentSession>(`/api/v1/agent-sessions/${id}`)
export const closeSession=(api:ApiClient,id:string)=>api.post<AgentSession>(`/api/v1/agent-sessions/${id}/close`)
export const getMessages=async(api:ApiClient,id:string)=>(await api.get<{items:AgentMessage[]}>(`/api/v1/agent-sessions/${id}/messages`)).items
export const modelProfiles=(api:ApiClient)=>api.get<AgentModelProfileCatalog>('/api/v1/agent-model-profiles')
export const plan=async(api:ApiClient,id:string,message:string,modelProfileId:string)=>(await api.post<{run:AgentRun}>(`/api/v1/agent-sessions/${id}/messages`,{message,maxRepairAttempts:2,modelProfileId})).run
export const getRun=(api:ApiClient,id:string)=>api.get<AgentRun>(`/api/v1/agent-runs/${id}`)
export const cancelRun=(api:ApiClient,id:string)=>api.post<AgentRun>(`/api/v1/agent-runs/${id}/cancel`)
export const getProposals=async(api:ApiClient,id:string)=>(await api.get<{items:AgentProposal[]}>(`/api/v1/agent-runs/${id}/proposals`)).items
export const getAudit=async(api:ApiClient,id:string)=>(await api.get<{runId:string;events:AgentAuditEvent[]}>(`/api/v1/agent-runs/${id}/audit`)).events
export const approveProposal=(api:ApiClient,id:string)=>api.post<ProposalDecision>(`/api/v1/agent-proposals/${id}/approve`)
export const rejectProposal=(api:ApiClient,id:string,reason:string)=>api.post<ProposalDecision>(`/api/v1/agent-proposals/${id}/reject`,{reason})
export const submitProposal=(api:ApiClient,id:string,context:Record<string,string>={})=>api.post<ProposalSubmission>(`/api/v1/agent-proposals/${id}/submit`,{context})
export const getProposalDecision=(api:ApiClient,id:string)=>optional(()=>api.get<ProposalDecision>(`/api/v1/agent-proposals/${id}/decision`))
export const getProposalSubmission=(api:ApiClient,id:string)=>optional(()=>api.get<ProposalSubmission>(`/api/v1/agent-proposals/${id}/submission`))
async function optional<T>(load:()=>Promise<T>):Promise<T|null>{try{return await load()}catch(failure){if(failure instanceof ApiError&&failure.code==='NOT_FOUND')return null;throw failure}}
