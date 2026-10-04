export type ApiErrorCode = 'UNAUTHORIZED' | 'FORBIDDEN' | 'NOT_FOUND' | 'SERVER_ERROR' | 'NETWORK_ERROR' | 'UNKNOWN'

export class ApiError extends Error {
  constructor(public readonly code: ApiErrorCode, public readonly status?: number, message?: string) { super(message ?? code) }
}

export interface ApiClientOptions {
  baseUrl?: string
  getToken: () => string | null
  onUnauthorized?: () => void
}

export class ApiClient {
  private readonly baseUrl: string
  constructor(private readonly options: ApiClientOptions) { this.baseUrl = (options.baseUrl ?? '').replace(/\/$/, '') }

  async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await this.raw(path, init)
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  }

  async raw(path: string, init: RequestInit = {}): Promise<Response> {
    const startedAt = performance.now()
    const method = init.method ?? 'GET'
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    const token = this.options.getToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
    let response: Response
    try { response = await fetch(`${this.baseUrl}${path}`, { ...init, headers }) }
    catch (cause) {
      debugEvent('api', 'request failed', { method, path, durationMs: Math.round(performance.now() - startedAt), error: cause instanceof Error ? cause.message : cause }, 'ERROR')
      throw new ApiError('NETWORK_ERROR', undefined, cause instanceof Error ? cause.message : undefined)
    }
    debugEvent('api', 'request completed', { method, path, status: response.status, durationMs: Math.round(performance.now() - startedAt) }, response.ok ? 'INFO' : 'WARN')
    if (response.status === 401) { this.options.onUnauthorized?.(); throw new ApiError('UNAUTHORIZED', 401) }
    if (!response.ok) throw await this.error(response)
    return response
  }

  get<T>(path: string) { return this.request<T>(path) }
  post<T>(path: string, body?: unknown) { return this.request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }) }

  private async error(response: Response): Promise<ApiError> {
    const message = await response.text().catch(() => '')
    const code = response.status === 403 ? 'FORBIDDEN' : response.status === 404 ? 'NOT_FOUND' : response.status >= 500 ? 'SERVER_ERROR' : 'UNKNOWN'
    return new ApiError(code, response.status, message.slice(0, 500))
  }
}

export const API_BASE_URL = import.meta.env.VITE_EDB_API_BASE_URL ?? ''
import { debugEvent } from './debug'
