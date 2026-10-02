# Kotlin helper migration

This step converts `ShoppingItem`, `SavedList`, `CartTotals`, `CurrencyFormat`, and
`ShoppingStyle` from Java to Kotlin. `MainActivity`, `ShoppingListStore`, settings,
resources, build dependencies, versioning, and persisted data remain unchanged.

## Compatibility choices

- Ordinary classes preserve reference identity; shopping items are not data classes.
- `@JvmField` and `@JvmStatic` retain the Java caller interface during migration.
- Item defaults, sequential double arithmetic, and currency half-up rounding remain unchanged.
- Nullable currency symbols and nullable raw input retain their previous fallback behavior.
- Explicit ASCII/control-character trimming matches Java `String.trim()`; Kotlin's default
  Unicode whitespace trimming would silently change name validation and currency parsing.
- Saved-list construction still copies the input list while retaining order and duplicates.
- UI colors and dimensions are unchanged.

## Test APK check

Use the development APK (application ID ending `.dev`), not a production release.
It installs beside the released app and uses separate data; it may update an existing
`.dev` installation signed with the same debug key. This is not a production upgrade test.

1. Add two items with identical names; edit and reorder one and confirm the other stays intact.
2. Mix a normal quantity and a fractional-weight item; check subtotal, tax, and budget remaining.
3. Try direct price entry and quick-cents entry.
4. Switch between default and custom currency settings (symbol after amount, comma decimal,
   blank/space grouping); confirm display and calculations remain correct.
5. Save a template, reload it, then close/reopen the app and verify the cart/settings persist.
6. Check that colors, spacing, completed-item appearance, and menus look unchanged.

Automated checks do not replace this device check. No production release or F-Droid update
is part of this step. Storage and MainActivity migration are subsequent work.

## Automated evidence

The original 27-test regression baseline passes after conversion. Three additional
compatibility tests cover duplicate-item identity, Java whitespace behavior, and quick
entry's ASCII-digit handling; these are checked against the Java parent as well as Kotlin.
Debug and optimized R8 smoke APKs are compared using the same JDK 17, Gradle wrapper,
build variants, and local debug signing key. Dependency/build configuration is unchanged.
Exact build results and APK byte counts are recorded in the review PR.
