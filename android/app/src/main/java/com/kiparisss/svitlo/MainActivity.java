package com.kiparisss.svitlo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Головний екран: вибір підчерги та поточний графік. */
public class MainActivity extends Activity {
    private static final int BG = 0xFF06050A;
    private static final int CARD = 0xFF110E1B;
    private static final int LINE = 0xFF2B2440;
    private static final int INK = 0xFFF4F1FF;
    private static final int DIM = 0xFF9B93B8;
    private static final int VIOLET = 0xFF8B5CF6;
    private static final int VIOLET2 = 0xFFC084FC;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<TextView> queueButtons = new ArrayList<>();
    private TextView statusTv, lineTv, todayTv, tomorrowTv, updatedTv, errorTv;
    private ImageView barIv;
    private LinearLayout card;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable rounded(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private void add(LinearLayout parent, View v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        parent.addView(v, lp);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(36));
        scroll.addView(root);

        TextView brand = text("СВІТЛО · ЖИТОМИР", 12, VIOLET2, true);
        brand.setLetterSpacing(0.15f);
        add(root, brand, 0);
        add(root, text("Коли буде світло?", 34, INK, true), 8);
        add(root, text("Оберіть свою підчергу. Віджет на головному екрані покаже, чи є світло і коли його увімкнуть.", 15, DIM, false), 8);

        TextView label = text("ВАША ПІДЧЕРГА", 12, DIM, true);
        label.setLetterSpacing(0.12f);
        add(root, label, 28);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        for (int q = 1; q <= 6; q++) {
            for (int s = 1; s <= 2; s++) {
                final String name = q + "." + s;
                TextView b = text(name, 17, INK, true);
                b.setGravity(Gravity.CENTER);
                b.setOnClickListener(v -> select(name));
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
                lp.width = 0;
                lp.height = dp(48);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                grid.addView(b, lp);
                queueButtons.add(b);
            }
        }
        add(root, grid, 8);

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(rounded(CARD, LINE, 24));
        statusTv = text("", 30, INK, true);
        lineTv = text("", 17, INK, true);
        todayTv = text("", 14, INK, false);
        todayTv.setTypeface(Typeface.MONOSPACE);
        tomorrowTv = text("", 14, INK, false);
        tomorrowTv.setTypeface(Typeface.MONOSPACE);
        barIv = new ImageView(this);
        barIv.setAdjustViewBounds(true);
        updatedTv = text("", 12, DIM, false);
        errorTv = text("", 13, 0xFFFB4D6D, false);
        add(card, statusTv, 0);
        add(card, lineTv, 2);
        add(card, barIv, 16);
        add(card, todayTv, 14);
        add(card, tomorrowTv, 8);
        add(card, updatedTv, 14);
        add(card, errorTv, 6);
        add(root, card, 24);

        TextView refresh = text("Оновити", 16, 0xFFFFFFFF, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setPadding(dp(16), dp(14), dp(16), dp(14));
        refresh.setBackground(rounded(VIOLET, 0, 28));
        refresh.setOnClickListener(v -> refresh());
        add(root, refresh, 20);

        TextView find = text("Знайти свою підчергу за адресою", 15, VIOLET2, true);
        find.setGravity(Gravity.CENTER);
        find.setPadding(dp(16), dp(13), dp(16), dp(13));
        find.setBackground(rounded(BG, 0x66C084FC, 28));
        find.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Schedule.SOURCE_URL))));
        add(root, find, 10);

        TextView hint = text("Як додати віджет: затисніть порожнє місце на головному екрані → Віджети → «Світло Житомир».", 14, DIM, false);
        hint.setPadding(dp(16), dp(14), dp(16), dp(14));
        hint.setBackground(rounded(0x1A8B5CF6, 0x4DC084FC, 18));
        add(root, hint, 24);

        TextView foot = text("Дані з сайту АТ «Житомиробленерго». Застосунок неофіційний і показує лише планові графіки.", 12, DIM, false);
        add(root, foot, 20);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(null);
        refresh();
    }

    private void select(String q) {
        Schedule.setQueue(this, q);
        render(null);
        refresh();
    }

    private void refresh() {
        final String q = Schedule.getQueue(this);
        if (q == null) return;
        updatedTv.setText("Оновлення…");
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            String err = null;
            try {
                Schedule.refresh(app, q);
            } catch (Exception e) {
                err = "Не вдалося оновити: " + e.getMessage();
            }
            final String fe = err;
            ui.post(() -> {
                render(fe);
                SvitloWidget.updateAll(app);
            });
        }).start();
    }

    private void render(String error) {
        String q = Schedule.getQueue(this);
        for (TextView b : queueButtons) {
            boolean sel = b.getText().toString().equals(q);
            b.setBackground(rounded(sel ? VIOLET : CARD, sel ? 0 : LINE, 16));
            b.setTextColor(sel ? 0xFFFFFFFF : INK);
        }
        errorTv.setText(error == null ? "" : error);
        errorTv.setVisibility(error == null ? View.GONE : View.VISIBLE);
        if (q == null) {
            statusTv.setText("Оберіть підчергу");
            statusTv.setTextColor(DIM);
            lineTv.setText("Натисніть на свою підчергу вище");
            barIv.setVisibility(View.GONE);
            todayTv.setText("");
            tomorrowTv.setText("");
            updatedTv.setText("");
            return;
        }
        Parser.Data data = Schedule.load(this, q);
        if (data == null) {
            statusTv.setText("Черга " + q);
            statusTv.setTextColor(INK);
            lineTv.setText("Завантаження графіка…");
            barIv.setVisibility(View.GONE);
            todayTv.setText("");
            tomorrowTv.setText("");
            return;
        }
        long now = System.currentTimeMillis();
        Parser.Status st = Parser.status(data, now);
        Object[] t = Schedule.texts(st);
        statusTv.setText((String) t[0]);
        statusTv.setTextColor((Integer) t[2]);
        lineTv.setText((String) t[1]);

        boolean[] today = Parser.offFor(data, now, 0);
        if (today != null) {
            barIv.setVisibility(View.VISIBLE);
            barIv.setImageBitmap(Schedule.bar(today, now, 900, true));
            todayTv.setText("Сьогодні:  " + join(Parser.intervals(today)));
        } else {
            barIv.setVisibility(View.GONE);
            todayTv.setText("Сьогодні:  немає даних");
        }
        boolean[] tomorrow = Parser.offFor(data, now, 1);
        tomorrowTv.setText(tomorrow != null ? "Завтра:    " + join(Parser.intervals(tomorrow)) : "Завтра:    графіка ще немає");
        String upd = data.updated != null ? data.updated : "";
        updatedTv.setText("Черга " + q + " · оновлено " + upd + (Schedule.isOffline(this, q) ? " · офлайн" : ""));
    }

    private static String join(List<String> list) {
        if (list.isEmpty()) return "без відключень";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
