<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type HealthComponent = { status?: string }
type HealthPayload = {
  status?: string
  components?: Record<string, HealthComponent>
}
type MenuKey = 'overview' | 'devices' | 'monitor' | 'alarms' | 'history' | 'hmi' | 'users' | 'settings'

type MenuItem = {
  key: MenuKey
  label: string
  helper: string
  icon: string
}

const menuItems: MenuItem[] = [
  { key: 'overview', label: '首页总览', helper: '运行态势', icon: '⌁' },
  { key: 'devices', label: '设备管理', helper: '站点与控制柜', icon: '▦' },
  { key: 'monitor', label: '实时监控', helper: '采集点位', icon: '◌' },
  { key: 'alarms', label: '报警中心', helper: '待确认事件', icon: '!' },
  { key: 'history', label: '历史数据', helper: '趋势与报表', icon: '∿' },
  { key: 'hmi', label: '组态画面', helper: '工艺流程', icon: '⌗' },
  { key: 'users', label: '用户与权限', helper: '角色策略', icon: '◎' },
  { key: 'settings', label: '系统设置', helper: '运行参数', icon: '⚙' },
]

const username = ref('admin')
const password = ref('')
const loginError = ref('')
const isAuthed = ref(false)
const activeMenu = ref<MenuKey>('overview')
const health = ref<HealthPayload | null>(null)
const healthText = ref('正在连接')
const checking = ref(false)

const activeItem = computed(() => menuItems.find((item) => item.key === activeMenu.value) ?? menuItems[0])
const dbStatus = computed(() => health.value?.components?.db?.status ?? 'UNKNOWN')
const redisStatus = computed(() => health.value?.components?.redis?.status ?? 'UNKNOWN')
const overallStatus = computed(() => health.value?.status ?? 'UNKNOWN')

const deviceRows = [
  { name: '一号加压泵站', area: '供水一区', status: '运行', load: '72%', signal: '2.4 s' },
  { name: '二号换热机组', area: '能源中心', status: '待机', load: '18%', signal: '3.1 s' },
  { name: '空压站 A 线', area: '动力车间', status: '运行', load: '64%', signal: '1.8 s' },
  { name: '污水提升井', area: '环保站', status: '告警', load: '91%', signal: '5.7 s' },
]

const alarmRows = [
  { level: '高', source: '污水提升井', message: '液位超过高高限', time: '19:08:12' },
  { level: '中', source: '空压站 A 线', message: '出口压力波动', time: '19:02:44' },
  { level: '低', source: '二号换热机组', message: '巡检计划待确认', time: '18:55:21' },
]

const trendBars = [42, 58, 53, 66, 71, 64, 77, 73, 81, 76, 88, 84]

