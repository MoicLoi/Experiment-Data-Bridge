import { useTranslation } from 'react-i18next'
import { useTheme, type ThemePreference } from '../theme/ThemeProvider'

export function ThemeSelect() {
  const { t } = useTranslation(); const { theme, setTheme } = useTheme()
  return <select aria-label={t('shell.theme')} value={theme} onChange={(event) => setTheme(event.target.value as ThemePreference)}>
    <option value="system">{t('shell.system')}</option><option value="light">{t('shell.light')}</option><option value="dark">{t('shell.dark')}</option>
  </select>
}
