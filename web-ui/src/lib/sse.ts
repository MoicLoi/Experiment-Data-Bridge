import { ApiError } from './api'

export interface SseMessage { id?: string; event?: string; data: string }

export async function streamSse(
  path: string,
  options: { baseUrl?: string; token: string; signal?: AbortSignal; onMessage: (message: SseMessage) => void },
): Promise<void> {
  let response: Response
  try {
    response = await fetch(`${(options.baseUrl ?? '').replace(/\/$/, '')}${path}`, {
      headers: { Accept: 'text/event-stream', Authorization: `Bearer ${options.token}` }, signal: options.signal,
    })
  } catch (cause) {
    if (options.signal?.aborted) return
    throw new ApiError('NETWORK_ERROR', undefined, cause instanceof Error ? cause.message : undefined)
  }
  if (!response.ok || !response.body) throw new ApiError(response.status === 401 ? 'UNAUTHORIZED' : 'UNKNOWN', response.status)
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += value
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const frame = buffer.slice(0, boundary).replace(/\r/g, '')
      buffer = buffer.slice(boundary + 2)
      const message = parseFrame(frame)
      if (message) options.onMessage(message)
      boundary = buffer.indexOf('\n\n')
    }
  }
}

function parseFrame(frame: string): SseMessage | null {
  if (!frame || frame.startsWith(':')) return null
  const message: SseMessage = { data: '' }
  const data: string[] = []
  for (const line of frame.split('\n')) {
    const separator = line.indexOf(':')
    const field = separator < 0 ? line : line.slice(0, separator)
    const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '')
    if (field === 'id') message.id = value
    else if (field === 'event') message.event = value
    else if (field === 'data') data.push(value)
  }
  if (!data.length) return null
  message.data = data.join('\n')
  return message
}
