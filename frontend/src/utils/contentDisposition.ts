function cleanFilename(value: string | undefined): string | null {
  if (!value) {
    return null
  }

  const cleaned = value.trim().replace(/^"|"$/g, '').replace(/\\"/g, '"')
  if (
    cleaned === ''
    || cleaned.includes('..')
    || cleaned.includes('/')
    || cleaned.includes('\\')
    || cleaned.includes('\0')
    || [...cleaned].some((character) => character < ' ')
  ) {
    return null
  }
  return cleaned
}

function decodeExtendedFilename(value: string): string | null {
  const withoutQuotes = value.trim().replace(/^"|"$/g, '')
  const encodedValue = withoutQuotes.replace(/^UTF-8''/i, '')
  try {
    return cleanFilename(decodeURIComponent(encodedValue))
  } catch {
    return cleanFilename(encodedValue)
  }
}

export function getDownloadFilename(
  header: string | undefined,
  fallback: string,
): string {
  if (header) {
    const extended = header.match(/filename\*\s*=\s*([^;]+)/i)?.[1]
    const extendedFilename = extended === undefined
      ? null
      : decodeExtendedFilename(extended)
    if (extendedFilename) {
      return extendedFilename
    }

    const basic = header.match(/filename\s*=\s*("[^"]*"|[^;]+)/i)?.[1]
    const basicFilename = cleanFilename(basic)
    if (basicFilename) {
      return basicFilename
    }
  }

  return fallback
}
