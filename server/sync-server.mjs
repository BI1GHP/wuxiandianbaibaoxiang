/**
 * CW练考通 · 进度同步服务
 *
 * 零依赖 Node 服务：用「同步码」在 App 与网页之间搬运备份 JSON。
 * 启动：node server/sync-server.mjs        （默认端口 8787）
 *      PORT=9000 node server/sync-server.mjs
 *
 * 接口：
 *   GET  /api/sync?code=ABC123   读取
 *   POST /api/sync               { code, payload } 覆盖写入
 *   POST /api/account/register   { username, password } 注册并返回 token
 *   POST /api/account/login      { username, password } 登录并返回 token
 *   POST /api/account/push       { payload } 上传（需 x-account-token）
 *   GET  /api/account/pull       下载（需 x-account-token）
 *   GET  /api/member/config      会员功能开关（公开，App / 网页读取）
 *   GET  /api/member/status      当前账号会员状态（需 x-account-token）
 *   POST /api/member/checkout    下单入口（功能关闭时 403；支付未接入时 501）
 *   POST /api/member/redeem      兑换码开通 / 续期（需 x-account-token）
 *   POST /api/admin/login        { password } 管理员登录，返回 admin token
 *   GET  /api/admin/member       读取会员开关（需 x-admin-token）
 *   POST /api/admin/member       { enabled, password } 开启 / 关闭（关闭需密码）
 *   POST /api/admin/grant        { username, days } 给账号开通 / 取消会员
 *   POST /api/admin/codes        { days, count } 批量生成兑换码
 *   GET  /api/admin/codes        兑换码列表与使用情况
 *   GET  /health                健康检查
 *   GET  /                      网页同步页
 *   GET  /admin.html            管理后台
 *
 * 数据落在 server/data/<code>.json；同步码即密钥，请勿公开传播。
 * 账号数据落在 server/data/accounts/<id>.json，密码只存 scrypt 哈希。
 * 会员开关落在 server/data/member.json，默认关闭；管理员密码默认 Hz@18601044023，
 * 可用环境变量 ADMIN_PASSWORD 覆盖。兑换码落在 server/data/codes.json。
 */
