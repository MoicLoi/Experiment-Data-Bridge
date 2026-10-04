import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react'

export type ThemePreference = 'light' | 'dark' | 'system'
const KEY = 'edb.theme'
const ThemeContext = createContext<{ theme: ThemePreference; setTheme: (theme: ThemePreference) => void } | null>(null)

export function ThemeProvider({ children }: PropsWithChildren) {
  const [theme, setTheme] = useState<ThemePreference>(() => (localStorage.getItem(KEY) as ThemePreference | null) ?? 'system')
  useEffect(() => {
    localStorage.setItem(KEY, theme)
    const media = matchMedia('(prefers-color-scheme: dark)')
    const apply = () => document.documentElement.dataset.theme = theme === 'system' ? (media.matches ? 'dark' : 'light') : theme
    apply(); media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [theme])
  return <ThemeContext.Provider value={{ theme, setTheme }}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const value = useContext(ThemeContext)
  if (!value) throw new Error('useTheme must be used inside ThemeProvider')
  return value
}
