import { useTranslation } from 'react-i18next'
import { supportedLocales, type SupportedLocale } from '../i18n/resources'

export function LanguageSelect() {
  const { i18n, t } = useTranslation()
  return <select aria-label={t('shell.language')} value={i18n.language} onChange={(event) => void i18n.changeLanguage(event.target.value as SupportedLocale)}>
    {supportedLocales.map((locale) => <option key={locale} value={locale}>{locale === 'zh-CN' ? t('locale.zhCN') : t('locale.enUS')}</option>)}
  </select>
}