import http from 'node:http'
import { createHash, randomBytes, randomInt, scryptSync, timingSafeEqual } from 'node:crypto'
import { mkdir, readdir, readFile, unlink, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const DATA_DIR = path.join(HERE, 'data')
const ACCOUNTS_DIR = path.join(DATA_DIR, 'accounts')
const PUBLIC_DIR = path.join(HERE, 'public')
const PORT = Number(process.env.PORT || 8787)
/** 监听地址；反向代理后建议设为 127.0.0.1，避免端口直接暴露 */
const HOST = process.env.HOST || '0.0.0.0'
const MAX_BODY = 512 * 1024
const CODE_RE = /^[A-Z0-9]{4,12}$/

/** 同步数据保留天数，0 表示永不过期 */
const TTL_DAYS = Number(process.env.SYNC_TTL_DAYS ?? 180)
const TTL_MS = TTL_DAYS > 0 ? TTL_DAYS * 86_400_000 : 0

/** 可选全局访问口令；设置后 App / 网页都需带上 x-sync-token */
const SYNC_TOKEN = process.env.SYNC_TOKEN || ''

/** 可选教练口令；设置后教练看板需带 x-coach-token，避免公网被随意汇总 */
const COACH_TOKEN = process.env.COACH_TOKEN || ''

/** 会员功能开关文件；默认关闭，需管理后台手动开启 */
const MEMBER_FILE = path.join(DATA_DIR, 'member.json')
const DEFAULT_MEMBER = { enabled: false, price: 18, updatedAt: 0 }

/** 兑换码（卡密）：支付渠道接入前的开通方式 */
const CODES_FILE = path.join(DATA_DIR, 'codes.json')

/** 管理后台密码：默认 Hz@18601044023，可用 ADMIN_PASSWORD 覆盖 */
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'Hz@18601044023'

/** admin token 有效期 2 小时，仅存内存（进程重启失效） */
const ADMIN_TTL_MS = 2 * 3600_000
const adminTokens = new Map()

// 目录仅服务账号可读写（Linux 生效，Windows 忽略）
await mkdir(DATA_DIR, { recursive: true, mode: 0o700 })
await mkdir(ACCOUNTS_DIR, { recursive: true, mode: 0o700 })

/** 删除超过保留期的同步记录，返回删除条数 */
async function cleanupExpired() {
  if (!TTL_MS) return 0
  const now = Date.now()
  let removed = 0
  const files = await readdir(DATA_DIR).catch(() => [])
  for (const name of files) {
    if (!name.endsWith('.json')) continue
    // 会员开关与兑换码不是同步记录，不参与过期清理
    if (name === 'member.json' || name === 'codes.json') continue
    const file = path.join(DATA_DIR, name)
    try {
      const record = JSON.parse(await readFile(file, 'utf8'))
      if (now - Number(record.updatedAt || 0) > TTL_MS) {
        await unlink(file)
        removed += 1
      }
    } catch {
      // 损坏或无法解析的文件同样视为过期垃圾
      await unlink(file).catch(() => {})
      removed += 1
    }
  }
  return removed
}

/** 简单限流：每 IP 每分钟 60 次 */
const hits = new Map()
function rateLimited(ip) {
  const now = Date.now()
  const list = (hits.get(ip) || []).filter((t) => now - t < 60_000)
  list.push(now)
  hits.set(ip, list)
  return list.length > 60
}

const CORS = {
  'access-control-allow-origin': '*',
  'access-control-allow-methods': 'GET,POST,OPTIONS',
  'access-control-allow-headers': 'content-type,x-sync-token,x-coach-token,x-account-token,x-admin-token'
}

/**
 * 未命中计数：防止用短同步码暴力枚举。
 * 同一 IP 10 分钟内 15 次 404 → 封禁 10 分钟。
 */
const misses = new Map()

function blocked(ip) {
  const item = misses.get(ip)
  if (!item) return false
  if (item.until && Date.now() < item.until) return true
  if (item.until && Date.now() >= item.until) {
    misses.delete(ip)
  }
  return false
}

function noteMiss(ip) {
  const now = Date.now()
  const item = misses.get(ip) || { count: 0, since: now, until: 0 }
  if (now - item.since > 10 * 60_000) {
    item.count = 0
    item.since = now
  }
  item.count += 1
  if (item.count >= 15) item.until = now + 10 * 60_000
  misses.set(ip, item)
}

function noteHit(ip) {
  misses.delete(ip)
}

function tokenAllowed(req) {
  if (!SYNC_TOKEN) return true
  return req.headers['x-sync-token'] === SYNC_TOKEN
}

function coachAllowed(req) {
  if (!COACH_TOKEN) return true
  return req.headers['x-coach-token'] === COACH_TOKEN
}

// —— 账号云同步：用户名 + 密码，密码只存 scrypt 哈希，token 登录后签发 ——

const USER_RE = /^[A-Za-z0-9_]{3,20}$/
const PASS_MIN = 6
const PASS_MAX = 64

/** 用户名 → 固定长度的文件 ID，避免中文/大小写/路径注入问题 */
function accountId(username) {
  return createHash('sha256')
    .update('cw:' + username.toLowerCase())
    .digest('hex')
    .slice(0, 24)
}

function hashPassword(password, salt) {
  return scryptSync(password, salt, 64).toString('hex')
}

function hashToken(token) {
  return createHash('sha256').update(token).digest('hex')
}

function sameHash(a, b) {
  const x = Buffer.from(String(a), 'hex')
  const y = Buffer.from(String(b), 'hex')
  return x.length === y.length && timingSafeEqual(x, y)
}

function accountFile(id) {
  return path.join(ACCOUNTS_DIR, `${id}.json`)
}

async function readAccount(id) {
  try {
    return JSON.parse(await readFile(accountFile(id), 'utf8'))
  } catch {
    return null
  }
}

async function writeAccount(record) {
  await writeFile(accountFile(record.id), JSON.stringify(record), {
    encoding: 'utf8',
    mode: 0o600
  })
}

/** 解析 x-account-token（id.secret），命中返回账号记录，否则 null */
async function accountFromRequest(req) {
  const raw = String(req.headers['x-account-token'] || '').trim()
  const dot = raw.indexOf('.')
  if (dot <= 0) return null
  const id = raw.slice(0, dot)
  if (!/^[a-f0-9]{24}$/.test(id)) return null
  const record = await readAccount(id)
  if (!record || !Array.isArray(record.tokens)) return null
  const want = hashToken(raw)
  if (!record.tokens.some((t) => sameHash(t, want))) return null
  return record
}

/** 注册 / 登录成功后签发 token：id.secret，只存 secret 的哈希 */
function issueToken(record) {
  const token = `${record.id}.${randomBytes(32).toString('hex')}`
  record.tokens = [hashToken(token), ...(record.tokens || [])].slice(0, 5)
  return token
}

/** 校验备份结构，账号与同步码共用 */
function validPayload(payload) {
  return Boolean(payload && typeof payload === 'object' && payload.prefs)
}

// —— 会员开关与管理后台 ——

async function readMemberConfig() {
  try {
    const cfg = JSON.parse(await readFile(MEMBER_FILE, 'utf8'))
    return {
      enabled: Boolean(cfg.enabled),
      price: Number(cfg.price) || DEFAULT_MEMBER.price,
      updatedAt: Number(cfg.updatedAt || 0)
    }
  } catch {
    return { ...DEFAULT_MEMBER }
  }
}

async function writeMemberConfig(cfg) {
  await writeFile(MEMBER_FILE, JSON.stringify(cfg), { encoding: 'utf8', mode: 0o600 })
}

/** 常量时间字符串比较，避免时序侧信道 */
function sameText(a, b) {
  const x = createHash('sha256').update(String(a)).digest()
  const y = createHash('sha256').update(String(b)).digest()
  return timingSafeEqual(x, y)
}

function adminAuthed(req) {
  const token = String(req.headers['x-admin-token'] || '').trim()
  const until = adminTokens.get(token)
  if (!until) return false
  if (Date.now() > until) {
    adminTokens.delete(token)
    return false
  }
  return true
}

/** 给账号续期（days=0 表示取消），返回新的到期时间 */
function grantMember(record, days) {
  if (days <= 0) {
    record.memberUntil = 0
    return 0
  }
  const base = Math.max(Date.now(), Number(record.memberUntil) || 0)
  record.memberUntil = base + days * 86_400_000
  return record.memberUntil
}

// —— 兑换码（卡密）：支付接入前的权益开通方式 ——

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'

async function readCodes() {
  try {
    const obj = JSON.parse(await readFile(CODES_FILE, 'utf8'))
    return obj && typeof obj === 'object' ? obj : {}
  } catch {
    return {}
  }
}

async function writeCodes(codes) {
  await writeFile(CODES_FILE, JSON.stringify(codes), { encoding: 'utf8', mode: 0o600 })
}

function newRedeemCode() {
  let out = ''
  for (let i = 0; i < 12; i++) out += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)]
  return out
}

