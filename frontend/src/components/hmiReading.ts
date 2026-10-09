export type ReadBinding = {
  deviceId: number
  pointId: number | null
  decimals: number
  unit: string
  staleSeconds: number
  activeValue: string
  activeText: string
  inactiveText: string
}
export type LiveValue = { pointId: number; deviceId: number; value: string; quality: string; collectedAt: string }
export type ReadStateKind = 'normal' | 'inactive' | 'unbound' | 'missing' | 'stale' | 'quality' | 'offline' | 'invalid'
export type ReadState = { text: string; status: string; active: boolean; time: string; kind: ReadStateKind }

export function validBinding(value: unknown): value is ReadBinding {
  if (!value || typeof value !== 'object') return false
  const binding = value as ReadBinding
  return Number.isSafeInteger(binding.deviceId) && binding.deviceId > 0 &&
    (binding.pointId === null || (Number.isSafeInteger(binding.pointId) && binding.pointId > 0)) &&
    Number.isInteger(binding.decimals) && binding.decimals >= 0 && binding.decimals <= 6 &&
    Number.isFinite(binding.staleSeconds) && binding.staleSeconds >= 5 && binding.staleSeconds <= 3600 &&
    [binding.unit, binding.activeValue, binding.activeText, binding.inactiveText].every(text => typeof text === 'string' && text.length <= 40)
}

export function readState(binding: ReadBinding | undefined, value: LiveValue | undefined, problem: string, now: number, lamp = false): ReadState {
  const lastText = () => {
    if (!value || !binding) return '—'
    if (lamp) return value.value === binding.activeValue ? binding.activeText : binding.inactiveText
    const number = Number(value.value)
    return value.value.trim() && Number.isFinite(number) ? `${number.toFixed(binding.decimals)}${binding.unit ? ` ${binding.unit}` : ''}` : '—'
  }
  const unavailable = (status: string, kind: ReadStateKind, preserve = false): ReadState => ({ text: preserve ? lastText() : '—', status, active: false, time: value?.collectedAt ?? '', kind })
  if (!binding?.pointId) return unavailable('未绑定', 'unbound')
  if (problem) return unavailable(problem === '读取失败' ? '通信中断' : problem, 'offline', true)
  if (!value || value.deviceId !== binding.deviceId || value.pointId !== binding.pointId) return unavailable('未采集', 'missing')
  if (value.quality !== 'GOOD') return unavailable(`质量异常：${value.quality || '未知'}`, 'quality', true)
  const timestamp = Date.parse(value.collectedAt)
  if (!Number.isFinite(timestamp)) return unavailable('时间无效', 'invalid', true)
  if (timestamp > now + 5000) return unavailable('时钟异常', 'invalid', true)
  if (now - timestamp > binding.staleSeconds * 1000) return unavailable('数据过期', 'stale', true)
  const active = value.value === binding.activeValue
  if (lamp) return { text: active ? binding.activeText : binding.inactiveText, status: '正常', active, time: value.collectedAt, kind: active ? 'normal' : 'inactive' }
  const number = Number(value.value)
  if (!value.value.trim() || !Number.isFinite(number)) return unavailable('非数值', 'invalid')
  return { text: `${number.toFixed(binding.decimals)}${binding.unit ? ` ${binding.unit}` : ''}`, status: '正常', active: false, time: value.collectedAt, kind: 'normal' }
}
