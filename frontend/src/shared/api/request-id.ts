/**
 * 请求标识生成：产出与后端 X-UrbanSafe-Request-Id 一致风格的 26 位 ULID。
 *
 * ULID = 10 位 Crockford Base32 时间戳（毫秒）+ 16 位随机部分。
 * 同一毫秒内单调递增，便于排序与关联日志。字母表剔除 I/L/O/U，避免与数字混淆。
 */

const ENCODING = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
const ENCODING_LEN = 32
const TIME_LEN = 10
const RANDOM_LEN = 16

export const REQUEST_ID_LENGTH = TIME_LEN + RANDOM_LEN
export const REQUEST_ID_ALPHABET = ENCODING

let lastTime = 0
let lastRandom: number[] = []

function randomIndices(count: number): number[] {
  const bytes = new Uint8Array(count)
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(bytes)
  } else {
    for (let i = 0; i < count; i += 1) bytes[i] = Math.floor(Math.random() * 256)
  }
  return Array.from(bytes, (b) => b % ENCODING_LEN)
}

function encodeTime(time: number): string {
  let str = ''
  let t = time
  for (let i = 0; i < TIME_LEN; i += 1) {
    str = ENCODING[t % ENCODING_LEN] + str
    t = Math.floor(t / ENCODING_LEN)
  }
  return str
}

function incrementRandom(): void {
  let i = lastRandom.length - 1
  while (i >= 0) {
    if (lastRandom[i] === ENCODING_LEN - 1) {
      lastRandom[i] = 0
      i -= 1
    } else {
      lastRandom[i] += 1
      return
    }
  }
}

function encodeRandom(indices: number[]): string {
  let str = ''
  for (let i = 0; i < indices.length; i += 1) str += ENCODING[indices[i]]
  return str
}

/**
 * 生成 26 位请求标识。可选传入时间戳用于测试与确定性场景。
 */
export function generateRequestId(now: number = Date.now()): string {
  if (lastRandom.length > 0 && now <= lastTime) {
    incrementRandom()
  } else {
    lastTime = now
    lastRandom = randomIndices(RANDOM_LEN)
  }
  return encodeTime(now) + encodeRandom(lastRandom)
}

/** 重置内部时钟状态，用于单元测试隔离。 */
export function resetRequestIdClock(): void {
  lastTime = 0
  lastRandom = []
}