/** 用户输入容错：去掉空格、横线与大小写差异 */
function normalizeCode(raw) {
  return String(raw || '').toUpperCase().replace(/[^A-Z0-9]/g, '')
}

// —— 教练看板：从备份 JSON 中抽取进度摘要（不落盘、不返回完整备份） ——

/** 备份里每个值带类型包装 { t, v }，这里统一取值 */
function prefValue(prefs, group, key) {
  const item = prefs?.[group]?.[key]
  return item && typeof item === 'object' ? item.v : undefined
}

function prefString(prefs, group, key) {
  const v = prefValue(prefs, group, key)
  return v == null ? '' : String(v)
}

function prefNumber(prefs, group, key) {
  const n = Number(prefValue(prefs, group, key))
  return Number.isFinite(n) ? n : 0
}

function parseJsonText(text, fallback) {
  if (typeof text !== 'string' || !text.trim()) return fallback
  try {
    return JSON.parse(text)
  } catch {
    return fallback
  }
}

function dayKey(ts) {
  const d = new Date(ts)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function dayTotal(d) {
  return Number(d?.learnCount || 0) + Number(d?.copyCount || 0) +
    Number(d?.contestCount || 0) + Number(d?.accentCount || 0)
}

/** 把一份备份摘要成看板所需字段 */
function summarizeMember(code, record) {
  const payload = record.payload || {}
  const prefs = payload.prefs || {}
  const history = parseJsonText(prefString(prefs, 'wc_stats', 'history'), [])
  const byDate = new Map()
  if (Array.isArray(history)) {
    for (const d of history) if (d && d.date) byDate.set(String(d.date), d)
  }

  const days = []
  let weekTotal = 0
  for (let i = 6; i >= 0; i--) {
    const date = dayKey(Date.now() - i * 86_400_000)
    const total = dayTotal(byDate.get(date))
    weekTotal += total
    days.push({ date, total })
  }

  // 连续天数：今天还没练则从昨天往前算
  let streak = 0
  for (let i = 0; i <= 365; i++) {
    const total = dayTotal(byDate.get(dayKey(Date.now() - i * 86_400_000)))
    if (total > 0) {
      streak += 1
    } else if (i > 0) {
      break
    }
  }

  const charStats = parseJsonText(prefString(prefs, 'wc_stats', 'charStats'), {})
  const weak = Object.entries(charStats)
    .map(([ch, arr]) => ({ ch, miss: Number(arr?.[1] || 0) - Number(arr?.[0] || 0) }))
    .filter((x) => x.miss > 0)
    .sort((a, b) => b.miss - a.miss)
    .slice(0, 3)

  return {
    code,
    found: true,
    updatedAt: Number(record.updatedAt || 0),
    callsign: prefString(prefs, 'wc_trainer', 'callsign') || '未设置',
    opClass: prefString(prefs, 'wc_trainer', 'opClass') || '',
    version: String(payload.version || ''),
    learnTotal: prefNumber(prefs, 'wc_stats', 'learnTotal'),
    learnCorrect: prefNumber(prefs, 'wc_stats', 'learnCorrect'),
    copyBest: prefNumber(prefs, 'wc_stats', 'copyBest'),
    contestBest: prefNumber(prefs, 'wc_stats', 'contestBest'),
    streak,
    weekTotal,
    days,
    weak
  }
}

function send(res, status, body) {
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    ...CORS
  })
  res.end(typeof body === 'string' ? body : JSON.stringify(body))
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks = []
    req.on('data', (chunk) => {
      size += chunk.length
      if (size > MAX_BODY) {
        reject(new Error('请求体过大'))
        req.destroy()
        return
      }
      chunks.push(chunk)
    })
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    req.on('error', reject)
  })
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.json': 'application/json; charset=utf-8'
}

