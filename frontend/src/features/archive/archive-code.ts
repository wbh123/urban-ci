export function suggestCommunityCode(
  now = new Date(),
  suffixFactory: () => string = defaultSuffix,
): string {
  return `COMM-${datePart(now)}-${suffixPart(suffixFactory())}`
}

export function suggestBuildingCode(
  now = new Date(),
  suffixFactory: () => string = defaultSuffix,
): string {
  return `BLDG-${datePart(now)}-${suffixPart(suffixFactory())}`
}

function datePart(value: Date): string {
  const year = value.getUTCFullYear()
  const month = String(value.getUTCMonth() + 1).padStart(2, '0')
  const day = String(value.getUTCDate()).padStart(2, '0')
  return `${year}${month}${day}`
}

function suffixPart(value: string): string {
  const normalized = value.toUpperCase().replace(/[^A-Z0-9]/g, '')
  return normalized || defaultSuffix()
}

function defaultSuffix(): string {
  return Math.random().toString(36).slice(2, 6).toUpperCase().padEnd(4, '0')
}
