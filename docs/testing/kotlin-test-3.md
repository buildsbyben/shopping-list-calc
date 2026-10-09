# Shopping Calc TEST.3 — saved-list details

This prerelease integrates StrongTheDev's feature from [PR #8](https://github.com/buildsbyben/shopping-list-calc/pull/8) with the unified Kotlin TEST.2 baseline. The integration merge retains all three original contributor commits; the Kotlin adaptation, regression coverage, and editor corrections are follow-up integration work.

## Install

Download `shopping-calc-2.2-kotlin-test.3.apk` from the GitHub prerelease assets. This is **Shopping Calc TEST**, package `io.github.buildsbyben.shoppinglistcalc.dev`, version code 20. It updates TEST.1/TEST.2 with the same test signing certificate and installs separately from the official app. This is not a stable or F-Droid release. TEST.2 remains available unchanged.

Use disposable test lists. The new build reads older name-only saved lists. Older builds do not understand the new detailed saved-list format; do not downgrade a TEST installation that has detailed lists and expect those lists to retain their data. No official-app data is imported automatically.

## Contributor credit

Original feature, per-list option, structured storage, and initial tests: **StrongTheDev (Agani Daniel .S)**, PR #8. Original commits: `49e9751`, `c0cd824`, `bb35c01`.

## What to check

1. Save a list without the checkbox: load it with both **Add to current** and **Replace current**. Expect names only, price 0, quantity 1, and weight mode off.
2. Save another list with **Include prices and quantities** enabled. Include regular and weighted items, fractional weight, duplicate names, semicolons, and quotes.
3. Load that detailed list using both buttons. Verify prices, quantities, weight mode, and totals. Loaded items must start unchecked; saving must not change the active cart's checked state.
4. Select an existing list to overwrite. Verify the checkbox adopts its saved mode. Try changing the checkbox, canceling the confirmation, and confirming an overwrite.
5. Edit a detailed saved list: insert an item at the top, reorder names, delete a line, and rename a product. Unchanged names keep details; new/renamed names start at price 0, quantity 1, weight mode off. Duplicate names retain details in occurrence order; the text editor cannot distinguish a swap of identical names.
6. Open and save the editor without changes. Literal names such as `1. Milk`, `- Bread`, quotes, and semicolons must remain intact. Changes must not affect the active cart.
7. Close/reopen the app and rotate the screen. Confirm saved data survives. Also retry rapid **+ Item** taps and the budget decimal keyboard from TEST.2.
8. Confirm older name-only TEST lists still open. Delete a test saved list and verify the current cart remains unchanged.

The saved-list editor edits names, not numeric values. To change stored prices/quantities, load the list, edit the current items, then save over it with details enabled. Weight mode is saved; the weight-unit setting remains global, as in the original feature.

Please report device/Android version, exact steps, expected versus actual behavior, and screenshots if useful. Device testing remains necessary even when automated checks pass.
