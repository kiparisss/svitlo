// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: deep-green; icon-glyph: bolt;

/*
 * ⚡ Світло · Житомир
 * Віджет графіка погодинних відключень АТ «Житомиробленерго» для Scriptable (iOS).
 *
 * Налаштування:
 *   1. Довго натисніть на віджет → «Редагувати віджет».
 *   2. Script: «svitlo-zhytomyr», Parameter: ваша підчерга, напр. 3.1
 *   Свою підчергу можна знайти за адресою на https://www.ztoe.com.ua/unhooking-search.php
 *
 * Розміри: малий, середній, а також віджети екрана блокування
 * (прямокутний, круглий, рядковий).
 */

const DEFAULT_QUEUE = "1.1" // використовується, якщо параметр віджета порожній
const SOURCE_URL = "https://www.ztoe.com.ua/unhooking-search.php"
const SLOTS_PER_DAY = 48
const CACHE_FILE = "svitlo-zhytomyr-cache.json"

/* ───────────────────────── Розбір сторінки ───────────────────────── */

// Сторінка у windows-1251; нам потрібні лише ASCII-частини (розмітка, цифри, дати),
// тому декодуємо байти вручну й замінюємо не-ASCII на пробіл.
function base64ToAscii(b64) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
  const lookup = {}
  for (let i = 0; i < 64; i++) lookup[alphabet[i]] = i
  const out = []
  let buf = 0, bits = 0
  for (let k = 0; k < b64.length; k++) {
    const v = lookup[b64[k]]
    if (v === undefined) continue
    buf = (buf << 6) | v
    bits += 6
    if (bits >= 8) {
      bits -= 8
      const byte = (buf >> bits) & 0xff
      buf &= (1 << bits) - 1
      out.push(byte < 128 ? String.fromCharCode(byte) : " ")
    }
  }
  return out.join("")
}

function parseQueue(raw) {
  const s = String(raw || "").trim().replace(",", ".")
  const m = s.match(/^([1-6])\.([12])$/)
  if (!m) throw new Error(`Невірна підчерга «${s}». Вкажіть у параметрі віджета, напр. 3.1`)
  const q = Number(m[1]), sub = Number(m[2])
  return { label: `${q}.${sub}`, pid: (q - 1) * 2 + sub }
}

function hexToRgb(hex) {
  let h = hex.replace("#", "")
  if (h.length === 3) h = h.split("").map(c => c + c).join("")
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)]
}

const isReddish = ([r, g, b]) => r >= 170 && g <= 110 && b <= 110

