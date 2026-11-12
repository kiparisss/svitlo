package com.kiparisss.svitlo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/** Завантаження, кеш і спільні для застосунку та віджета речі. */
public final class Schedule {
    public static final String SOURCE_URL = "https://www.ztoe.com.ua/unhooking-search.php";
    private static final String PREFS = "svitlo";

    public static final int COLOR_ON = 0xFF4ADE80;
    public static final int COLOR_OFF = 0xFFFB4D6D;
    public static final int COLOR_DIM = 0xFF9B93B8;

    private Schedule() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getQueue(Context c) {
        return prefs(c).getString("queue", null);
    }

    public static void setQueue(Context c, String q) {
        prefs(c).edit().putString("queue", q).apply();
    }

    public static Parser.Data load(Context c, String q) {
        return Parser.deserialize(prefs(c).getString("data_" + q, null));
    }

    public static boolean isOffline(Context c, String q) {
        return prefs(c).getBoolean("offline_" + q, false);
    }

    /** Завантажує графік із сайту й зберігає. Кидає виняток, якщо не вдалося. */
    public static Parser.Data refresh(Context c, String q) throws Exception {
        try {
            Parser.Data d = Parser.parse(fetchHtml(), Parser.pidOf(q));
            prefs(c).edit()
                    .putString("data_" + q, Parser.serialize(d))
                    .putBoolean("offline_" + q, false)
                    .apply();
            return d;
        } catch (Exception e) {
            prefs(c).edit().putBoolean("offline_" + q, true).apply();
            throw e;
        }
    }

    static String fetchHtml() throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(SOURCE_URL).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SvitloZhytomyr/1.0");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), Charset.forName("windows-1251"));
        } finally {
            con.disconnect();
        }
    }

    static String clock(long t) {
        String s = Parser.hhmm(t);
        return "00:00".equals(s) ? "24:00" : s;
    }

    /** [заголовок, рядок, колір] для поточного стану. */
    public static Object[] texts(Parser.Status st) {
        if (!st.known) return new Object[]{"Немає графіка", "на поточний час", COLOR_DIM};
        if (st.off) {
            String line = st.change >= 0 ? "Увімкнуть о " + clock(st.change) : "Без світла до " + clock(st.graphEnd);
            return new Object[]{"Світла немає", line, COLOR_OFF};
        }
        String line = st.change >= 0 ? "Вимкнуть о " + clock(st.change) : "Відключень не заплановано";
        return new Object[]{"Світло є", line, COLOR_ON};
    }

    /** Смужка на 24 години з позначкою поточного часу. */
    public static Bitmap bar(boolean[] off, long now, int width, boolean labels) {
        int barH = Math.round(width * 0.05f);
        int labelH = labels ? Math.round(width * 0.045f) : 0;
        Bitmap bmp = Bitmap.createBitmap(width, barH + labelH + 4, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float seg = width / (float) off.length;
        float gap = Math.max(1f, seg * 0.15f);
        for (int i = 0; i < off.length; i++) {
            p.setColor(off[i] ? COLOR_OFF : 0xFF2F5A3C);
            c.drawRoundRect(new RectF(i * seg + gap / 2, 2, (i + 1) * seg - gap / 2, barH + 2), 3, 3, p);
        }
        if (now > 0) {
            float x = Parser.minutesOfDay(now) / 1440f * width;
            p.setColor(0xFFFFFFFF);
            float w = Math.max(3f, width * 0.006f);
            c.drawRect(Math.max(0, x - w / 2), 0, Math.min(width, x + w / 2), barH + 4, p);
        }
        if (labels) {
            p.setColor(COLOR_DIM);
            p.setTextSize(labelH * 0.85f);
            p.setTypeface(Typeface.DEFAULT);
            float y = barH + 4 + labelH * 0.85f;
            String[] t = {"0", "6", "12", "18", "24"};
            for (int k = 0; k < t.length; k++) {
                float x = k * width / 4f;
                Paint.Align a = k == 0 ? Paint.Align.LEFT : k == 4 ? Paint.Align.RIGHT : Paint.Align.CENTER;
                p.setTextAlign(a);
                c.drawText(t[k], x, y, p);
            }
        }
        return bmp;
    }
}
