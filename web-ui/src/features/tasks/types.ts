export type TaskStatus = 'PENDING' | 'QUEUED' | 'RUNNING' | 'RETRYING' | 'COMPLETED' | 'FAILED' | 'TIMED_OUT' | 'CANCELLED'
export interface Project { id: number; name: string; description?: string | null }
export interface TaskProgress { percent: number; message?: string | null; updatedAt: string }
export interface TaskResult { outputPath?: string | null; contentType?: string | null; metadata: Record<string, string> }
export interface TaskError { code: string; category: string; message: string; retryable: boolean; details: Record<string, string>; occurredAt: string }
export interface TaskFailureDiagnostic { code: string; title: string; message: string; action?: string | null; actionLabel?: string | null; automaticActionSafe: boolean }
export interface Task { id: number; projectId: number; taskType: string; status: TaskStatus; priority: number; attemptCount: number; maxAttempts: number; nextAttemptAt?: string | null; timeoutMs?: number | null; cancellationRequestedAt?: string | null; progress?: TaskProgress | null; result?: TaskResult | null; error?: TaskError | null; diagnostic?: TaskFailureDiagnostic | null; durationMs?: number | null; createdAt: string; updatedAt: string; startedAt?: string | null; completedAt?: string | null }
export interface TaskPage { items: Task[]; page: number; size: number; totalElements: number; totalPages: number }
export interface TaskAuditEvent { id: number; type: string; fromStatus?: TaskStatus | null; toStatus?: TaskStatus | null; actorUserId?: number | null; details: Record<string, string>; createdAt: string }
export interface TaskTimeline { taskId: number; events: TaskAuditEvent[] }
export interface TaskLifecycleEvent { taskId: number; projectId: number; type: string; previousStatus?: TaskStatus | null; status: TaskStatus; attemptCount: number; details: Record<string, string>; occurredAt: string }
