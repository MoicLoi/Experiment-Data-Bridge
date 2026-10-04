import type { SupportedLocale } from './resources'

export const formatDateTime = (value: string | Date, locale: SupportedLocale) =>
  new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value))

export const formatNumber = (value: number, locale: SupportedLocale) => new Intl.NumberFormat(locale).format(value)

export function formatBytes(value: number, locale: SupportedLocale): string {
  if (value === 0) return '0 B'
  const unit = Math.min(Math.floor(Math.log(value) / Math.log(1024)), 4)
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(value / 1024 ** unit)} ${['B', 'KiB', 'MiB', 'GiB', 'TiB'][unit]}`
}
