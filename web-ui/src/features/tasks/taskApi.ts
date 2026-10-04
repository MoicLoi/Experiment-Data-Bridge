import type { ApiClient } from '../../lib/api'
import type { Project, Task, TaskPage, TaskStatus, TaskTimeline } from './types'

export const listProjects = (api: ApiClient) => api.get<Project[]>('/api/v1/projects')
export const createDemoProject = (api: ApiClient, name = 'frontend-smoke') => api.post<Project>('/api/v1/projects', {
  name, description: 'Created from the Demo v1 frontend quick start.', mode: 'PERSONAL', configYaml: 'schemaVersion: 1\n',
})
export const listTasks = (api: ApiClient, projectId: number, status: TaskStatus | '', page: number, size = 20) => {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) query.set('status', status)
  return api.get<TaskPage>(`/api/v1/projects/${projectId}/tasks?${query}`)
}
export const getTask = (api: ApiClient, taskId: number) => api.get<Task>(`/api/v1/tasks/${taskId}`)
export const getTimeline = (api: ApiClient, taskId: number) => api.get<TaskTimeline>(`/api/v1/tasks/${taskId}/timeline`)
export const cancelTask = (api: ApiClient, taskId: number) => api.post<Task>(`/api/v1/tasks/${taskId}/cancel`)
export const requeueTask = (api: ApiClient, taskId: number) => api.post<Task>(`/api/v1/admin/tasks/${taskId}/requeue`)
