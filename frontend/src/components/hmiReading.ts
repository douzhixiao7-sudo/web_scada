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
export type ReadState = { text: string; status: string; active: boolean; time: string }

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
  const unavailable = (status: string): ReadState => ({ text: '—', status, active: false, time: value?.collectedAt ?? '' })
  if (!binding?.pointId) return unavailable('未绑定')
  if (problem) return unavailable(problem)
  if (!value || value.deviceId !== binding.deviceId || value.pointId !== binding.pointId) return unavailable('未采集')
  if (value.quality !== 'GOOD') return unavailable(`质量异常：${value.quality || '未知'}`)
  const timestamp = Date.parse(value.collectedAt)
  if (!Number.isFinite(timestamp)) return unavailable('时间无效')
  if (timestamp > now + 5000) return unavailable('时钟异常')
  if (now - timestamp > binding.staleSeconds * 1000) return unavailable('数据过期')
  const active = value.value === binding.activeValue
  if (lamp) return { text: active ? binding.activeText : binding.inactiveText, status: '正常', active, time: value.collectedAt }
  const number = Number(value.value)
  if (!value.value.trim() || !Number.isFinite(number)) return unavailable('非数值')
  return { text: `${number.toFixed(binding.decimals)}${binding.unit ? ` ${binding.unit}` : ''}`, status: '正常', active: false, time: value.collectedAt }
}
