package com.kiparisss.svitlo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Розбір сторінки графіків Житомиробленерго. Чиста Java, без Android. */
public final class Parser {
    public static final int SLOTS = 48;
    public static final TimeZone KYIV = TimeZone.getTimeZone("Europe/Kiev");

    public static final class Day {
        public String date;
        public boolean[] off;
    }

    public static final class Data {
        public final List<Day> days = new ArrayList<>();
        public String updated;
        public long fetchedAt;
    }

    public static final class Status {
        public boolean known;
        public boolean off;
        public long change = -1;   // -1: змін до кінця графіка немає
        public long graphEnd;
    }

    private Parser() {}

    public static int pidOf(String queue) {
        String[] p = queue.split("\\.");
        return (Integer.parseInt(p[0]) - 1) * 2 + Integer.parseInt(p[1]);
    }

    static boolean isReddish(int r, int g, int b) {
        return r >= 170 && g <= 110 && b <= 110;
    }

    static boolean colorIsRed(String s) {
        s = s.toLowerCase(Locale.ROOT);
        if (Pattern.compile("\\b(red|crimson|tomato|firebrick|darkred|orangered)\\b").matcher(s).find()) return true;
        Matcher m = Pattern.compile("#([0-9a-f]{6}|[0-9a-f]{3})\\b").matcher(s);
        while (m.find()) {
            String h = m.group(1);
            if (h.length() == 3) {
                h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            }
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            if (isReddish(r, g, b)) return true;
        }
        m = Pattern.compile("rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})").matcher(s);
        while (m.find()) {
            if (isReddish(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))) return true;
        }
        return false;
    }

    /** CSS-класи з блоків style, що фарбують фон у червоний. */
    static Set<String> redClasses(String html) {
        Set<String> set = new HashSet<>();
        Matcher style = Pattern.compile("<style[^>]*>([\\s\\S]*?)</style>", Pattern.CASE_INSENSITIVE).matcher(html);
        while (style.find()) {
            Matcher m = Pattern.compile("\\.([a-zA-Z0-9_-]+)[^{}]*\\{[^}]*background(?:-color)?\\s*:\\s*([^;}]+)").matcher(style.group(1));
            while (m.find()) {
                if (colorIsRed(m.group(2))) set.add(m.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    static boolean cellIsOff(String markup, Set<String> redClasses) {
        String s = markup.toLowerCase(Locale.ROOT);
        if (colorIsRed(s)) return true;
        Matcher m = Pattern.compile("class\\s*=\\s*[\"']?([^\"'>]+)").matcher(s);
        while (m.find()) {
            for (String name : m.group(1).trim().split("\\s+")) {
                if (redClasses.contains(name)) return true;
                if (name.matches(".*\\b(off|red|cherv|vidkl|danger).*")) return true;
            }
        }
        return false;
    }

    /** Ознака кольору клітинки: фон, bgcolor або клас (рамки не враховуються). */
    static String colorSig(String markup) {
        String s = markup.toLowerCase(Locale.ROOT);
        Matcher m = Pattern.compile("background(?:-color)?\\s*:\\s*([^;\"']+)").matcher(s);
        if (m.find()) return "bg:" + m.group(1).trim();
        m = Pattern.compile("bgcolor\\s*=\\s*[\"']?([^\"'\\s>]+)").matcher(s);
        if (m.find()) return "bgcolor:" + m.group(1);
        m = Pattern.compile("class\\s*=\\s*[\"']?([^\"'>]+)").matcher(s);
        if (m.find()) return "class:" + m.group(1).trim();
        return s.replaceAll("\\s+", " ").trim();
    }

    public static Data parse(String html, int pid) throws Exception {
        Set<String> redClasses = redClasses(html);
        Pattern cellRe = Pattern.compile(
                "<td([^>]*)>\\s*(?:<(?!/?td)[^>]+>\\s*)*<a[^>]*pidcherga_id=" + pid + "(?![0-9])[^>]*>([\\s\\S]*?)</a>",
                Pattern.CASE_INSENSITIVE);
        Matcher m = cellRe.matcher(html);
        List<Boolean> cells = new ArrayList<>();
        Set<String> sigs = new LinkedHashSet<>();
        while (m.find()) {
            String inner = m.group(2);
            String text = inner.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
            if (text.matches("\\d\\.\\d")) continue; // підпис «3.1», а не часова клітинка
            String markup = m.group(1) + " " + inner.replaceAll("(?i)href\\s*=\\s*[\"'][^\"']*[\"']", "");
            cells.add(cellIsOff(markup, redClasses));
            sigs.add(colorSig(markup));
        }
        if (cells.size() < SLOTS) {
            throw new Exception("Не знайшов графік для підчерги (" + cells.size() + " клітинок). Можливо, сайт змінив розмітку.");
        }
        boolean anyOff = false;
        for (Boolean b : cells) anyOff |= b;
        if (!anyOff && sigs.size() > 1) {
            throw new Exception("Не вдалося розпізнати кольори графіка: " + sigs);
        }

        Data data = new Data();
        data.fetchedAt = System.currentTimeMillis();
        Matcher um = Pattern.compile("(\\d{1,2}:\\d{2})\\s+(\\d{2}\\.\\d{2}\\.\\d{4})").matcher(html);
        data.updated = um.find() ? um.group(0) : null;

        TreeMap<Long, String> dates = new TreeMap<>();
        Matcher dm = Pattern.compile("\\b\\d{2}\\.\\d{2}\\.\\d{4}\\b").matcher(html);
        while (dm.find()) {
            try { dates.put(dayStart(dm.group()), dm.group()); } catch (Exception ignored) { }
        }
        List<String> all = new ArrayList<>(dates.values());
        int dayCount = cells.size() / SLOTS;
        List<String> last = all.size() >= dayCount ? all.subList(all.size() - dayCount, all.size()) : all;

        for (int d = 0; d < dayCount; d++) {
            Day day = new Day();
            day.date = d < last.size() ? last.get(d) : null;
            day.off = new boolean[SLOTS];
            for (int i = 0; i < SLOTS; i++) day.off[i] = cells.get(d * SLOTS + i);
            data.days.add(day);
        }
        return data;
    }

    /* ───────── час ───────── */

    public static long dayStart(String date) {
        String[] p = date.split("\\.");
        Calendar c = Calendar.getInstance(KYIV);
        c.clear();
        c.set(Integer.parseInt(p[2]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[0]), 0, 0, 0);
        return c.getTimeInMillis();
    }

    static long slotStart(long dayStart, int i) {
        Calendar c = Calendar.getInstance(KYIV);
        c.setTimeInMillis(dayStart);
        c.add(Calendar.MINUTE, i * 30);
        return c.getTimeInMillis();
    }

    public static String dateStr(long t, int addDays) {
        Calendar c = Calendar.getInstance(KYIV);
        c.setTimeInMillis(t);
        c.add(Calendar.DAY_OF_MONTH, addDays);
        return String.format(Locale.ROOT, "%02d.%02d.%04d",
                c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
    }

    public static String hhmm(long t) {
        Calendar c = Calendar.getInstance(KYIV);
        c.setTimeInMillis(t);
        return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    /** Хвилини від початку доби за Києвом (для позначки «зараз» на смужці). */
    public static int minutesOfDay(long t) {
        Calendar c = Calendar.getInstance(KYIV);
        c.setTimeInMillis(t);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    public static Status status(Data data, long now) {
        List<long[]> tl = new ArrayList<>(); // {start, off}
        for (int d = 0; d < data.days.size(); d++) {
            Day day = data.days.get(d);
            long base = day.date != null ? dayStart(day.date) : dayStart(dateStr(now, d));
            for (int i = 0; i < SLOTS; i++) tl.add(new long[]{slotStart(base, i), day.off[i] ? 1 : 0});
        }
        Status st = new Status();
        int idx = -1;
        for (int k = 0; k < tl.size(); k++) {
            long start = tl.get(k)[0];
            long end = k + 1 < tl.size() ? tl.get(k + 1)[0] : slotStart(start, 1);
            if (start <= now && now < end) { idx = k; break; }
        }
        if (idx < 0) return st;
        st.known = true;
        st.off = tl.get(idx)[1] == 1;
        int j = idx + 1;
        while (j < tl.size() && (tl.get(j)[1] == 1) == st.off) j++;
        st.change = j < tl.size() ? tl.get(j)[0] : -1;
        st.graphEnd = slotStart(tl.get(tl.size() - 1)[0], 1);
        return st;
    }

    public static boolean[] offFor(Data data, long now, int addDays) {
        String want = dateStr(now, addDays);
        for (Day d : data.days) if (want.equals(d.date)) return d.off;
        if (addDays == 0 && data.days.size() == 1 && data.days.get(0).date == null) return data.days.get(0).off;
        return null;
    }

    static String slotLabel(int i) {
        if (i >= SLOTS) return "24:00";
        return String.format(Locale.ROOT, "%02d:%s", i / 2, i % 2 == 1 ? "30" : "00");
    }

    public static List<String> intervals(boolean[] off) {
        List<String> res = new ArrayList<>();
        int s = -1;
        for (int i = 0; i < off.length; i++) {
            if (off[i] && s < 0) s = i;
            if (!off[i] && s >= 0) { res.add(slotLabel(s) + "–" + slotLabel(i)); s = -1; }
        }
        if (s >= 0) res.add(slotLabel(s) + "–" + slotLabel(off.length));
        return res;
    }

    /* ───────── кеш у вигляді рядка ───────── */

    public static String serialize(Data d) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.updated == null ? "" : d.updated).append('\n').append(d.fetchedAt).append('\n');
        for (Day day : d.days) {
            sb.append(day.date == null ? "-" : day.date).append('|');
            for (boolean b : day.off) sb.append(b ? '1' : '0');
            sb.append('\n');
        }
        return sb.toString();
    }

    public static Data deserialize(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] lines = s.split("\n");
            Data d = new Data();
            d.updated = lines[0].isEmpty() ? null : lines[0];
            d.fetchedAt = Long.parseLong(lines[1]);
            for (int k = 2; k < lines.length; k++) {
                String[] p = lines[k].split("\\|");
                if (p.length != 2 || p[1].length() != SLOTS) continue;
                Day day = new Day();
                day.date = "-".equals(p[0]) ? null : p[0];
                day.off = new boolean[SLOTS];
                for (int i = 0; i < SLOTS; i++) day.off[i] = p[1].charAt(i) == '1';
                d.days.add(day);
            }
            return d.days.isEmpty() ? null : d;
        } catch (Exception e) {
            return null;
        }
    }
}
