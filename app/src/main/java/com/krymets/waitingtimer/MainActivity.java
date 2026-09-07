package com.krymets.waitingtimer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private AppDb db;
    private LinearLayout content;
    private TextView timerText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int page = 0; // 0 home, 1 history, 2 stats
    private AppDb.Period statsPeriod = AppDb.Period.MONTH;
    private long renderedActiveId = -2;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (page == 0) {
                AppDb.Session active = db.getActiveSession();
                long id = active == null ? -1 : active.id;
                if (id != renderedActiveId) {
                    renderHome();
                } else if (active != null && timerText != null) {
                    timerText.setText(formatClock(AppDb.activeDuration(active)));
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            renderCurrent();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = AppDb.get(this);
        NotificationHelper.ensureChannel(this);
        buildShell();
        registerDataReceiver();
        renderHome();
        handler.post(ticker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderCurrent();
        AppDb.Session active = db.getActiveSession();
        if (active != null && NotificationHelper.canNotify(this)) {
            NotificationHelper.showOngoing(this, active);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        try { unregisterReceiver(dataReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void registerDataReceiver() {
        IntentFilter filter = new IntentFilter(WaitActionReceiver.ACTION_DATA_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dataReceiver, filter);
        }
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));

        TextView title = text("WAITING / WASTING", 18, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lpMatchWrap());

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(8), 0, dp(8));
        Button home = navButton("Главная", 0);
        Button history = navButton("История", 1);
        Button stats = navButton("Статистика", 2);
        nav.addView(home, new LinearLayout.LayoutParams(0, dp(48), 1));
        nav.addView(history, new LinearLayout.LayoutParams(0, dp(48), 1));
        nav.addView(stats, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(nav, lpMatchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private Button navButton(String label, int target) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(v -> {
            page = target;
            renderCurrent();
        });
        return b;
    }

    private void renderCurrent() {
        if (content == null) return;
        if (page == 1) renderHistory();
        else if (page == 2) renderStats();
        else renderHome();
    }

    private void renderHome() {
        page = 0;
        content.removeAllViews();
        timerText = null;
        AppDb.Session active = db.getActiveSession();
        renderedActiveId = active == null ? -1 : active.id;

        if (active == null) renderIdleHome(); else renderActiveHome(active);
    }

    private void renderIdleHome() {
        TextView idea = text("waIting time → waSting time", 22, true);
        idea.setGravity(Gravity.CENTER);
        idea.setPadding(0, dp(22), 0, dp(24));
        content.addView(idea, lpMatchWrap());

        Button start = new Button(this);
        start.setText("START");
        start.setTextSize(30);
        start.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        start.setMinHeight(dp(96));
        start.setOnClickListener(v -> startWaiting());
        content.addView(start, lpMatchWrap());

        TextView today = text("Сегодня: " + AppDb.formatDuration(db.todayTotal()), 20, true);
        today.setGravity(Gravity.CENTER);
        today.setPadding(0, dp(22), 0, dp(6));
        content.addView(today, lpMatchWrap());

        TextView month = text("Этот месяц: " + AppDb.formatDuration(db.monthTotal()), 16, false);
        month.setGravity(Gravity.CENTER);
        content.addView(month, lpMatchWrap());

        section("Последние ожидания");
        List<AppDb.Session> recent = db.getRecentCompleted(5);
        if (recent.isEmpty()) {
            TextView empty = text("Пока пусто. Первое ожидание начнётся одним нажатием START.", 15, false);
            empty.setPadding(0, dp(6), 0, dp(12));
            content.addView(empty, lpMatchWrap());
        } else {
            for (AppDb.Session s : recent) addSessionRow(s, false);
        }

        Button history = button("Открыть историю", v -> { page = 1; renderHistory(); });
        content.addView(history, lpMatchWrap());
        Button stats = button("Открыть статистику", v -> { page = 2; renderStats(); });
        content.addView(stats, lpMatchWrap());
    }

    private void renderActiveHome(AppDb.Session active) {
        TextView waiting = text("ЖДУ", 22, true);
        waiting.setGravity(Gravity.CENTER);
        waiting.setPadding(0, dp(18), 0, dp(12));
        content.addView(waiting, lpMatchWrap());

        timerText = text(formatClock(AppDb.activeDuration(active)), 50, true);
        timerText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timerText.setGravity(Gravity.CENTER);
        timerText.setPadding(0, dp(6), 0, dp(18));
        content.addView(timerText, lpMatchWrap());

        TextView q = text("Что ждём?", 16, false);
        q.setGravity(Gravity.CENTER);
        content.addView(q, lpMatchWrap());

        String objectLabel = active.objectName != null ? active.objectName :
                (active.category != null ? active.category : "+ Указать");
        Button object = button(objectLabel, v -> showAssignDialog(active.id, false));
        object.setTextSize(20);
        content.addView(object, lpMatchWrap());

        Button stop = new Button(this);
        stop.setText("ДОЖДАЛСЯ");
        stop.setTextSize(24);
        stop.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        stop.setMinHeight(dp(82));
        stop.setOnClickListener(v -> stopWaitingFromApp());
        LinearLayout.LayoutParams stopLp = lpMatchWrap();
        stopLp.topMargin = dp(18);
        content.addView(stop, stopLp);

        String startText = "Началось в " + new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(active.startedAt));
        Button edit = button(startText + " · Изменить начало", v ->
                pickDateTime(active.startedAt, value -> {
                    if (!db.updateStart(active.id, value)) {
                        toast("Начало должно быть в прошлом и раньше окончания");
                        return;
                    }
                    AppDb.Session changed = db.getActiveSession();
                    NotificationHelper.onSessionStartedOrChanged(this, changed);
                    WaitingWidgetProvider.updateAll(this);
                    renderHome();
                }));
        content.addView(edit, lpMatchWrap());

        TextView hint = text("Можно убрать телефон. Таймер продолжит считаться без открытого приложения.", 14, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(12), dp(18), dp(12), 0);
        content.addView(hint, lpMatchWrap());
    }

    private void startWaiting() {
        db.startSession();
        AppDb.Session active = db.getActiveSession();
        NotificationHelper.onSessionStartedOrChanged(this, active);
        WaitingWidgetProvider.updateAll(this);
        renderHome();
        requestNotificationPermissionIfNeeded();
    }

    private void stopWaitingFromApp() {
        AppDb.Session finished = db.stopActive();
        NotificationHelper.clearAll(this);
        WaitingWidgetProvider.updateAll(this);
        renderHome();
        if (finished != null && finished.objectName == null && finished.category == null) {
            showAssignDialog(finished.id, true);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 501);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 501 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            AppDb.Session active = db.getActiveSession();
            if (active != null) NotificationHelper.showOngoing(this, active);
        }
    }

    private void showAssignDialog(long sessionId, boolean afterStop) {
        List<AppDb.WaitingObject> recent = db.getRecentObjects(6);
        ArrayList<String> labels = new ArrayList<>();
        for (AppDb.WaitingObject o : recent) labels.add(o.name + " · " + o.category);
        labels.add("+ Новый объект");
        for (String category : AppDb.CATEGORIES) labels.add(category);
        labels.add("Не указывать");

        new AlertDialog.Builder(this)
                .setTitle(afterStop ? "Кого или что вы ждали?" : "Что ждём?")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which < recent.size()) {
                        db.assignObject(sessionId, recent.get(which).id);
                        afterMetaChange(sessionId);
                        return;
                    }
                    int index = which - recent.size();
                    if (index == 0) {
                        showCreateObjectDialog(sessionId);
                    } else if (index >= 1 && index <= AppDb.CATEGORIES.length) {
                        db.clearObject(sessionId);
                        db.setCategory(sessionId, AppDb.CATEGORIES[index - 1]);
                        afterMetaChange(sessionId);
                    } else {
                        // Explicitly leave unnamed; the session is already safely stored.
                        renderCurrent();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showCreateObjectDialog(long sessionId) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        EditText name = new EditText(this);
        name.setHint("Например, Сергей или Автобус 54");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(name, lpMatchWrap());
        Spinner categories = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, AppDb.CATEGORIES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categories.setAdapter(adapter);
        box.addView(categories, lpMatchWrap());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Новый объект")
                .setView(box)
                .setPositiveButton("Сохранить", null)
                .setNegativeButton("Отмена", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("Введите название");
                return;
            }
            long objectId = db.createObject(n, (String) categories.getSelectedItem());
            if (objectId > 0) {
                db.assignObject(sessionId, objectId);
                dialog.dismiss();
                afterMetaChange(sessionId);
            }
        }));
        dialog.show();
    }

    private void afterMetaChange(long sessionId) {
        AppDb.Session s = db.getSession(sessionId);
        if (s != null && s.endedAt == null) NotificationHelper.showOngoing(this, s);
        WaitingWidgetProvider.updateAll(this);
        renderCurrent();
    }

    private void renderHistory() {
        page = 1;
        renderedActiveId = -2;
        timerText = null;
        content.removeAllViews();
        TextView title = text("История", 30, true);
        title.setPadding(0, dp(10), 0, dp(12));
        content.addView(title, lpMatchWrap());

        List<AppDb.Session> sessions = db.getRecentCompleted(500);
        if (sessions.isEmpty()) {
            content.addView(text("Завершённых ожиданий пока нет.", 16, false), lpMatchWrap());
            return;
        }
        for (AppDb.Session s : sessions) addSessionRow(s, true);
    }

    private void addSessionRow(AppDb.Session s, boolean editable) {
        String label = s.objectName != null ? s.objectName : (s.category != null ? s.category : "Без объекта");
        String when = formatWhen(s.startedAt);
        long duration = s.durationMs != null ? s.durationMs : AppDb.activeDuration(s);
        Button row = new Button(this);
        row.setAllCaps(false);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setText(label + "\n" + when + "  ·  " + AppDb.formatDuration(duration));
        row.setTextSize(16);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        if (editable) row.setOnClickListener(v -> showSessionActions(s.id));
        LinearLayout.LayoutParams lp = lpMatchWrap();
        lp.bottomMargin = dp(6);
        content.addView(row, lp);
    }

    private void showSessionActions(long sessionId) {
        AppDb.Session s = db.getSession(sessionId);
        if (s == null) return;
        String[] actions = new String[]{"Изменить объект", "Изменить категорию", "Изменить начало", "Изменить окончание", "Удалить"};
        new AlertDialog.Builder(this)
                .setTitle(s.objectName != null ? s.objectName : "Ожидание")
                .setItems(actions, (d, which) -> {
                    if (which == 0) showAssignDialog(sessionId, false);
                    else if (which == 1) showCategoryDialog(sessionId);
                    else if (which == 2) pickDateTime(s.startedAt, value -> {
                        if (!db.updateStart(sessionId, value)) toast("Некорректное время начала");
                        renderHistory();
                        WaitingWidgetProvider.updateAll(this);
                    });
                    else if (which == 3 && s.endedAt != null) pickDateTime(s.endedAt, value -> {
                        if (!db.updateEnd(sessionId, value)) toast("Окончание должно быть после начала и не в будущем");
                        renderHistory();
                        WaitingWidgetProvider.updateAll(this);
                    });
                    else if (which == 4) confirmDelete(sessionId);
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showCategoryDialog(long sessionId) {
        ArrayList<String> choices = new ArrayList<>();
        for (String c : AppDb.CATEGORIES) choices.add(c);
        choices.add("Без категории");
        new AlertDialog.Builder(this)
                .setTitle("Категория")
                .setItems(choices.toArray(new String[0]), (d, which) -> {
                    db.setCategory(sessionId, which < AppDb.CATEGORIES.length ? AppDb.CATEGORIES[which] : null);
                    renderHistory();
                    WaitingWidgetProvider.updateAll(this);
                }).show();
    }

    private void confirmDelete(long sessionId) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить ожидание?")
                .setMessage("Запись исчезнет из истории и статистики.")
                .setPositiveButton("Удалить", (d, w) -> {
                    db.deleteSession(sessionId);
                    renderHistory();
                    WaitingWidgetProvider.updateAll(this);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void renderStats() {
        page = 2;
        renderedActiveId = -2;
        timerText = null;
        content.removeAllViews();

        TextView title = text("Статистика", 30, true);
        title.setPadding(0, dp(10), 0, dp(10));
        content.addView(title, lpMatchWrap());

        AppDb.Stats lifetime = db.stats(0L, System.currentTimeMillis() + 1L);
        TextView lifetimeLabel = text("ВСЕГО В ОЖИДАНИИ", 14, true);
        lifetimeLabel.setGravity(Gravity.CENTER);
        lifetimeLabel.setPadding(0, dp(10), 0, dp(4));
        content.addView(lifetimeLabel, lpMatchWrap());
        TextView lifetimeValue = text(formatLifetime(lifetime.totalMs), 36, true);
        lifetimeValue.setGravity(Gravity.CENTER);
        lifetimeValue.setPadding(0, 0, 0, dp(18));
        content.addView(lifetimeValue, lpMatchWrap());

        LinearLayout periods = new LinearLayout(this);
        periods.setOrientation(LinearLayout.HORIZONTAL);
        addPeriodButton(periods, "Сегодня", AppDb.Period.TODAY);
        addPeriodButton(periods, "Неделя", AppDb.Period.WEEK);
        addPeriodButton(periods, "Месяц", AppDb.Period.MONTH);
        addPeriodButton(periods, "Всё", AppDb.Period.ALL);
        content.addView(periods, lpMatchWrap());

        long[] range = AppDb.periodRange(statsPeriod);
        AppDb.Stats stats = db.stats(range[0], range[1]);
        section(periodTitle(statsPeriod));

        content.addView(metric("Общее время ожидания", AppDb.formatDuration(stats.totalMs)), lpMatchWrap());
        content.addView(metric("Количество ожиданий", String.valueOf(stats.count)), lpMatchWrap());
        content.addView(metric("Среднее ожидание", AppDb.formatDuration(stats.averageMs())), lpMatchWrap());

        section("По категориям");
        if (stats.byCategory.isEmpty()) content.addView(text("Нет данных", 15, false), lpMatchWrap());
        else for (Map.Entry<String, Long> e : stats.byCategory.entrySet())
            content.addView(statRow(e.getKey(), e.getValue()), lpMatchWrap());

        section("По объектам");
        if (stats.byObject.isEmpty()) content.addView(text("Нет объектов с именами", 15, false), lpMatchWrap());
        else for (Map.Entry<String, Long> e : stats.byObject.entrySet())
            content.addView(statRow(e.getKey(), e.getValue()), lpMatchWrap());
    }

    private void addPeriodButton(LinearLayout row, String label, AppDb.Period period) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT, statsPeriod == period ? Typeface.BOLD : Typeface.NORMAL);
        b.setOnClickListener(v -> { statsPeriod = period; renderStats(); });
        row.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
    }

    private TextView metric(String name, String value) {
        TextView t = text(name + "\n" + value, 20, false);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        return t;
    }

    private TextView statRow(String name, long value) {
        TextView t = text(name + "  —  " + AppDb.formatDuration(value), 17, false);
        t.setPadding(dp(8), dp(7), dp(8), dp(7));
        return t;
    }

    private void section(String title) {
        TextView t = text(title, 20, true);
        t.setPadding(0, dp(24), 0, dp(8));
        content.addView(t, lpMatchWrap());
    }

    private String periodTitle(AppDb.Period p) {
        if (p == AppDb.Period.TODAY) return "Сегодня";
        if (p == AppDb.Period.WEEK) return "Эта неделя";
        if (p == AppDb.Period.MONTH) return "Этот месяц";
        return "Всё время";
    }

    private String formatWhen(long ms) {
        Calendar now = Calendar.getInstance();
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(ms);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
        if (sameDay(now, date)) return "Сегодня, " + time;
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        if (sameDay(yesterday, date)) return "Вчера, " + time;
        return new SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
                a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private String formatClock(long ms) {
        long sec = Math.max(0, ms) / 1000L;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
    }

    private String formatLifetime(long ms) {
        long minutes = Math.max(0, ms) / 60_000L;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days == 0) return AppDb.formatDuration(ms);
        long remHours = hours % 24;
        return days + " " + dayWord(days) + " " + remHours + " ч";
    }

    private String dayWord(long n) {
        long mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 14) return "дней";
        long mod10 = n % 10;
        if (mod10 == 1) return "день";
        if (mod10 >= 2 && mod10 <= 4) return "дня";
        return "дней";
    }

    private interface DateTimeCallback { void onSelected(long value); }

    private void pickDateTime(long initial, DateTimeCallback callback) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(initial);
        DatePickerDialog dateDialog = new DatePickerDialog(this,
                (DatePicker view, int year, int month, int day) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.setTimeInMillis(initial);
                    picked.set(Calendar.YEAR, year);
                    picked.set(Calendar.MONTH, month);
                    picked.set(Calendar.DAY_OF_MONTH, day);
                    TimePickerDialog timeDialog = new TimePickerDialog(this,
                            (TimePicker tv, int hour, int minute) -> {
                                picked.set(Calendar.HOUR_OF_DAY, hour);
                                picked.set(Calendar.MINUTE, minute);
                                picked.set(Calendar.SECOND, 0);
                                picked.set(Calendar.MILLISECOND, 0);
                                callback.onSelected(picked.getTimeInMillis());
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true);
                    timeDialog.show();
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dateDialog.show();
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String value, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
