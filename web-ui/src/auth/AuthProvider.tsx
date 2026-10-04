import { createContext, useCallback, useContext, useEffect, useMemo, useState, type PropsWithChildren } from 'react'
import { ApiClient, API_BASE_URL } from '../lib/api'

interface User { username: string; role: string }
interface AuthContextValue {
  token: string | null; user: User | null; ready: boolean; api: ApiClient
  login: (username: string, password: string, remember: boolean) => Promise<void>; logout: () => void
}

const TOKEN_KEY = 'edb.auth.token'
const AuthContext = createContext<AuthContextValue | null>(null)
const storedToken = () => sessionStorage.getItem(TOKEN_KEY) ?? localStorage.getItem(TOKEN_KEY)

export function AuthProvider({ children }: PropsWithChildren) {
  const [token, setToken] = useState<string | null>(storedToken)
  const [user, setUser] = useState<User | null>(null)
  const [ready, setReady] = useState(false)
  const logout = useCallback(() => {
    sessionStorage.removeItem(TOKEN_KEY); localStorage.removeItem(TOKEN_KEY); setToken(null); setUser(null)
  }, [])
  const api = useMemo(() => new ApiClient({ baseUrl: API_BASE_URL, getToken: () => token, onUnauthorized: logout }), [token, logout])

  useEffect(() => {
    if (!token) { setReady(true); return }
    setReady(false)
    api.get<User>('/api/v1/user/me').then(setUser).catch(logout).finally(() => setReady(true))
  }, [api, token, logout])

  const login = useCallback(async (username: string, password: string, remember: boolean) => {
    const publicApi = new ApiClient({ baseUrl: API_BASE_URL, getToken: () => null })
    const response = await publicApi.post<{ token: string }>('/api/v1/auth/login', { username, password })
    const storage = remember ? localStorage : sessionStorage
    localStorage.removeItem(TOKEN_KEY); sessionStorage.removeItem(TOKEN_KEY); storage.setItem(TOKEN_KEY, response.token)
    setReady(false); setToken(response.token)
  }, [])

  return <AuthContext.Provider value={{ token, user, ready, api, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
