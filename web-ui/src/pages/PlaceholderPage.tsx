import { useTranslation } from 'react-i18next'
export function PlaceholderPage({ titleKey }: { titleKey: string }) { const { t } = useTranslation(); return <section className="placeholder"><span>EDB / 0.25</span><h1>{t(titleKey)}</h1><p>{t('common.unavailable')}</p></section> }
