import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

export function OverviewPage() {
  const { t } = useTranslation()
  return <div className="overview">
    <section className="hero"><span>{t('overview.eyebrow')}</span><h1>{t('overview.title')}</h1><p>{t('overview.description')}</p></section>
    <section className="status-grid">
      <article><i className="status-dot" /><h2>{t('overview.statusTitle')}</h2><p>{t('overview.statusBody')}</p></article>
      <article className="next"><span>01</span><h2>{t('overview.taskTitle')}</h2><p>{t('overview.taskBody')}</p><Link to="/tasks">{t('overview.openTasks')}</Link></article>
    </section>
  </div>
}
