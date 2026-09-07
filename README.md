# Waiting / Wasting Time

Android prototype for tracking active, forced waiting time: people, transport, queues, organizations and orders.

**Product idea:** `waIting time → waSting time`

## Prototype v1

- One-tap START with no required metadata.
- One active waiting session at a time.
- Local-only persistence, no account and no Internet dependency.
- Editable start/end time and waiting object/category.
- History with edit/delete.
- Statistics for today, week, month and all time, including lifetime waiting time.
- Correct overlap accounting across day/week/month boundaries.
- Home-screen widget with direct START/STOP and system Chronometer.
- Ongoing notification with chronometer and STOP action.
- One 60-minute “still waiting?” reminder.
- State recovery after process death and device reboot.
- Light/dark system theme support.

## Build

Requires JDK 17 and Android SDK 35.

```bash
gradle :app:assembleDebug
```

GitHub Actions builds an installable debug APK on every push to `main` and `prototype-v1`.

## Prototype trade-offs

- Uses platform `SQLiteOpenHelper` instead of Room to keep the first prototype dependency-light.
- Post-STOP metadata selection uses a compact optional dialog instead of a dedicated bottom sheet component.
- Share-card generation is intentionally omitted because it was optional in the v1 specification.