function colorStringIsRed(s) {
  s = s.toLowerCase()
  if (/\b(red|crimson|tomato|firebrick|darkred|orangered)\b/.test(s)) return true
  for (const h of s.match(/#(?:[0-9a-f]{6}|[0-9a-f]{3})\b/g) || []) {
    if (isReddish(hexToRgb(h))) return true
  }
  const rgbRe = /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/g
  let m
  while ((m = rgbRe.exec(s))) {
    if (isReddish([+m[1], +m[2], +m[3]])) return true
  }
  return false
}

// Класи з <style>, які фарбують фон у червоний
function redCssClasses(html) {
  const set = new Set()
  const re = /\.([a-z0-9_-]+)[^{]*\{[^}]*background(?:-color)?\s*:\s*([^;}]+)/gi
  let m
  while ((m = re.exec(html))) if (colorStringIsRed(m[2])) set.add(m[1].toLowerCase())
  return set
}

function cellIsOff(markup, redClasses) {
  const s = markup.toLowerCase()
  if (colorStringIsRed(s)) return true
  if (/class\s*=\s*["']?[^"'>]*\b(off|red|cherv|vidkl|danger)/.test(s)) return true
  const cls = s.match(/class\s*=\s*["']?([^"'>]+)/g) || []
  for (const c of cls) {
    for (const name of c.replace(/class\s*=\s*["']?/, "").split(/\s+/)) {
      if (redClasses.has(name)) return true
    }
  }
  return false
}

function parseSchedule(html, pid) {
  const redClasses = redCssClasses(html)

  // Кожна клітинка рядка підчерги містить посилання ...pidcherga_id=N
  const re = new RegExp(
    `<td([^>]*)>\\s*(?:<(?!\\/?td)[^>]+>\\s*)*<a[^>]*pidcherga_id=${pid}(?![0-9])[^>]*>([\\s\\S]*?)<\\/a>`,
    "gi"
  )
  const cells = []
  let m
  while ((m = re.exec(html))) {
    const inner = m[2]
    const text = inner.replace(/<[^>]+>/g, "").replace(/&nbsp;/g, " ").trim()
    if (/^\d\.\d$/.test(text)) continue // це підпис «3.1», а не часова клітинка
    const markup = m[1] + " " + inner.replace(/href\s*=\s*["'][^"']*["']/gi, "")
    cells.push({ off: cellIsOff(markup, redClasses), sig: markup.replace(/\s+/g, " ").trim() })
  }

  if (cells.length < SLOTS_PER_DAY) {
    throw new Error(`Не знайшов графік для підчерги (знайдено ${cells.length} клітинок). Можливо, сайт змінив розмітку.`)
  }

  // Якщо жодна клітинка не «червона», але клітинки різні — кольори не розпізнано
  const distinct = [...new Set(cells.map(c => c.sig))]
  if (!cells.some(c => c.off) && distinct.length > 1) {
    const err = new Error("Не вдалося розпізнати кольори клітинок. Надішліть розробнику цей текст:\n\n" +
      distinct.slice(0, 4).map(s => s.slice(0, 200)).join("\n---\n"))
    err.debug = true
    throw err
  }

  const dayCount = Math.floor(cells.length / SLOTS_PER_DAY)
  const updated = (html.match(/(\d{1,2}:\d{2})\s+(\d{2}\.\d{2}\.\d{4})/) || [])
  const allDates = [...new Set(html.match(/\b\d{2}\.\d{2}\.\d{4}\b/g) || [])]
    .sort((a, b) => dateFromStr(a) - dateFromStr(b))
  const dates = allDates.slice(-dayCount)

  const days = []
  for (let d = 0; d < dayCount; d++) {
    days.push({
      date: dates[d] || null,
      off: cells.slice(d * SLOTS_PER_DAY, (d + 1) * SLOTS_PER_DAY).map(c => c.off),
    })
  }
  return { days, updated: updated[0] || null, fetchedAt: Date.now() }
}

/* ───────────────────────── Логіка часу ───────────────────────── */

function dateFromStr(s) {
  const [d, mo, y] = s.split(".").map(Number)
  return new Date(y, mo - 1, d, 0, 0, 0)
}
const pad = n => String(n).padStart(2, "0")
const ddmmyyyy = d => `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()}`
const hhmm = d => `${pad(d.getHours())}:${pad(d.getMinutes())}`
const slotLabel = i => i >= SLOTS_PER_DAY ? "24:00" : `${pad(Math.floor(i / 2))}:${i % 2 ? "30" : "00"}`

function buildTimeline(data, now) {
  const timeline = []
  data.days.forEach((day, idx) => {
    // Якщо дату не знайшли — вважаємо, що перша таблиця на сьогодні
    const base = day.date ? dateFromStr(day.date)
      : new Date(now.getFullYear(), now.getMonth(), now.getDate() + idx)
    day.off.forEach((off, i) => {
      timeline.push({
        start: new Date(base.getFullYear(), base.getMonth(), base.getDate(), 0, i * 30),
        off,
      })
    })
  })
  return timeline
}

function computeStatus(data, now) {
  const tl = buildTimeline(data, now)
  const idx = tl.findIndex(s => s.start <= now && now < new Date(s.start.getTime() + 30 * 60000))
  if (idx === -1) return { known: false }

  const cur = tl[idx]
  let j = idx + 1
  while (j < tl.length && tl[j].off === cur.off) j++
  const change = j < tl.length ? tl[j].start : null
  const graphEnd = new Date(tl[tl.length - 1].start.getTime() + 30 * 60000)

  return { known: true, off: cur.off, change, graphEnd }
}

function todayOff(data, now) {
  const today = ddmmyyyy(now)
  const day = data.days.find(d => d.date === today) || (data.days.length === 1 ? data.days[0] : null)
  return day ? day.off : null
}

function tomorrowOff(data, now) {
  const t = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1)
  const day = data.days.find(d => d.date === ddmmyyyy(t))
  return day ? day.off : null
}

function intervals(off) {
  const res = []
  let s = null
  off.forEach((o, i) => {
    if (o && s === null) s = i
    if (!o && s !== null) { res.push([s, i]); s = null }
  })
  if (s !== null) res.push([s, off.length])
  return res.map(([a, b]) => `${slotLabel(a)}–${slotLabel(b)}`)
}

/* ───────────────────────── Кеш ───────────────────────── */

function cachePath() {
  const fm = FileManager.local()
  return fm.joinPath(fm.documentsDirectory(), CACHE_FILE)
}
function saveCache(key, data) {
  try {
    const fm = FileManager.local()
    let all = {}
    if (fm.fileExists(cachePath())) all = JSON.parse(fm.readString(cachePath()))
    all[key] = data
    fm.writeString(cachePath(), JSON.stringify(all))
  } catch (e) { /* кеш — не критично */ }
}
function loadCache(key) {
  try {
    const fm = FileManager.local()
    if (!fm.fileExists(cachePath())) return null
    return JSON.parse(fm.readString(cachePath()))[key] || null
  } catch (e) { return null }
}

/* ───────────────────────── Інтерфейс ───────────────────────── */

function palette() {
  return {
    bgTop: new Color("#1c2230"),
    bgBottom: new Color("#0f1218"),
    text: Color.white(),
    dim: new Color("#9aa3b2"),
    on: new Color("#34c759"),
    off: new Color("#ff453a"),
    barOn: new Color("#2f5a3c"),
    barOff: new Color("#ff453a"),
  }
}

function drawBar(off, now, width, C, withLabels = true) {
  const barH = 14, labelH = withLabels ? 13 : 0
  const ctx = new DrawContext()
  ctx.size = new Size(width, barH + labelH)
  ctx.opaque = false
  ctx.respectScreenScale = true

  const seg = width / off.length
  off.forEach((o, i) => {
    ctx.setFillColor(o ? C.barOff : C.barOn)
    const path = new Path()
    path.addRoundedRect(new Rect(i * seg + 0.6, 0, Math.max(seg - 1.2, 1), barH), 1.5, 1.5)
    ctx.addPath(path)
    ctx.fillPath()
  })

  if (now) {
    const x = ((now.getHours() * 60 + now.getMinutes()) / 1440) * width
    ctx.setFillColor(Color.white())
    ctx.fillRect(new Rect(Math.min(Math.max(x - 1, 0), width - 2), -2, 2, barH + 4))
  }

  if (withLabels) {
    ctx.setFont(Font.mediumSystemFont(9))
    ctx.setTextColor(C.dim)
    for (const h of [0, 6, 12, 18, 24]) {
      const x = (h / 24) * width
      if (h === 0) { ctx.setTextAlignedLeft(); ctx.drawTextInRect("0", new Rect(0, barH + 1, 20, 12)) }
      else if (h === 24) { ctx.setTextAlignedRight(); ctx.drawTextInRect("24", new Rect(width - 20, barH + 1, 20, 12)) }
      else { ctx.setTextAlignedCenter(); ctx.drawTextInRect(String(h), new Rect(x - 10, barH + 1, 20, 12)) }
    }
  }
  return ctx.getImage()
}

function statusTexts(st) {
  if (!st.known) return { title: "Немає графіка", line: "на поточний час", color: null }
  if (st.off) {
    return {
      title: "Світла немає",
      line: st.change ? `Увімкнуть о ${hhmm(st.change)}` : `Без світла до ${hhmm(st.graphEnd)}`,
      short: st.change ? `увімк. ${hhmm(st.change)}` : "без світла",
      color: "off",
    }
  }
  return {
    title: "Світло є",
    line: st.change ? `Вимкнуть о ${hhmm(st.change)}` : "Відключень не заплановано",
    short: st.change ? `вимк. ${hhmm(st.change)}` : "без відключень",
    color: "on",
  }
}

function addTimer(stack, date, size, color) {
  const t = stack.addDate(date)
  t.applyTimerStyle()
  t.font = Font.mediumMonospacedSystemFont(size)
  t.textColor = color
  t.lineLimit = 1
}

function buildHomeWidget(family, queue, data, st, now, offline, C) {
  const w = new ListWidget()
  w.url = SOURCE_URL
  const g = new LinearGradient()
  g.colors = [C.bgTop, C.bgBottom]
  g.locations = [0, 1]
  w.backgroundGradient = g
  w.setPadding(14, 14, 12, 14)

  const tx = statusTexts(st)
  const accent = tx.color === "off" ? C.off : tx.color === "on" ? C.on : C.dim

  // Шапка
  const head = w.addStack()
  head.centerAlignContent()
  const icon = head.addImage(SFSymbol.named(st.off ? "bolt.slash.fill" : "bolt.fill").image)
  icon.imageSize = new Size(13, 13)
  icon.tintColor = accent
  head.addSpacer(4)
  const q = head.addText(`Черга ${queue.label}`)
  q.font = Font.semiboldSystemFont(12)
  q.textColor = C.dim
  head.addSpacer()
  if (offline) {
    const o = head.addText("офлайн")
    o.font = Font.mediumSystemFont(10)
    o.textColor = C.dim
  }

  w.addSpacer(family === "small" ? 6 : 4)

  const body = w.addStack()
  body.layoutHorizontally()
  const left = body.addStack()
  left.layoutVertically()

  const title = left.addText(tx.title)
  title.font = Font.boldSystemFont(family === "small" ? 22 : 24)
  title.textColor = accent
  title.minimumScaleFactor = 0.7
  title.lineLimit = 1

  const line = left.addText(tx.line)
  line.font = Font.mediumSystemFont(13)
  line.textColor = C.text
  line.minimumScaleFactor = 0.8
  line.lineLimit = 1

  if (st.known && st.change) {
    const tr = left.addStack()
    tr.centerAlignContent()
    const lbl = tr.addText("через ")
    lbl.font = Font.systemFont(12)
    lbl.textColor = C.dim
    addTimer(tr, st.change, 12, C.dim)
  }

  // Середній віджет: список відключень сьогодні / завтра
  if (family === "medium") {
    body.addSpacer()
    const right = body.addStack()
    right.layoutVertically()
    const tOff = todayOff(data, now)
    const tmOff = tomorrowOff(data, now)
    const block = (label, off) => {
      const h = right.addText(label)
      h.font = Font.semiboldSystemFont(10)
      h.textColor = C.dim
      const list = off ? intervals(off) : null
      const txt = !off ? "немає даних" : list.length ? list.slice(0, 3).join("\n") + (list.length > 3 ? "\n…" : "") : "без відключень"
      const t = right.addText(txt)
      t.font = Font.mediumMonospacedSystemFont(11)
      t.textColor = C.text
      t.rightAlignText()
    }
    block("СЬОГОДНІ", tOff)
    if (tmOff) { right.addSpacer(4); block("ЗАВТРА", tmOff) }
  }

  w.addSpacer()

  const tOff = todayOff(data, now)
  if (tOff) {
    const width = family === "small" ? 130 : 290
    const img = w.addImage(drawBar(tOff, now, width, C))
    img.imageSize = new Size(width, 27)
    img.centerAlignImage()
  }

  w.addSpacer(4)
  const foot = w.addText(data.updated ? `Оновлено ${data.updated.split(" ")[0]} · ztoe.com.ua` : "ztoe.com.ua")
  foot.font = Font.systemFont(9)
  foot.textColor = C.dim
  foot.lineLimit = 1
  foot.minimumScaleFactor = 0.8

  return w
}

function buildAccessoryWidget(family, queue, st) {
  const w = new ListWidget()
  w.url = SOURCE_URL
  const tx = statusTexts(st)

  if (family === "accessoryInline") {
    const t = w.addText(`${st.off ? "⚫" : "⚡"} ${queue.label}: ${tx.short || tx.title}`)
    return w
  }

  if (family === "accessoryCircular") {
    w.addAccessoryWidgetBackground = true
    const s = w.addStack()
    s.layoutVertically()
    s.centerAlignContent()
    const icon = s.addImage(SFSymbol.named(st.off ? "bolt.slash.fill" : "bolt.fill").image)
    icon.imageSize = new Size(14, 14)
    icon.centerAlignImage()
    const t = s.addText(st.change ? hhmm(st.change) : "—")
    t.font = Font.semiboldSystemFont(13)
    t.centerAlignText()
    t.minimumScaleFactor = 0.6
    return w
  }

  // accessoryRectangular
  const head = w.addText(`⚡ Черга ${queue.label}`)
  head.font = Font.semiboldSystemFont(11)
  const title = w.addText(tx.title)
  title.font = Font.boldSystemFont(15)
  const line = w.addText(tx.line)
  line.font = Font.systemFont(12)
  line.minimumScaleFactor = 0.8
  return w
}

function buildErrorWidget(message, C) {
  const w = new ListWidget()
  w.url = SOURCE_URL
  w.backgroundColor = C.bgBottom
  const t = w.addText("⚡ Світло · Житомир")
  t.font = Font.semiboldSystemFont(12)
  t.textColor = C.dim
  w.addSpacer(6)
  const m = w.addText(message)
  m.font = Font.systemFont(12)
  m.textColor = C.text
  m.minimumScaleFactor = 0.7
  return w
}

/* ───────────────────────── Запуск ───────────────────────── */

async function fetchHtml() {
  const req = new Request(SOURCE_URL)
  req.timeoutInterval = 20
  req.headers = { "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)" }
  const data = await req.load()
  return base64ToAscii(data.toBase64String())
}

async function main() {
  const C = palette()
  const family = config.widgetFamily || "medium"
  const now = new Date()
  let widget

  try {
    const queue = parseQueue(args.widgetParameter || DEFAULT_QUEUE)
    let data, offline = false, fetchError = null
    try {
      data = parseSchedule(await fetchHtml(), queue.pid)
      saveCache(queue.label, data)
    } catch (e) {
      fetchError = e
      data = loadCache(queue.label)
      offline = true
      if (!data) throw e
    }

    const st = computeStatus(data, now)
    widget = family.startsWith("accessory")
      ? buildAccessoryWidget(family, queue, st)
      : buildHomeWidget(family, queue, data, st, now, offline, C)

    // Оновлення: у момент наступної зміни або щонайменше раз на 15 хв
    const in15 = new Date(now.getTime() + 15 * 60000)
    widget.refreshAfterDate = st.known && st.change && st.change < in15 ? st.change : in15

    if (config.runsInApp && fetchError && fetchError.debug) {
      const a = new Alert(); a.title = "Діагностика"; a.message = fetchError.message; a.addAction("OK"); await a.present()
    }
  } catch (e) {
    widget = buildErrorWidget(e.message, C)
    if (config.runsInApp) {
      const a = new Alert(); a.title = "Помилка"; a.message = e.message; a.addAction("OK"); await a.present()
    }
  }

  if (config.runsInWidget) {
    Script.setWidget(widget)
  } else {
    await widget.presentMedium()
  }
  Script.complete()
}

if (typeof Script !== "undefined") {
  main()
} else if (typeof module !== "undefined") {
  module.exports = { parseSchedule, parseQueue, computeStatus, intervals, base64ToAscii, todayOff }
}