async function serveStatic(res, pathname) {
  const rel = pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, '')
  const file = path.join(PUBLIC_DIR, rel)
  if (!file.startsWith(PUBLIC_DIR)) return send(res, 403, { ok: false, error: 'forbidden' })
  try {
    const data = await readFile(file)
    res.writeHead(200, {
      'content-type': MIME[path.extname(file)] || 'application/octet-stream',
      'cache-control': 'no-cache'
    })
    res.end(data)
  } catch {
    send(res, 404, { ok: false, error: 'not found' })
  }
}

const server = http.createServer(async (req, res) => {
  const ip = req.socket.remoteAddress || 'unknown'
  if (req.method === 'OPTIONS') {
    res.writeHead(204, CORS)
    res.end()
    return
  }

  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`)
  const pathname = url.pathname

  try {
    if (pathname === '/health') {
      return send(res, 200, { ok: true, service: 'cw-sync', time: Date.now() })
    }

    if (pathname === '/api/sync' && req.method === 'POST') {
      if (!tokenAllowed(req)) {
        return send(res, 401, { ok: false, error: '访问口令不正确' })
      }
      if (blocked(ip)) {
        return send(res, 429, { ok: false, error: '尝试次数过多，请 10 分钟后再试' })
      }
      if (rateLimited(ip)) return send(res, 429, { ok: false, error: '操作太频繁，请稍后再试' })

      const raw = await readBody(req)
      let body
      try {
        body = JSON.parse(raw)
      } catch {
        return send(res, 400, { ok: false, error: 'JSON 格式错误' })
      }

      const code = String(body?.code || '').toUpperCase()
      if (!CODE_RE.test(code)) {
        return send(res, 400, { ok: false, error: '同步码需为 4–12 位字母或数字' })
      }
      const payload = body?.payload
      if (!payload || typeof payload !== 'object' || !payload.prefs) {
        return send(res, 400, { ok: false, error: '缺少有效的备份数据' })
      }

      const record = {
        code,
        updatedAt: Date.now(),
        payload
      }
      await writeFile(
        path.join(DATA_DIR, `${code}.json`),
        JSON.stringify(record),
        { encoding: 'utf8', mode: 0o600 }
      )
      return send(res, 200, { ok: true, code, updatedAt: record.updatedAt })
    }

    if (pathname === '/api/sync' && req.method === 'GET') {
      if (!tokenAllowed(req)) {
        return send(res, 401, { ok: false, error: '访问口令不正确' })
      }
      if (blocked(ip)) {
        return send(res, 429, { ok: false, error: '尝试次数过多，请 10 分钟后再试' })
      }
      const code = String(url.searchParams.get('code') || '').toUpperCase()
      if (!CODE_RE.test(code)) {
        return send(res, 400, { ok: false, error: '同步码格式不正确' })
      }
      try {
        const file = path.join(DATA_DIR, `${code}.json`)
        const record = JSON.parse(await readFile(file, 'utf8'))
        if (TTL_MS && Date.now() - Number(record.updatedAt || 0) > TTL_MS) {
          await unlink(file).catch(() => {})
          return send(res, 404, {
            ok: false,
            error: `该同步码已超过 ${TTL_DAYS} 天有效期，请重新上传`
          })
        }
        noteHit(ip)
        return send(res, 200, { ok: true, ...record })
      } catch {
        noteMiss(ip)
        return send(res, 404, { ok: false, error: '该同步码还没有上传过进度' })
      }
    }

    // 教练看板：一次汇总多个学员同步码，只返回摘要
    if (pathname === '/api/coach/summary' && req.method === 'POST') {
      if (!coachAllowed(req)) {
        return send(res, 401, { ok: false, error: '教练口令不正确' })
      }
      if (rateLimited(ip)) return send(res, 429, { ok: false, error: '操作太频繁，请稍后再试' })

      const raw = await readBody(req)
      let body
      try {
        body = JSON.parse(raw)
      } catch {
        return send(res, 400, { ok: false, error: 'JSON 格式错误' })
      }

      const codes = [...new Set(
        (Array.isArray(body?.codes) ? body.codes : [])
          .map((c) => String(c || '').toUpperCase().trim())
          .filter(Boolean)
      )]
      if (!codes.length) {
        return send(res, 400, { ok: false, error: '请提供至少一个同步码' })
      }
      if (codes.length > 50) {
        return send(res, 400, { ok: false, error: '一次最多汇总 50 个同步码' })
      }

      const members = []
      const missing = []
      for (const code of codes) {
        if (!CODE_RE.test(code)) {
          missing.push(code)
          continue
        }
        try {
          const record = JSON.parse(await readFile(path.join(DATA_DIR, `${code}.json`), 'utf8'))
          if (TTL_MS && Date.now() - Number(record.updatedAt || 0) > TTL_MS) {
            missing.push(code)
            continue
          }
          members.push(summarizeMember(code, record))
        } catch {
          missing.push(code)
        }
      }
      members.sort((a, b) => b.updatedAt - a.updatedAt)
      return send(res, 200, { ok: true, time: Date.now(), members, missing })
    }

    // —— 账号：注册 / 登录 / 上传 / 下载 ——
    if (pathname.startsWith('/api/account/') && req.method === 'POST' &&
      (pathname === '/api/account/register' || pathname === '/api/account/login')
    ) {
      if (rateLimited(ip)) return send(res, 429, { ok: false, error: '操作太频繁，请稍后再试' })
      if (blocked(ip)) {
        return send(res, 429, { ok: false, error: '尝试次数过多，请 10 分钟后再试' })
      }

      const raw = await readBody(req)
      let body
      try {
        body = JSON.parse(raw)
      } catch {
        return send(res, 400, { ok: false, error: 'JSON 格式错误' })
      }

      const username = String(body?.username || '').trim()
      const password = String(body?.password || '')
      if (!USER_RE.test(username)) {
        return send(res, 400, { ok: false, error: '用户名需为 3–20 位字母、数字或下划线' })
      }
      if (password.length < PASS_MIN || password.length > PASS_MAX) {
        return send(res, 400, { ok: false, error: `密码需为 ${PASS_MIN}–${PASS_MAX} 位` })
      }

      const id = accountId(username)
      let record = await readAccount(id)

      if (pathname === '/api/account/register') {
        if (record) {
          noteMiss(ip)
          return send(res, 409, { ok: false, error: '该用户名已被注册' })
        }
        const salt = randomBytes(16).toString('hex')
        record = {
          id,
          username,
          salt,
          hash: hashPassword(password, salt),
          tokens: [],
          createdAt: Date.now(),
          updatedAt: 0,
          payload: null
        }
        const token = issueToken(record)
        await writeAccount(record)
        return send(res, 200, {
          ok: true,
          username: record.username,
          token,
          updatedAt: 0,
          hasPayload: false
        })
      }

      // 登录
      if (!record || !sameHash(record.hash, hashPassword(password, record.salt))) {
        noteMiss(ip)
        return send(res, 401, { ok: false, error: '用户名或密码不正确' })
      }
      const token = issueToken(record)
      await writeAccount(record)
      noteHit(ip)
      return send(res, 200, {
        ok: true,
        username: record.username,
        token,
        updatedAt: Number(record.updatedAt || 0),
        hasPayload: Boolean(record.payload)
      })
    }

    if (pathname === '/api/account/push' && req.method === 'POST') {
      if (rateLimited(ip)) return send(res, 429, { ok: false, error: '操作太频繁，请稍后再试' })
      const record = await accountFromRequest(req)
      if (!record) return send(res, 401, { ok: false, error: '登录已失效，请重新登录' })

      const raw = await readBody(req)
      let body
      try {
        body = JSON.parse(raw)
      } catch {
        return send(res, 400, { ok: false, error: 'JSON 格式错误' })
      }
      if (!validPayload(body?.payload)) {
        return send(res, 400, { ok: false, error: '缺少有效的备份数据' })
      }

      record.payload = body.payload
      record.updatedAt = Date.now()
      await writeAccount(record)
      return send(res, 200, { ok: true, updatedAt: record.updatedAt })
    }

    if (pathname === '/api/account/pull' && req.method === 'GET') {
      if (rateLimited(ip)) return send(res, 429, { ok: false, error: '操作太频繁，请稍后再试' })
      const record = await accountFromRequest(req)
      if (!record) return send(res, 401, { ok: false, error: '登录已失效，请重新登录' })
      if (!record.payload) {
        return send(res, 404, { ok: false, error: '该账号还没有上传过进度' })
      }
      return send(res, 200, {
        ok: true,
        username: record.username,
        updatedAt: Number(record.updatedAt || 0),
        payload: record.payload
      })
    }

    // —— 会员：公开配置 / 账号状态 / 下单占位 ——
    if (pathname === '/api/member/config' && req.method === 'GET') {
      const cfg = await readMemberConfig()
      return send(res, 200, { ok: true, ...cfg })
    }

    if (pathname === '/api/member/status' && req.method === 'GET') {
      if (rateLimited(ip)) return send(res, 429, { ok: false, error: '操作太频繁，请稍后再试' })
      const record = await accountFromRequest(req)
      if (!record) return send(res, 401, { ok: false, error: '登录已失效，请重新登录' })
      const cfg = await readMemberConfig()
      const until = Number(record.memberUntil || 0)
      return send(res, 200, {
        ok: true,
        username: record.username,
        enabled: cfg.enabled,
        price: cfg.price,
        memberUntil: until,
        active: until > Date.now()
      })
    }

    if (pathname === '/api/member/checkout' && req.method === 'POST') {
      if (rateLimited(ip)) return send(res, 429, { ok: false, error: '操作太频繁，请稍后再试' })
      const cfg = await readMemberConfig()
      if (!cfg.enabled) {
        return send(res, 403, { ok: false, error: '会员功能暂未开放' })
      }
      const record = await accountFromRequest(req)
      if (!record) return send(res, 401, { ok: false, error: '请先在账号云同步登录' })
      // 支付渠道尚未接入：先保留接口与订单入口，不产生任何扣费
      return send(res, 501, { ok: false, error: '支付渠道尚未接入，可使用兑换码开通或联系管理员' })
    }

    // 兑换码开通：校验卡密 → 续期账号 → 卡密标记已用
    if (pathname === '/api/member/redeem' && req.method === 'POST') {
      if (rateLimited(ip)) return send(res, 429, { ok: false, error: '操作太频繁，请稍后再试' })
      const cfg = await readMemberConfig()
      if (!cfg.enabled) {
        return send(res, 403, { ok: false, error: '会员功能暂未开放' })
      }
      const record = await accountFromRequest(req)
      if (!record) return send(res, 401, { ok: false, error: '请先在账号云同步登录' })

      const raw = await readBody(req)
      let body
      try {
        body = JSON.parse(raw)
      } catch {
        return send(res, 400, { ok: false, error: 'JSON 格式错误' })
      }
      const code = normalizeCode(body?.code)
      if (code.length < 8) {
        return send(res, 400, { ok: false, error: '兑换码格式不正确' })
      }

      const codes = await readCodes()
      const item = codes[code]
      if (!item) return send(res, 404, { ok: false, error: '兑换码无效' })
      if (item.usedBy) {
        return send(res, 409, { ok: false, error: `该兑换码已被 ${item.usedBy} 使用` })
      }

      const days = Number(item.days) || 0
      const memberUntil = grantMember(record, days)
      item.usedBy = record.username
      item.usedAt = Date.now()
      codes[code] = item
      await writeAccount(record)
      await writeCodes(codes)
      return send(res, 200, { ok: true, username: record.username, days, memberUntil })
    }

    // —— 管理后台 ——
    if (pathname === '/api/admin/login' && req.method === 'POST') {
      if (blocked(ip)) {
        return send(res, 429, { ok: false, error: '尝试次数过多，请 10 分钟后再试' })
      }
      if (rateLimited(ip)) return send(res, 429, { ok: false, error: '操作太频繁，请稍后再试' })

      const raw = await readBody(req)
      let body
      try {
        body = JSON.parse(raw)
      } catch {
        return send(res, 400, { ok: false, error: 'JSON 格式错误' })
      }
      if (!sameText(body?.password || '', ADMIN_PASSWORD)) {
        noteMiss(ip)
        return send(res, 401, { ok: false, error: '管理员密码不正确' })
      }

      noteHit(ip)
      // 顺手清理过期 token，避免内存无限增长
      const now = Date.now()
      for (const [key, until] of adminTokens) {
        if (now > until) adminTokens.delete(key)
      }
      const token = randomBytes(24).toString('hex')
      const expiresAt = now + ADMIN_TTL_MS
      adminTokens.set(token, expiresAt)
      return send(res, 200, { ok: true, token, expiresAt })
    }

    if (pathname === '/api/admin/member' && req.method === 'GET') {
      if (!adminAuthed(req)) return send(res, 401, { ok: false, error: '管理员登录已失效' })
      return send(res, 200, { ok: true, ...(await readMemberConfig()) })
    }

    if (pathname === '/api/admin/member' && req.method === 'POST') {
      if (!adminAuthed(req)) return send(res, 401, { ok: false, error: '管理员登录已失效' })
      const raw = await readBody(req)
      let body
      try {
        body = JSON.parse(raw)
      } catch {
        return send(res, 400, { ok: false, error: 'JSON 格式错误' })
      }

      const enabled = Boolean(body?.enabled)
      // 关闭会员功能必须再次校验管理员密码
      if (!enabled && !sameText(body?.password || '', ADMIN_PASSWORD)) {
        return send(res, 403, { ok: false, error: '关闭会员功能需要管理员密码' })
      }

      const cfg = await readMemberConfig()
      cfg.enabled = enabled
      cfg.updatedAt = Date.now()
      await writeMemberConfig(cfg)
      return send(res, 200, { ok: true, ...cfg })
    }

    if (pathname === '/api/admin/grant' && req.method === 'POST') {
      if (!adminAuthed(req)) return send(res, 401, { ok: false, error: '管理员登录已失效' })
      const raw = await readBody(req)
      let body
      try {
        body = JSON.parse(raw)
      } catch {
        return send(res, 400, { ok: false, error: 'JSON 格式错误' })
      }

      const username = String(body?.username || '').trim()
      if (!USER_RE.test(username)) {
        return send(res, 400, { ok: false, error: '用户名格式不正确' })
      }
      const days = Math.floor(Number(body?.days))
      if (!Number.isFinite(days) || days < 0 || days > 3650) {
        return send(res, 400, { ok: false, error: '天数需为 0–3650' })
      }

      const record = await readAccount(accountId(username))
      if (!record) return send(res, 404, { ok: false, error: '该账号不存在' })
      const memberUntil = grantMember(record, days)
      await writeAccount(record)
      return send(res, 200, { ok: true, username: record.username, memberUntil })
    }

    if (pathname === '/api/admin/codes' && req.method === 'GET') {
      if (!adminAuthed(req)) return send(res, 401, { ok: false, error: '管理员登录已失效' })
      const codes = await readCodes()
      const list = Object.entries(codes)
        .map(([code, item]) => ({
          code,
          days: Number(item.days) || 0,
          createdAt: Number(item.createdAt) || 0,
          usedBy: item.usedBy || '',
          usedAt: Number(item.usedAt) || 0
        }))
        .sort((a, b) => b.createdAt - a.createdAt)
        .slice(0, 300)
      return send(res, 200, {
        ok: true,
        codes: list,
        unused: list.filter((c) => !c.usedBy).length,
        total: Object.keys(codes).length
      })
    }

    if (pathname === '/api/admin/codes' && req.method === 'POST') {
      if (!adminAuthed(req)) return send(res, 401, { ok: false, error: '管理员登录已失效' })
      const raw = await readBody(req)
      let body
      try {
        body = JSON.parse(raw)
      } catch {
        return send(res, 400, { ok: false, error: 'JSON 格式错误' })
      }

      const days = Math.floor(Number(body?.days))
      const count = Math.floor(Number(body?.count))
      if (!Number.isFinite(days) || days < 1 || days > 3650) {
        return send(res, 400, { ok: false, error: '天数需为 1–3650' })
      }
      if (!Number.isFinite(count) || count < 1 || count > 100) {
        return send(res, 400, { ok: false, error: '一次最多生成 100 个兑换码' })
      }

      const codes = await readCodes()
      const created = []
      for (let i = 0; i < count; i++) {
        let code = newRedeemCode()
        while (codes[code]) code = newRedeemCode()
        codes[code] = { days, createdAt: Date.now(), usedBy: '', usedAt: 0 }
        created.push(code)
      }
      await writeCodes(codes)
      return send(res, 200, { ok: true, days, codes: created })
    }

    if (pathname.startsWith('/api/')) {
      return send(res, 404, { ok: false, error: '接口不存在' })
    }

    return await serveStatic(res, pathname)
  } catch (error) {
    return send(res, 500, { ok: false, error: error.message || '服务器内部错误' })
  }
})

server.listen(PORT, HOST, async () => {
  console.log(`CW sync server: http://${HOST}:${PORT}`)
  console.log(`数据目录: ${DATA_DIR}`)
  console.log(TTL_MS ? `保留期: ${TTL_DAYS} 天` : '保留期: 永不过期')
  console.log(SYNC_TOKEN ? '访问口令: 已启用' : '访问口令: 未启用')
  console.log(COACH_TOKEN ? '教练口令: 已启用' : '教练看板: 无需口令')
  const memberCfg = await readMemberConfig()
  console.log(`会员功能: ${memberCfg.enabled ? '已开放' : '暂未开放'}（管理后台 /admin.html）`)
  if (!process.env.ADMIN_PASSWORD) console.log('管理密码: 使用默认值，建议设置 ADMIN_PASSWORD')

  const removed = await cleanupExpired()
  if (removed) console.log(`启动清理: 删除 ${removed} 条过期记录`)

  // 每 6 小时巡检一次；unref 保证定时器不阻止进程退出
  if (TTL_MS) {
    setInterval(async () => {
      const count = await cleanupExpired()
      if (count) console.log(`定时清理: 删除 ${count} 条过期记录`)
    }, 6 * 3600_000).unref()
  }
})
