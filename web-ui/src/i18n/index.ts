import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { resources, supportedLocales, type SupportedLocale } from './resources'

const STORAGE_KEY = 'edb.locale'

function initialLocale(): SupportedLocale {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (supportedLocales.includes(stored as SupportedLocale)) return stored as SupportedLocale
  return navigator.language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
}

void i18n.use(initReactI18next).init({
  resources,
  lng: initialLocale(),
  fallbackLng: 'en-US',
  interpolation: { escapeValue: false },
  returnNull: false,
})

i18n.on('languageChanged', (locale) => {
  localStorage.setItem(STORAGE_KEY, locale)
  document.documentElement.lang = locale
})

document.documentElement.lang = i18n.language
export default i18n
