package com.krymets.waitingtimer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "waiting_timer.db";
    private static final int DB_VERSION = 1;
    private static volatile AppDb instance;

    public static final String[] CATEGORIES = new String[]{
            "Человек", "Транспорт", "Очередь", "Организация", "Заказ", "Другое"
    };

    public static AppDb get(Context context) {
        if (instance == null) {
            synchronized (AppDb.class) {
                if (instance == null) instance = new AppDb(context.getApplicationContext());
            }
        }
        return instance;
    }

    private AppDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE waiting_objects (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "last_used_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE wait_sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "started_at INTEGER NOT NULL," +
                "ended_at INTEGER," +
                "duration_ms INTEGER," +
                "waiting_object_id INTEGER," +
                "category TEXT," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "start_elapsed_at INTEGER," +
                "boot_epoch_estimate INTEGER," +
                "FOREIGN KEY(waiting_object_id) REFERENCES waiting_objects(id) ON DELETE SET NULL)");
        db.execSQL("CREATE INDEX idx_sessions_started ON wait_sessions(started_at DESC)");
        db.execSQL("CREATE INDEX idx_sessions_ended ON wait_sessions(ended_at)");
        db.execSQL("CREATE UNIQUE INDEX idx_one_active ON wait_sessions((1)) WHERE ended_at IS NULL");
        db.execSQL("CREATE INDEX idx_objects_recent ON waiting_objects(last_used_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 prototype. Future migrations should preserve local history.
    }

    public synchronized Session getActiveSession() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.*, o.name AS object_name, o.category AS object_category " +
                        "FROM wait_sessions s LEFT JOIN waiting_objects o ON o.id=s.waiting_object_id " +
                        "WHERE s.ended_at IS NULL LIMIT 1", null)) {
            return c.moveToFirst() ? readSession(c) : null;
        }
    }

    public synchronized Session getSession(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.*, o.name AS object_name, o.category AS object_category " +
                        "FROM wait_sessions s LEFT JOIN waiting_objects o ON o.id=s.waiting_object_id WHERE s.id=? LIMIT 1",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? readSession(c) : null;
        }
    }

    public synchronized long startSession() {
        Session active = getActiveSession();
        if (active != null) return active.id;
        long now = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        ContentValues v = new ContentValues();
        v.put("started_at", now);
        v.put("created_at", now);
        v.put("updated_at", now);
        v.put("start_elapsed_at", elapsed);
        v.put("boot_epoch_estimate", now - elapsed);
        return getWritableDatabase().insertOrThrow("wait_sessions", null, v);
    }

    public synchronized Session stopActive() {
        Session active = getActiveSession();
        if (active == null) return null;
        long now = System.currentTimeMillis();
        long duration = Math.max(0L, now - active.startedAt);
        ContentValues v = new ContentValues();
        v.put("ended_at", now);
        v.put("duration_ms", duration);
        v.put("updated_at", now);
        getWritableDatabase().update("wait_sessions", v, "id=?", new String[]{String.valueOf(active.id)});
        return getSession(active.id);
    }

    public synchronized boolean updateStart(long sessionId, long newStart) {
        if (newStart > System.currentTimeMillis()) return false;
        Session s = getSession(sessionId);
        if (s == null || (s.endedAt != null && newStart >= s.endedAt)) return false;
        long now = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        ContentValues v = new ContentValues();
        v.put("started_at", newStart);
        v.put("updated_at", now);
        if (s.endedAt != null) {
            v.put("duration_ms", Math.max(0, s.endedAt - newStart));
        } else {
            long delta = Math.max(0, now - newStart);
            v.put("start_elapsed_at", Math.max(0, elapsed - delta));
            v.put("boot_epoch_estimate", now - elapsed);
        }
        return getWritableDatabase().update("wait_sessions", v, "id=?", new String[]{String.valueOf(sessionId)}) > 0;
    }

    public synchronized boolean updateEnd(long sessionId, long newEnd) {
        Session s = getSession(sessionId);
        if (s == null || s.endedAt == null || newEnd <= s.startedAt || newEnd > System.currentTimeMillis()) return false;
        ContentValues v = new ContentValues();
        v.put("ended_at", newEnd);
        v.put("duration_ms", newEnd - s.startedAt);
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("wait_sessions", v, "id=?", new String[]{String.valueOf(sessionId)}) > 0;
    }

    public synchronized boolean assignObject(long sessionId, long objectId) {
        WaitingObject object = getObject(objectId);
        if (object == null) return false;
        ContentValues v = new ContentValues();
        v.put("waiting_object_id", objectId);
        v.put("category", object.category);
        v.put("updated_at", System.currentTimeMillis());
        boolean ok = getWritableDatabase().update("wait_sessions", v, "id=?", new String[]{String.valueOf(sessionId)}) > 0;
        if (ok) touchObject(objectId);
        return ok;
    }

    public synchronized boolean setCategory(long sessionId, String category) {
        ContentValues v = new ContentValues();
        if (category == null) v.putNull("category"); else v.put("category", category);
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("wait_sessions", v, "id=?", new String[]{String.valueOf(sessionId)}) > 0;
    }

    public synchronized boolean clearObject(long sessionId) {
        ContentValues v = new ContentValues();
        v.putNull("waiting_object_id");
        v.put("updated_at", System.currentTimeMillis());
        return getWritableDatabase().update("wait_sessions", v, "id=?", new String[]{String.valueOf(sessionId)}) > 0;
    }

    public synchronized boolean deleteSession(long sessionId) {
        return getWritableDatabase().delete("wait_sessions", "id=?", new String[]{String.valueOf(sessionId)}) > 0;
    }

    public synchronized long createObject(String name, String category) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) return -1;
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("name", cleanName);
        v.put("category", category == null ? "Другое" : category);
        v.put("created_at", now);
        v.put("last_used_at", now);
        return getWritableDatabase().insert("waiting_objects", null, v);
    }

    public synchronized WaitingObject getObject(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM waiting_objects WHERE id=? LIMIT 1",
                new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) return null;
            return new WaitingObject(
                    c.getLong(c.getColumnIndexOrThrow("id")),
                    c.getString(c.getColumnIndexOrThrow("name")),
                    c.getString(c.getColumnIndexOrThrow("category")),
                    c.getLong(c.getColumnIndexOrThrow("created_at")),
                    c.getLong(c.getColumnIndexOrThrow("last_used_at")));
        }
    }

    private synchronized void touchObject(long id) {
        ContentValues v = new ContentValues();
        v.put("last_used_at", System.currentTimeMillis());
        getWritableDatabase().update("waiting_objects", v, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized List<WaitingObject> getRecentObjects(int limit) {
        ArrayList<WaitingObject> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM waiting_objects ORDER BY last_used_at DESC LIMIT " + Math.max(1, limit), null)) {
            while (c.moveToNext()) {
                out.add(new WaitingObject(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("name")),
                        c.getString(c.getColumnIndexOrThrow("category")),
                        c.getLong(c.getColumnIndexOrThrow("created_at")),
                        c.getLong(c.getColumnIndexOrThrow("last_used_at"))));
            }
        }
        return out;
    }

    public synchronized List<Session> getRecentCompleted(int limit) {
        ArrayList<Session> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.*, o.name AS object_name, o.category AS object_category " +
                        "FROM wait_sessions s LEFT JOIN waiting_objects o ON o.id=s.waiting_object_id " +
                        "WHERE s.ended_at IS NOT NULL ORDER BY s.started_at DESC LIMIT " + Math.max(1, limit), null)) {
            while (c.moveToNext()) out.add(readSession(c));
        }
        return out;
    }

    public synchronized List<Session> getAllSessions() {
        ArrayList<Session> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.*, o.name AS object_name, o.category AS object_category " +
                        "FROM wait_sessions s LEFT JOIN waiting_objects o ON o.id=s.waiting_object_id " +
                        "ORDER BY s.started_at DESC", null)) {
            while (c.moveToNext()) out.add(readSession(c));
        }
        return out;
    }

    private Session readSession(Cursor c) {
        int endedIdx = c.getColumnIndexOrThrow("ended_at");
        int durationIdx = c.getColumnIndexOrThrow("duration_ms");
        int objectIdx = c.getColumnIndexOrThrow("waiting_object_id");
        int elapsedIdx = c.getColumnIndexOrThrow("start_elapsed_at");
        int bootIdx = c.getColumnIndexOrThrow("boot_epoch_estimate");
        String objectName = c.getString(c.getColumnIndexOrThrow("object_name"));
        String category = c.getString(c.getColumnIndexOrThrow("category"));
        if (category == null) category = c.getString(c.getColumnIndexOrThrow("object_category"));
        return new Session(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getLong(c.getColumnIndexOrThrow("started_at")),
                c.isNull(endedIdx) ? null : c.getLong(endedIdx),
                c.isNull(durationIdx) ? null : c.getLong(durationIdx),
                c.isNull(objectIdx) ? null : c.getLong(objectIdx),
                objectName,
                category,
                c.getLong(c.getColumnIndexOrThrow("created_at")),
                c.getLong(c.getColumnIndexOrThrow("updated_at")),
                c.isNull(elapsedIdx) ? null : c.getLong(elapsedIdx),
                c.isNull(bootIdx) ? null : c.getLong(bootIdx));
    }

    public synchronized Stats stats(long fromInclusive, long toExclusive) {
        long now = System.currentTimeMillis();
        long total = 0;
        int count = 0;
        Map<String, Long> byCategory = new LinkedHashMap<>();
        Map<String, Long> byObject = new LinkedHashMap<>();

        for (Session s : getAllSessions()) {
            long end = s.endedAt == null ? now : s.endedAt;
            long overlapStart = Math.max(s.startedAt, fromInclusive);
            long overlapEnd = Math.min(end, toExclusive);
            if (overlapEnd <= overlapStart) continue;
            long duration = overlapEnd - overlapStart;
            total += duration;
            count++;
            String category = s.category == null ? "Остальное" : s.category;
            byCategory.put(category, byCategory.getOrDefault(category, 0L) + duration);
            if (s.objectName != null && !s.objectName.trim().isEmpty()) {
                byObject.put(s.objectName, byObject.getOrDefault(s.objectName, 0L) + duration);
            }
        }
        return new Stats(total, count, sortMap(byCategory), sortMap(byObject));
    }

    private LinkedHashMap<String, Long> sortMap(Map<String, Long> input) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(input.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        LinkedHashMap<String, Long> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : entries) sorted.put(e.getKey(), e.getValue());
        return sorted;
    }

    public long todayTotal() {
        long[] range = periodRange(Period.TODAY);
        return stats(range[0], range[1]).totalMs;
    }

    public long monthTotal() {
        long[] range = periodRange(Period.MONTH);
        return stats(range[0], range[1]).totalMs;
    }

    public static long[] periodRange(Period period) {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = Calendar.getInstance();

        if (period == Period.TODAY) {
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_MONTH, 1);
        } else if (period == Period.WEEK) {
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            int day = start.get(Calendar.DAY_OF_WEEK);
            int delta = (day == Calendar.SUNDAY) ? -6 : Calendar.MONDAY - day;
            start.add(Calendar.DAY_OF_MONTH, delta);
            end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_MONTH, 7);
        } else if (period == Period.MONTH) {
            start.set(Calendar.DAY_OF_MONTH, 1);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            end = (Calendar) start.clone();
            end.add(Calendar.MONTH, 1);
        } else {
            start.setTimeInMillis(0L);
            end.setTimeInMillis(System.currentTimeMillis() + 1L);
        }
        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    public static long activeDuration(Session s) {
        if (s == null) return 0;
        if (s.endedAt != null) return Math.max(0, s.endedAt - s.startedAt);
        long now = System.currentTimeMillis();
        long elapsedNow = SystemClock.elapsedRealtime();
        if (s.startElapsedAt != null && s.bootEpochEstimate != null) {
            long currentBootEstimate = now - elapsedNow;
            if (Math.abs(currentBootEstimate - s.bootEpochEstimate) < 120_000L && elapsedNow >= s.startElapsedAt) {
                return elapsedNow - s.startElapsedAt;
            }
        }
        return Math.max(0, now - s.startedAt);
    }

    public static String formatDuration(long ms) {
        long minutes = Math.max(0, ms) / 60_000L;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) return String.format(Locale.getDefault(), "%d дн %d ч", days, hours % 24);
        if (hours > 0) return String.format(Locale.getDefault(), "%d ч %02d мин", hours, minutes % 60);
        return String.format(Locale.getDefault(), "%d мин", minutes);
    }

    public enum Period { TODAY, WEEK, MONTH, ALL }

    public static class Session {
        public final long id;
        public final long startedAt;
        public final Long endedAt;
        public final Long durationMs;
        public final Long waitingObjectId;
        public final String objectName;
        public final String category;
        public final long createdAt;
        public final long updatedAt;
        public final Long startElapsedAt;
        public final Long bootEpochEstimate;

        public Session(long id, long startedAt, Long endedAt, Long durationMs, Long waitingObjectId,
                       String objectName, String category, long createdAt, long updatedAt,
                       Long startElapsedAt, Long bootEpochEstimate) {
            this.id = id;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.durationMs = durationMs;
            this.waitingObjectId = waitingObjectId;
            this.objectName = objectName;
            this.category = category;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.startElapsedAt = startElapsedAt;
            this.bootEpochEstimate = bootEpochEstimate;
        }
    }

    public static class WaitingObject {
        public final long id;
        public final String name;
        public final String category;
        public final long createdAt;
        public final long lastUsedAt;

        public WaitingObject(long id, String name, String category, long createdAt, long lastUsedAt) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.createdAt = createdAt;
            this.lastUsedAt = lastUsedAt;
        }
    }

    public static class Stats {
        public final long totalMs;
        public final int count;
        public final LinkedHashMap<String, Long> byCategory;
        public final LinkedHashMap<String, Long> byObject;

        public Stats(long totalMs, int count, LinkedHashMap<String, Long> byCategory, LinkedHashMap<String, Long> byObject) {
            this.totalMs = totalMs;
            this.count = count;
            this.byCategory = byCategory;
            this.byObject = byObject;
        }

        public long averageMs() { return count == 0 ? 0 : totalMs / count; }
    }
}
