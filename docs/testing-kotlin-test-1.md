# Kotlin test build 1

- Launcher name: Shopping Calc TEST
- Version: 2.2-kotlin-test.1 (code 18)
- Package: io.github.buildsbyben.shoppinglistcalc.dev
- Contains all eight Kotlin production files from the stacked migration, plus the rapid-add callback fix.
- Debug-signed unofficial build; separate from the official app. Reuses the existing .dev package, not another new test-app identity.

## Fix and regression

The unpatched full Kotlin branch throws `IllegalArgumentException: parameter must be a descendant of this view` in MainActivity's delayed scroll callback when five Add taps are spaced 150 ms apart. List rebuilding detaches the callback's row.

The fix cancels pending row focus/scroll callbacks before list rebuilding and on pause/destruction, verifies the target is still attached to the current list, and replaces obsolete scroll requests. It does not drop or throttle item additions.

## Phone checks

1. Keep the official app installed. Install the APK and open Shopping Calc TEST.
2. Rapidly tap + Item 3–5 times, repeatedly and at varying speeds, with the keyboard both visible and hidden. Every tap should add an item without crashing; focus should finish on the newest row.
3. Try Settings > New item focus modes, price keyboard Next, deleting a newly added row, and rotating/backgrounding immediately after adding.
4. Enter item names/prices, close and reopen, verify totals and persistence; check saved lists and drag reordering.

Automated checks do not replace Android 16/Samsung phone acceptance. No main-branch merge or official release is part of this build.
