# Kotlin migration regression baseline

This change adds tests only; application behavior, storage, UI, and dependencies are unchanged.
The new suites are written in Kotlin so they can remain in place during the Java migration.

## Run

With JDK 17 and Android SDK 35 configured via `ANDROID_HOME` or `local.properties`:

```sh
./gradlew testDebugUnitTest lintDebug --no-daemon --max-workers=2
```

GitHub Actions runs this command for pull requests and pushes to `main` or `test/**`.
Reports are uploaded even when checks fail.

## Contracts covered

- Empty carts, quantity/weight multiplication, tax, and unrounded intermediate totals.
- Current inclusion of both completed and incomplete items in totals.
- Item defaults and quick-entry readiness; saved-list defensive copying.
- Currency prefix/suffix, grouping disabled or space-separated, fraction limits,
  half-up rounding, direct and quick entry, invalid-input fallback, locale independence.
- Fresh-install defaults and literal legacy preferences/JSON, including missing fields.
- Written preference types and JSON keys, all persisted item fields, Unicode/escaping,
  empty-list saves, saved-list order/duplicates, blank names, and malformed data handling.
- Independence of current-cart data and saved templates.
- Existing six SettingsActivity tests remain unchanged.

Literal legacy fixtures and direct schema assertions deliberately supplement round-trip
checks: changing both the reader and writer must not silently break existing installs.
These tests characterize current behavior; migration changes should preserve it. Product
behavior changes should be reviewed separately instead of silently changing expectations.

## Still needed before a complete Kotlin migration

This is a model/currency/storage baseline, not full application coverage. MainActivity
keyboard/focus navigation, drag-and-drop and auto-scroll, dialogs, lifecycle behavior,
and end-to-end saved-list operations still need targeted coverage and device checks.
Robolectric storage reopening is not a real installed-APK upgrade test. Before release,
also verify existing-user data through an actual upgrade, optimized release behavior,
APK size, and F-Droid reproducibility.
