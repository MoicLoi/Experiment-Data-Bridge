import type { Task } from '../tasks/types'

export interface AgentSession { id:string; userId:number; projectId:number; agentId:string; agentVersion:string; status:string; createdAt:string; updatedAt:string }
export interface AgentRun { id:string; sessionId:string; correlationId:string; status:string; modelProfileId:string; inputTokens?:number|null; outputTokens?:number|null; failureCode?:string|null; failureMessage?:string|null; failureRetryable?:boolean|null; startedAt:string; updatedAt:string; completedAt?:string|null }
export interface AgentMessage { id:number; runId:string; role:string; content:string; createdAt:string }
export interface AgentValidationError { path:string; message:string }
export interface AgentProposal { id:string; runId:string; workflowIrJson:string; explanation:string; assumptions:string[]; warnings:string[]; valid:boolean; executionOrder:string[]; validationErrors:AgentValidationError[]; approvalRequired:boolean; createdAt:string }
export interface AgentAuditEvent { id:number; sessionId:string; runId?:string|null; actorUserId:number; projectId:number; type:string; runStatus?:string|null; detailsJson:string; createdAt:string }
export interface AgentLifecycleEvent { sessionId:string; runId?:string|null; projectId:number; type:string; runStatus?:string|null; detailsJson:string; occurredAt:string }
export interface ProposalDecision { proposalId:string; status:string; decidedBy:number; workflowSha256:string; reason?:string|null; decidedAt:string }
export interface ProposalSubmission { proposalId:string; definitionId:number; workflowId:string; definitionVersion:number; workflowSha256:string; task:Task }
export interface AgentModelProfile { id:string;providerId:string;modelName:string;toolCallingEnabled:boolean;structuredOutputEnabled:boolean;builtIn:boolean;default:boolean }
export interface AgentModelProfileCatalog { defaultProfileId:string;items:AgentModelProfile[] }