async function checkBackend() {
  checking.value = true
  healthText.value = '正在连接'
  try {
    const response = await fetch('/api/actuator/health', {
      signal: AbortSignal.timeout(5000),
      cache: 'no-store',
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const result = (await response.json()) as HealthPayload
    health.value = result
    healthText.value = result.status === 'UP' ? '在线' : '异常'
  } catch {
    health.value = null
    healthText.value = '离线'
  } finally {
    checking.value = false
  }
}

function submitLogin() {
  if (!username.value.trim()) {
    loginError.value = '请输入账号'
    return
  }
  loginError.value = ''
  isAuthed.value = true
  void checkBackend()
}

function logout() {
  isAuthed.value = false
  activeMenu.value = 'overview'
}

onMounted(checkBackend)
</script>

<template>
  <main v-if="!isAuthed" class="login-shell">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="brand-block">
        <p class="eyebrow">WEB SCADA CONTROL CENTER</p>
        <h1 id="login-title">工业数据中台</h1>
        <p>面向设备运行、报警响应和生产态势的后台管理入口。</p>
      </div>

      <form class="login-card" @submit.prevent="submitLogin">
        <div class="status-strip" role="status" aria-live="polite">
          <span :class="['status-dot', overallStatus === 'UP' ? 'is-ok' : 'is-warn']"></span>
          后端 {{ healthText }} · MySQL {{ dbStatus }} · Redis {{ redisStatus }}
        </div>
        <label>
          <span>账号</span>
          <input v-model="username" autocomplete="username" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="password" type="password" autocomplete="current-password" placeholder="本阶段演示登录" />
        </label>
        <p v-if="loginError" class="form-error">{{ loginError }}</p>
        <button type="submit">进入后台</button>
        <button class="ghost" type="button" :disabled="checking" @click="checkBackend">
          {{ checking ? '检查中' : '检查连接' }}
        </button>
      </form>
    </section>
  </main>

  <main v-else class="app-shell">
    <aside class="sidebar" aria-label="后台菜单">
      <div class="product-mark">
        <span class="mark-grid"></span>
        <div>
          <strong>Web SCADA</strong>
          <small>工业管理后台</small>
        </div>
      </div>
      <nav>
        <button
          v-for="item in menuItems"
          :key="item.key"
          :class="['nav-item', { active: activeMenu === item.key }]"
          type="button"
          @click="activeMenu = item.key"
        >
          <span class="nav-icon" aria-hidden="true">{{ item.icon }}</span>
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.helper }}</small>
          </span>
        </button>
      </nav>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">{{ activeItem.helper }}</p>
          <h2>{{ activeItem.label }}</h2>
        </div>
        <div class="top-actions">
          <span class="health-pill"><span class="status-dot is-ok"></span> MySQL {{ dbStatus }}</span>
          <span class="health-pill"><span class="status-dot is-ok"></span> Redis {{ redisStatus }}</span>
          <button class="ghost compact" type="button" :disabled="checking" @click="checkBackend">刷新</button>
          <button class="ghost compact" type="button" @click="logout">退出</button>
        </div>
      </header>

      <section v-if="activeMenu === 'overview'" class="content-grid overview-grid">
        <article class="metric-card strong">
          <span>系统状态</span>
          <strong>{{ overallStatus }}</strong>
          <small>Actuator 健康检查</small>
        </article>
        <article class="metric-card">
          <span>在线设备</span>
          <strong>128</strong>
          <small>模拟站点资产</small>
        </article>
        <article class="metric-card warn">
          <span>当前报警</span>
          <strong>3</strong>
          <small>待确认事件</small>
        </article>
        <article class="metric-card">
          <span>采集延迟</span>
          <strong>2.8s</strong>
          <small>最近 5 分钟均值</small>
        </article>
        <article class="panel wide">
          <div class="panel-head">
            <h3>实时负载趋势</h3>
            <span>模拟数据</span>
          </div>
          <div class="trend" aria-label="实时负载趋势模拟图">
            <span v-for="(bar, index) in trendBars" :key="index" :style="{ height: `${bar}%` }"></span>
          </div>
        </article>
        <article class="panel">
          <div class="panel-head">
            <h3>连接状态</h3>
            <span>{{ healthText }}</span>
          </div>
          <dl class="status-list">
            <dt>后端</dt><dd>{{ overallStatus }}</dd>
            <dt>MySQL</dt><dd>{{ dbStatus }}</dd>
            <dt>Redis</dt><dd>{{ redisStatus }}</dd>
          </dl>
        </article>
      </section>

      <section v-else-if="activeMenu === 'devices' || activeMenu === 'monitor'" class="panel page-panel">
        <div class="panel-head">
          <h3>{{ activeMenu === 'devices' ? '设备台账' : '实时采集点位' }}</h3>
          <span>基础骨架</span>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>对象</th><th>区域</th><th>状态</th><th>负载</th><th>通讯</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in deviceRows" :key="row.name">
                <td>{{ row.name }}</td>
                <td>{{ row.area }}</td>
                <td><span :class="['tag', row.status === '告警' ? 'danger' : row.status === '待机' ? 'idle' : 'ok']">{{ row.status }}</span></td>
                <td>{{ row.load }}</td>
                <td>{{ row.signal }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="activeMenu === 'alarms'" class="panel page-panel">
        <div class="panel-head">
          <h3>报警事件</h3>
          <span>待确认 3 条</span>
        </div>
        <div class="alarm-list">
          <article v-for="alarm in alarmRows" :key="alarm.time">
            <span :class="['alarm-level', alarm.level === '高' ? 'danger' : alarm.level === '中' ? 'warn' : 'info']">{{ alarm.level }}</span>
            <div><strong>{{ alarm.message }}</strong><small>{{ alarm.source }} · {{ alarm.time }}</small></div>
            <button class="ghost compact" type="button">确认</button>
          </article>
        </div>
      </section>

      <section v-else class="empty-state">
        <p class="eyebrow">{{ activeItem.label }}</p>
        <h3>{{ activeItem.label }}页面骨架已预留</h3>
        <p>当前阶段只搭建后台信息架构和视觉基底，后续可继续接入真实业务接口、权限与数据模型。</p>
      </section>
    </section>
  </main>
</template>
