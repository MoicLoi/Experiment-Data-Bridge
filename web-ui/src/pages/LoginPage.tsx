import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { ApiError } from '../lib/api'
import { LanguageSelect } from '../components/LanguageSelect'
import { ThemeSelect } from '../components/ThemeSelect'

export function LoginPage() {
  const { t } = useTranslation(); const auth = useAuth(); const navigate = useNavigate(); const location = useLocation()
  const [username, setUsername] = useState(''); const [password, setPassword] = useState(''); const [remember, setRemember] = useState(false)
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  if (auth.token) return <Navigate to="/" replace />
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(null)
    try { await auth.login(username, password, remember); navigate((location.state as { from?: string } | null)?.from ?? '/', { replace: true }) }
    catch (failure) { setError(failure instanceof ApiError && failure.status === 401 ? t('auth.invalid') : t('errors.network')) }
    finally { setBusy(false) }
  }
  return <main className="login-page">
    <div className="login-tools"><LanguageSelect /><ThemeSelect /></div>
    <section className="login-brand"><div className="brand-mark">EDB</div><p>EXPERIMENT DATA BRIDGE</p><h1>{t('auth.title')}</h1><span>{t('auth.subtitle')}</span></section>
    <form className="login-card" onSubmit={submit}>
      <label>{t('auth.username')}<input autoComplete="username" required value={username} onChange={(event) => setUsername(event.target.value)} /></label>
      <label>{t('auth.password')}<input type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} /></label>
      <label className="check"><input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} />{t('auth.remember')}</label>
      {error && <p className="form-error" role="alert">{error}</p>}
      <button type="submit" disabled={busy}>{busy ? t('auth.submitting') : t('auth.submit')}</button>
    </form>
  </main>
}
