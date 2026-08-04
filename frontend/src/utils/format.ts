export function formatDateTime(value: string | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-TW', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Taipei',
  }).format(date)
}

export function formatCurrency(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '—'
  }

  return new Intl.NumberFormat('zh-TW', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

export function formatFileSize(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value) || value < 0) {
    return '—'
  }

  if (value < 1024) {
    return `${Math.round(value)} B`
  }

  const units = ['KB', 'MB', 'GB']
  let size = value / 1024
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }

  const rounded = size >= 10 ? size.toFixed(1) : size.toFixed(2)
  return `${Number(rounded)} ${units[unitIndex]}`
}
