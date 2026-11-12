"""Оновлює index.html і svitlo-zhytomyr.js у корені репозиторію: розділ Android і виправлення розбору."""
import pathlib

js = pathlib.Path('svitlo-zhytomyr.js')
s = js.read_text(encoding='utf-8')
if 'function colorSig' not in s:
    s = s.replace('    cells.push({ off: cellIsOff(markup, redClasses), sig: markup.replace(/\\s+/g, " ").trim() })',
                  '    cells.push({ off: cellIsOff(markup, redClasses), sig: colorSig(markup) })')
    s = s.replace('function parseSchedule(html, pid) {', '''// Ознака кольору клітинки: фон, bgcolor або клас (товщина рамок не враховується)
function colorSig(markup) {
  const s = markup.toLowerCase()
  let m = s.match(/background(?:-color)?\\s*:\\s*([^;"']+)/)
  if (m) return "bg:" + m[1].trim()
  m = s.match(/bgcolor\\s*=\\s*["']?([^"'\\s>]+)/)
  if (m) return "bgcolor:" + m[1]
  m = s.match(/class\\s*=\\s*["']?([^"'>]+)/)
  if (m) return "class:" + m[1].trim()
  return s.replace(/\\s+/g, " ").trim()
}

function parseSchedule(html, pid) {''')
    js.write_text(s, encoding='utf-8')
lines = s.count('\n') + 1

p = pathlib.Path('index.html')
s = p.read_text(encoding='utf-8')
if 'id="android"' not in s:
    s = s.replace('Віджет для iPhone з графіком погодинних відключень <b>Житомирщини</b>.', 'Віджет для iPhone та Android з графіком погодинних відключень <b>Житомирщини</b>.')
    s = s.replace('content="Віджет для iPhone з графіком погодинних відключень Житомирщини"', 'content="Віджет для iPhone та Android з графіком погодинних відключень Житомирщини"')
    s = s.replace('content="Віджет для iPhone: коли буде світло. Налаштування за 2 хвилини."', 'content="Віджет для iPhone та Android: коли буде світло. Налаштування за 2 хвилини."')
    s = s.replace('521 рядків', f'{lines} рядків')
    s = s.replace('<div class="meta">Безкоштовно', '<div class="jump"><a href="#iphone">iPhone</a><a href="#android">Android</a></div>\n    <div class="meta">Безкоштовно')
    s = s.replace('<section>\n    <h2>Налаштування за 2 хвилини</h2>', '<section id="iphone">\n    <h2>iPhone: налаштування за 2 хвилини</h2>')
    android = '''  <section id="android">
    <h2>Android: окремий застосунок</h2>
    <div class="cta" style="margin-bottom:20px">
      <a class="btn" href="svitlo.apk" download>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 4v11m0 0-4-4m4 4 4-4M5 20h14"/></svg>
        <span>Завантажити для Android</span>
      </a>
    </div>
    <ol class="steps">
      <li><div><b>Завантаж і встанови</b><p>Натисни кнопку вище й відкрий файл <code>svitlo.apk</code>. Android попросить дозволити встановлення з цього джерела, погодься.</p></div></li>
      <li><div><b>Обери підчергу</b><p>Відкрий застосунок «Світло Житомир» і натисни свою підчергу.</p></div></li>
      <li><div><b>Додай віджет</b><p>Затисни порожнє місце на головному екрані → Віджети → «Світло Житомир».</p></div></li>
    </ol>
  </section>

  <div class="note"><b>Не знаєш'''
    s = s.replace('  <div class="note"><b>Не знаєш', android, 1)
    s = s.replace('  .meta{font-size:14px;color:var(--dim)}', '  .meta{font-size:14px;color:var(--dim)}\n  .jump{display:flex;gap:8px}\n  .jump a{font-size:14px;font-weight:600;color:var(--ink);text-decoration:none;padding:8px 14px;border-radius:999px;background:var(--surface2);border:1px solid var(--line)}\n  a.btn{text-decoration:none}\n  section{scroll-margin-top:24px}')
    p.write_text(s, encoding='utf-8')
print('site patched')
