package com.kiparisss.svitlo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/** Віджет головного екрана. */
public class SvitloWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.kiparisss.svitlo.REFRESH";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        refreshAsync(ctx, goAsync());
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            refreshAsync(ctx, goAsync());
        }
    }

    private static void refreshAsync(final Context ctx, final PendingResult result) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                String q = Schedule.getQueue(app);
                if (q != null) {
                    try { Schedule.refresh(app, q); } catch (Exception ignored) { }
                }
                updateAll(app);
            } finally {
                result.finish();
            }
        }).start();
    }

    /** Перемальовує всі віджети з кешу й планує наступне оновлення. */
    public static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, SvitloWidget.class));
        if (ids == null || ids.length == 0) return;
        long next = build(ctx, mgr, ids);
        scheduleNext(ctx, next);
    }

    /** Повертає час наступної зміни стану (або -1). */
    private static long build(Context ctx, AppWidgetManager mgr, int[] ids) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        Intent open = new Intent(ctx, MainActivity.class);
        rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        long now = System.currentTimeMillis();
        String q = Schedule.getQueue(ctx);
        Parser.Data data = q == null ? null : Schedule.load(ctx, q);
        long change = -1;

        rv.setViewVisibility(R.id.offline, View.GONE);
        rv.setViewVisibility(R.id.timerRow, View.GONE);
        rv.setViewVisibility(R.id.bar, View.GONE);
        rv.setTextViewText(R.id.today, "");

        if (q == null) {
            rv.setTextViewText(R.id.queue, "Світло · Житомир");
            rv.setTextViewText(R.id.status, "Оберіть підчергу");
            rv.setTextColor(R.id.status, Schedule.COLOR_DIM);
            rv.setTextViewText(R.id.line, "Натисніть, щоб налаштувати");
            rv.setTextViewText(R.id.updated, "");
        } else if (data == null) {
            rv.setTextViewText(R.id.queue, "Черга " + q);
            rv.setTextViewText(R.id.status, "Немає даних");
            rv.setTextColor(R.id.status, Schedule.COLOR_DIM);
            rv.setTextViewText(R.id.line, "Перевірте інтернет");
            rv.setTextViewText(R.id.updated, "ztoe.com.ua");
        } else {
            Parser.Status st = Parser.status(data, now);
            Object[] t = Schedule.texts(st);
            rv.setTextViewText(R.id.queue, "Черга " + q);
            rv.setTextViewText(R.id.status, (String) t[0]);
            rv.setTextColor(R.id.status, (Integer) t[2]);
            rv.setTextViewText(R.id.line, (String) t[1]);
            if (Schedule.isOffline(ctx, q)) rv.setViewVisibility(R.id.offline, View.VISIBLE);

            if (st.known && st.change >= 0) {
                change = st.change;
                rv.setViewVisibility(R.id.timerRow, View.VISIBLE);
                long base = SystemClock.elapsedRealtime() + (st.change - now);
                rv.setChronometer(R.id.timer, base, null, true);
                rv.setChronometerCountDown(R.id.timer, true);
            }

            boolean[] today = Parser.offFor(data, now, 0);
            if (today != null) {
                rv.setViewVisibility(R.id.bar, View.VISIBLE);
                rv.setImageViewBitmap(R.id.bar, Schedule.bar(today, now, 600, true));
                List<String> list = Parser.intervals(today);
                StringBuilder sb = new StringBuilder("СЬОГОДНІ");
                if (list.isEmpty()) sb.append("\nбез відключень");
                for (int i = 0; i < Math.min(3, list.size()); i++) sb.append('\n').append(list.get(i));
                if (list.size() > 3) sb.append("\n…");
                rv.setTextViewText(R.id.today, sb.toString());
            }
            String upd = data.updated != null ? data.updated.split(" ")[0] : null;
            rv.setTextViewText(R.id.updated, upd != null ? "Оновлено " + upd + " · ztoe.com.ua" : "ztoe.com.ua");
        }
        mgr.updateAppWidget(ids, rv);
        return change;
    }

    private static void scheduleNext(Context ctx, long change) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(ctx, SvitloWidget.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long now = System.currentTimeMillis();
        long when = now + 30 * 60 * 1000L;
        if (change > now && change + 5000 < when) when = change + 5000;
        am.set(AlarmManager.RTC, when, pi);
    }
}
