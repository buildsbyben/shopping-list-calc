package io.github.buildsbyben.shoppinglistcalc

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.DragEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

import java.util.ArrayList
import java.util.Collections

class MainActivity : Activity() {
    private companion object {
        const val PREFS = "shopping_calc"
    }
    private val items: ArrayList<ShoppingItem> = ArrayList()
    private lateinit var money: CurrencyFormat
    private lateinit var store: ShoppingListStore
    private lateinit var scroll: ScrollView
    private lateinit var scrollContent: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var subtotalView: TextView
    private lateinit var taxView: TextView
    private lateinit var totalView: TextView
    private lateinit var remainingView: TextView
    private lateinit var addButton: Button
    private lateinit var menuButton: Button
    private var rebuilding: Boolean = false
    private var focusAfterRebuild: ShoppingItem? = null
    private var taxRate: Double = 0.0
    private var budget: Double = 0.0
    private var formattingPrice: Boolean = false
    private var quickCentsEntry: Boolean = false
    private var quickEntry: Boolean = false
    private var reordering: Boolean = false
    private var reorderItems: ArrayList<ShoppingItem>? = null
    private lateinit var weightUnit: String
    private var reorderAutoScrollDirection: Int = 0
    private val reorderAutoScroller = object : Runnable {
        override fun run() {
            if (!::scroll.isInitialized || reorderAutoScrollDirection == 0) {
                return
            }
            val before: Int = scroll.getScrollY()
            scroll.scrollBy(0, reorderAutoScrollDirection * dp(12))
            if (scroll.getScrollY() != before) {
                scroll.postDelayed(this, 16)
            }
        }
    }

    private val bg: Int = ShoppingStyle.BACKGROUND
    private val inputBg: Int = ShoppingStyle.INPUT_BACKGROUND
    private val panelSoft: Int = ShoppingStyle.CONTROL_BACKGROUND
    private val panelIcon: Int = ShoppingStyle.CONTROL_ICON
    private val cardBg: Int = ShoppingStyle.CARD_BACKGROUND
    private val completedCardBg: Int = ShoppingStyle.COMPLETED_CARD_BACKGROUND
    private val text: Int = ShoppingStyle.TEXT
    private val muted: Int = ShoppingStyle.MUTED_TEXT
    private val danger: Int = ShoppingStyle.DANGER
    private val accent: Int = ShoppingStyle.ACCENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        getWindow().setStatusBarColor(bg)
        getWindow().setNavigationBarColor(bg)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false)
        }
        store = ShoppingListStore(getSharedPreferences(PREFS, MODE_PRIVATE))
        load()
        buildUi()
        rebuildList()
        recalc()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) {
            load()
            if (::list.isInitialized) {
                rebuildList()
                recalc()
            }
        }
    }

    private fun buildUi() {
        val screen: LinearLayout = column()
        screen.setBackgroundColor(bg)

        val summary: LinearLayout = column()
        val summaryHorizontalPadding: Int = dp(14)
        val summaryTopPadding: Int = dp(8)
        val summaryBottomPadding: Int = dp(8)
        val contentHorizontalPadding: Int = dp(16)
        val contentTopPadding: Int = dp(12)
        val contentBottomPadding: Int = dp(24)
        summary.setPadding(summaryHorizontalPadding, summaryTopPadding, summaryHorizontalPadding, summaryBottomPadding)
        summary.setBackgroundColor(altBackground())
        screen.addView(summary, matchWrap(LinearLayout.LayoutParams(0, 0)))

        val totalLine: LinearLayout = row()
        totalLine.setGravity(Gravity.CENTER_VERTICAL)
        summary.addView(totalLine)

        val totalBox: LinearLayout = column()
        totalBox.addView(label("Total", 11, muted, false))
        totalView = label("\$0.00", 26, accent, true)
        totalBox.addView(totalView)
        totalLine.addView(totalBox, weightWrap(1f))

        val add: Button = button("+ Item", accent, contrastFor(accent))
        totalLine.addView(add, LinearLayout.LayoutParams(dp(84), dp(ShoppingStyle.SUMMARY_ACTION_SIZE_DP)))
        addButton = add

        val menu: Button = button("⋮", panelSoft, panelIcon)
        menu.setTextSize(22f)
        val menuParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                dp(ShoppingStyle.SUMMARY_ACTION_SIZE_DP),
                dp(ShoppingStyle.SUMMARY_ACTION_SIZE_DP)
        )
        menuParams.leftMargin = dp(ShoppingStyle.FIELD_GAP_DP)
        totalLine.addView(menu, menuParams)
        menuButton = menu

        val summaryGrid: LinearLayout = row()
        summaryGrid.setGravity(Gravity.CENTER_VERTICAL)
        summary.addView(summaryGrid, matchWrap(top(6)))
        subtotalView = metric(summaryGrid, "Subtotal")
        taxView = metric(summaryGrid, "Tax")
        remainingView = metric(summaryGrid, "Remaining")

        add.setOnClickListener({ v ->
            val item: ShoppingItem = ShoppingItem()
            item.order = nextOrder()
            item.qty = 1.0
            items.add(item)
            saveItems()
            focusAfterRebuild = item
            rebuildList()
            recalc()
         })
        menu.setOnClickListener({ v -> showActionsMenu(menu) })

        scroll = ScrollView(this)
        scroll.setFillViewport(true)
        scroll.setBackgroundColor(bg)
        scroll.setClipToPadding(false)
        screen.addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f))

        scrollContent = column()
        scrollContent.setPadding(contentHorizontalPadding, contentTopPadding, contentHorizontalPadding, contentBottomPadding)
        scroll.addView(scrollContent)

        list = column()
        scrollContent.addView(list, matchWrap(LinearLayout.LayoutParams(0, 0)))

        screen.setOnApplyWindowInsetsListener(applyInsets@{ view, insets ->
            var statusTop: Int = 0
            var bottomInset: Int = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                statusTop = insets.getInsets(WindowInsets.Type.statusBars()).top
                val navBottom: Int = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                val imeBottom: Int = insets.getInsets(WindowInsets.Type.ime()).bottom
                bottomInset = Math.max(navBottom, imeBottom)
            } else {
                statusTop = insets.getSystemWindowInsetTop()
                bottomInset = insets.getSystemWindowInsetBottom()
            }
            summary.setPadding(
                    summaryHorizontalPadding,
                    summaryTopPadding + statusTop,
                    summaryHorizontalPadding,
                    summaryBottomPadding
            )
            if (::scrollContent.isInitialized) {
                scrollContent.setPadding(
                        contentHorizontalPadding,
                        contentTopPadding,
                        contentHorizontalPadding,
                        contentBottomPadding + bottomInset
                )
            }
            return@applyInsets insets
         })

        setContentView(screen)
    }

    private fun showActionsMenu(anchor: View) {
        val menu: PopupMenu = PopupMenu(this, anchor)
        menu.getMenu().add("Settings")
        menu.getMenu().add("Edit List")
        menu.getMenu().add("Reorder items")
        menu.getMenu().add("Saved lists")
        menu.getMenu().add("Clear / Delete list")
        menu.setOnMenuItemClickListener(selectAction@{ item ->
            val title: String = item.getTitle().toString()
            if ("Settings".equals(title)) {
                startActivity(Intent(this, SettingsActivity::class.java))
                return@selectAction true
            }
            if ("Saved lists".equals(title)) {
                showSavedListsMenu()
                return@selectAction true
            }
            if ("Edit List".equals(title)) {
                showListViewDialog()
                return@selectAction true
            }
            if ("Reorder items".equals(title)) {
                enterReorderMode()
                return@selectAction true
            }
            if ("Clear / Delete list".equals(title)) {
                showClearDeleteListDialog()
                return@selectAction true
            }
            return@selectAction false
         })
        menu.show()
    }

    private fun showClearDeleteListDialog() {
        AlertDialog.Builder(this)
                .setTitle("Clear / Delete list")
                .setMessage("Clear values keeps item names and resets prices, quantities, and checked status. Delete all removes every item from the current list.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear values", { dialog, which -> clearList() })
                .setPositiveButton("Delete all", { dialog, which -> deleteList() })
                .show()
    }

    private fun showSavedListsMenu() {
        AlertDialog.Builder(this)
                .setTitle("Saved lists")
                .setItems(arrayOf("Save current list", "Load saved list", "Edit saved list"), { dialog, which ->
                    if (which == 0) {
                        showSavedListPicker(SavedListAction.SAVE)
                    } else if (which == 1) {
                        showSavedListPicker(SavedListAction.LOAD)
                    } else {
                        showSavedListPicker(SavedListAction.EDIT)
                    }
                 })
                .setNegativeButton("Cancel", null)
                .show()
    }

    private fun showSavedListPicker(action: SavedListAction) {
        val savedLists: ArrayList<SavedList> = store.savedLists()
        if (action == SavedListAction.SAVE) {
            showSaveCurrentListDialog(savedLists)
            return
        }
        if (savedLists.isEmpty() && action != SavedListAction.SAVE) {
            AlertDialog.Builder(this)
                    .setTitle("No saved lists")
                    .setMessage("Save your current list first, then you can load or edit it here.")
                    .setPositiveButton("OK", null)
                    .show()
            return
        }

        val choices: ArrayList<String> = ArrayList()
        for (savedList in savedLists) {
            choices.add(savedList.name)
        }
        val title: String = if (action == SavedListAction.LOAD) "Load saved list" else "Edit saved list"
        AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(choices.toTypedArray(), { dialog, which ->
                    val selected: SavedList = savedLists.get(which)
                    if (action == SavedListAction.LOAD) {
                        confirmLoadSavedList(selected)
                    } else {
                        showSavedListEditor(selected)
                    }
                 })
                .setNegativeButton("Cancel", null)
                .show()
    }

    private fun showSaveCurrentListDialog(savedLists: ArrayList<SavedList>) {
        val nameInput: EditText = input("New saved list name", false)
        nameInput.setSingleLine(true)
        nameInput.setPadding(dp(12), dp(6), dp(12), dp(6))
        val wrap: LinearLayout = column()
        wrap.setPadding(dp(18), dp(8), dp(18), 0)
        wrap.addView(nameInput, dialogNameInputParams())

        val existingLabel: TextView = label("Or select a saved list to overwrite", 13, muted, false)
        wrap.addView(existingLabel, matchWrap(top(14)))
        val savedListScroll: ScrollView = ScrollView(this)
        val savedListChoices: RadioGroup = RadioGroup(this)
        savedListChoices.setOrientation(LinearLayout.VERTICAL)
        for (i in 0 until savedLists.size) {
            val choice: RadioButton = RadioButton(this)
            choice.setId(i + 1)
            choice.setText(savedLists.get(i).name)
            choice.setTextColor(text)
            choice.setTextSize(16f)
            choice.setPadding(0, dp(3), 0, dp(3))
            savedListChoices.addView(choice, matchWrap(LinearLayout.LayoutParams(0, 0)))
        }
        savedListScroll.addView(savedListChoices)
        val scrollParams: LinearLayout.LayoutParams = matchWrap(LinearLayout.LayoutParams(0, 0))
        scrollParams.height = dp(180)
        wrap.addView(savedListScroll, scrollParams)

        nameInput.addTextChangedListener(object : SimpleWatcher() {
            override fun afterTextChanged(s: Editable) {
                if (s.length > 0) {
                    savedListChoices.clearCheck()
                }
            }
        })
        val dialog: AlertDialog = AlertDialog.Builder(this)
                .setTitle("Save current list")
                .setMessage("Save item names from your current list.")
                .setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create()
        dialog.setOnShowListener({ d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(saveList@{ v ->
            val selectedId: Int = savedListChoices.getCheckedRadioButtonId()
            if (selectedId != -1) {
                dialog.dismiss()
                confirmOverwriteSavedList(savedLists.get(selectedId - 1))
                return@saveList
            }
            val name: String = nameInput.getText().toString().trim { it <= ' ' }
            if (name.isEmpty()) {
                nameInput.setError("Enter a name or select a saved list")
                return@saveList
            }
            for (savedList in savedLists) {
                if (savedList.name.equals(name, ignoreCase = true)) {
                    dialog.dismiss()
                    confirmOverwriteSavedList(SavedList(name, savedList.itemNames))
                    return@saveList
                }
            }
            savedLists.add(SavedList(name, cleanListNames(currentItemNameList())))
            store.saveSavedLists(savedLists)
            dialog.dismiss()
         }) })
        dialog.show()
    }

    private fun confirmOverwriteSavedList(selected: SavedList) {
        AlertDialog.Builder(this)
                .setTitle("Replace saved list?")
                .setMessage("Your current item names will replace \"" + selected.name + "\". This does not change your current list.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Replace", { dialog, which ->
                    val savedLists: ArrayList<SavedList> = store.savedLists()
                    replaceSavedList(savedLists, selected.name, cleanListNames(currentItemNameList()))
                    store.saveSavedLists(savedLists)
                 })
                .show()
    }

    private fun confirmLoadSavedList(selected: SavedList) {
        AlertDialog.Builder(this)
                .setTitle("Use \"" + selected.name + "\"")
                .setMessage("Choose how its item names should affect your current list.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Add to current", { dialog, which -> addSavedListToCurrent(selected) })
                .setPositiveButton("Replace current", { dialog, which -> replaceCurrentWithSavedList(selected) })
                .show()
    }

    private fun addSavedListToCurrent(selected: SavedList) {
        for (name in selected.itemNames) {
            val item: ShoppingItem = ShoppingItem()
            item.name = name
            item.qty = 1.0
            item.order = nextOrder()
            items.add(item)
        }
        saveItems()
        rebuildList()
        recalc()
    }

    private fun replaceCurrentWithSavedList(selected: SavedList) {
        items.clear()
        for (i in 0 until selected.itemNames.size) {
            val item: ShoppingItem = ShoppingItem()
            item.name = selected.itemNames.get(i)
            item.qty = 1.0
            item.order = (i + 1) * 10
            items.add(item)
        }
        saveItems()
        rebuildList()
        recalc()
    }

    private fun showSavedListEditor(selected: SavedList) {
        val nameInput: EditText = input("Saved list name", false)
        nameInput.setText(selected.name)
        nameInput.setSingleLine(true)
        nameInput.setPadding(dp(12), dp(6), dp(12), dp(6))
        val listInput: EditText = multilineListInput()
        listInput.setText(joinListNames(selected.itemNames))
        val wrap: LinearLayout = column()
        wrap.setPadding(dp(18), dp(8), dp(18), 0)
        wrap.addView(nameInput, dialogNameInputParams())
        wrap.addView(listInput, matchWrap(top(ShoppingStyle.FIELD_GAP_DP)))
        val dialog: AlertDialog = AlertDialog.Builder(this)
                .setMessage("One item per line. Changes affect only this saved list.")
                .setView(wrap)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", null)
                .setPositiveButton("Save", null)
                .create()
        dialog.setOnShowListener({ d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(saveEdits@{ v ->
                val name: String = nameInput.getText().toString().trim { it <= ' ' }
                if (name.isEmpty()) {
                    nameInput.setError("Enter a saved list name")
                    return@saveEdits
                }
                val savedLists: ArrayList<SavedList> = store.savedLists()
                for (savedList in savedLists) {
                    if (!savedList.name.equals(selected.name, ignoreCase = true) && savedList.name.equals(name, ignoreCase = true)) {
                        nameInput.setError("A saved list already uses this name")
                        return@saveEdits
                    }
                }
                updateSavedList(savedLists, selected.name, name, cleanListNames(listInput.getText().toString()))
                store.saveSavedLists(savedLists)
                dialog.dismiss()
             })
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener({ v -> confirmDeleteSavedList(selected.name, dialog) })
         })
        dialog.show()
    }

    private fun confirmDeleteSavedList(name: String, editorDialog: AlertDialog) {
        AlertDialog.Builder(this)
                .setTitle("Delete saved list?")
                .setMessage("\"" + name + "\" will be permanently deleted. Your current list will not change.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", { dialog, which ->
                    val savedLists: ArrayList<SavedList> = store.savedLists()
                    for (i in savedLists.size - 1 downTo 0) {
                        if (savedLists.get(i).name.equals(name)) {
                            savedLists.removeAt(i)
                        }
                    }
                    store.saveSavedLists(savedLists)
                    editorDialog.dismiss()
                 })
                .show()
    }

    private fun replaceSavedList(savedLists: ArrayList<SavedList>, name: String, names: ArrayList<String>) {
        for (i in 0 until savedLists.size) {
            if (savedLists.get(i).name.equals(name, ignoreCase = true)) {
                savedLists.set(i, SavedList(savedLists.get(i).name, names))
                return
            }
        }
        savedLists.add(SavedList(name, names))
    }

    private fun updateSavedList(savedLists: ArrayList<SavedList>, originalName: String, updatedName: String, names: ArrayList<String>) {
        for (i in 0 until savedLists.size) {
            if (savedLists.get(i).name.equals(originalName)) {
                savedLists.set(i, SavedList(updatedName, names))
                return
            }
        }
        savedLists.add(SavedList(updatedName, names))
    }

    private fun showListViewDialog() {
        val listInput: EditText = multilineListInput()
        listInput.setText(currentItemNameList())
        listInput.setSelection(listInput.getText().length)

        val wrap: LinearLayout = column()
        wrap.setPadding(dp(18), dp(8), dp(18), 0)
        wrap.addView(listInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val dialog: AlertDialog = AlertDialog.Builder(this)
                .setTitle("Edit List")
                .setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create()

        dialog.setOnShowListener({ d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener({ v ->
                applyListView(listInput.getText().toString())
                dialog.dismiss()
             })
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(muted)
            listInput.requestFocus()
            listInput.postDelayed({
                val imm: InputMethodManager? = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
                if (imm != null) {
                    imm.showSoftInput(listInput, InputMethodManager.SHOW_IMPLICIT)
                }
             }, 180)
         })

        dialog.show()
    }

    private fun enterReorderMode() {
        sortItems()
        reorderItems = ArrayList(items)
        reordering = true
        addButton.setText("Save")
        addButton.setOnClickListener({ v -> exitReorderMode(true) })
        menuButton.setText("Cancel")
        menuButton.setTextSize(12f)
        menuButton.setOnClickListener({ v -> exitReorderMode(false) })
        rebuildList()
    }

    private fun exitReorderMode(save: Boolean) {
        if (save && reorderItems != null) {
            for (i in 0 until reorderItems!!.size) {
                reorderItems!!.get(i).order = (i + 1) * 10
            }
            items.clear()
            items.addAll(reorderItems!!)
            saveItems()
        }
        reordering = false
        reorderItems = null
        addButton.setText("+ Item")
        addButton.setOnClickListener({ v ->
            val item: ShoppingItem = ShoppingItem()
            item.order = nextOrder()
            item.qty = 1.0
            items.add(item)
            saveItems()
            focusAfterRebuild = item
            rebuildList()
            recalc()
         })
        menuButton.setText("⋮")
        menuButton.setTextSize(22f)
        menuButton.setOnClickListener({ v -> showActionsMenu(menuButton) })
        rebuildList()
        recalc()
    }

    private fun addReorderRow(rows: LinearLayout, reorderItems: ArrayList<ShoppingItem>, item: ShoppingItem) {
        val row: LinearLayout = row()
        row.setGravity(Gravity.CENTER_VERTICAL)
        row.setPadding(dp(10), dp(8), dp(6), dp(8))
        row.setBackgroundColor(cardBg)

        val details: LinearLayout = column()
        val name: TextView = label(if (item.name.isEmpty()) "Unnamed item" else item.name, 16, text, true)
        details.addView(name)
        val quantity: String = if (item.byWeight) trimNumber(item.qty) + " " + weightUnit else "Qty " + trimNumber(item.qty)
        details.addView(label(money.format(item.price) + " · " + quantity, 13, muted, false))
        row.addView(details, weightWrap(1f))

        val handle: TextView = label("☰", 27, panelIcon, false)
        handle.setGravity(Gravity.CENTER)
        row.setContentDescription("Press and hold to reorder " + item.name)
        handle.setContentDescription("Press and hold anywhere on this item to reorder " + item.name)
        row.addView(handle, LinearLayout.LayoutParams(dp(48), dp(52)))
        rows.addView(row, matchWrap(bottom(ShoppingStyle.ITEM_CARD_GAP_DP)))

        val startDrag = View.OnLongClickListener({ v -> startReorderDrag(rows, row, item) })
        // The entire item is the drag target. Keep the same listener on the
        // handle too: a child view can receive the long-press before its row.
        row.setOnLongClickListener(startDrag)
        handle.setOnLongClickListener(startDrag)
        // A drag event is delivered to the view directly under the finger first.
        // Handle it here as well as on the list so crossing a row always updates
        // the insertion marker instead of leaving the drag stranded on that row.
        row.setOnDragListener({ v, event -> handleReorderDragEvent(
                rows, v, event, v.getTop()) })
    }

    private fun startReorderDrag(rows: LinearLayout, row: View, item: ShoppingItem): Boolean {
        val drag: ReorderDrag = ReorderDrag(row, item)
        drag.placeholder = reorderPlaceholder()
        val data: ClipData = ClipData.newPlainText("shopping-item", item.name)
        val shadow: View.DragShadowBuilder = View.DragShadowBuilder(row)
        val started: Boolean
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            started = row.startDragAndDrop(data, shadow, drag, 0)
        } else {
            started = row.startDrag(data, shadow, drag, 0)
        }
        // startDragAndDrop requires an attached source view.  The old code
        // removed it before starting, which could leave only empty markers.
        if (started) {
            val index: Int = rows.indexOfChild(row)
            rows.removeView(row)
            rows.addView(drag.placeholder, index)
        }
        return started
    }

    private fun updateReorderAutoScroll(target: View, event: DragEvent) {
        if (!::scroll.isInitialized) {
            return
        }
        val targetLocation: IntArray = IntArray(2)
        val scrollLocation: IntArray = IntArray(2)
        target.getLocationOnScreen(targetLocation)
        scroll.getLocationOnScreen(scrollLocation)
        val yInViewport: Float = targetLocation[1] + event.getY() - scrollLocation[1]
        val edge: Int = dp(56)
        val direction: Int = if (yInViewport < edge) -1 else if (yInViewport > scroll.getHeight() - edge) 1 else 0
        if (direction == reorderAutoScrollDirection) {
            return
        }
        reorderAutoScrollDirection = direction
        scroll.removeCallbacks(reorderAutoScroller)
        if (direction != 0) {
            scroll.post(reorderAutoScroller)
        }
    }

    private fun stopReorderAutoScroll() {
        reorderAutoScrollDirection = 0
        if (::scroll.isInitialized) {
            scroll.removeCallbacks(reorderAutoScroller)
        }
    }

    private fun reorderPlaceholder(): View {
        val placeholder: View = View(this)
        val outline: GradientDrawable = GradientDrawable()
        outline.setColor(Color.TRANSPARENT)
        outline.setStroke(dp(1), accent)
        placeholder.setBackground(outline)
        placeholder.setOnDragListener({ v, event -> handleReorderDragEvent(
                list, v, event, v.getTop()) })
        val params: LinearLayout.LayoutParams = matchWrap(bottom(ShoppingStyle.ITEM_CARD_GAP_DP))
        params.height = dp(68)
        placeholder.setLayoutParams(params)
        return placeholder
    }

    private fun wireReorderDragTarget(rows: LinearLayout, reorderItems: ArrayList<ShoppingItem>) {
        rows.setOnDragListener({ v, event -> handleReorderDragEvent(rows, rows, event, 0) })
    }

    private fun handleReorderDragEvent(rows: LinearLayout, target: View, event: DragEvent, targetTop: Int): Boolean {
        if (!(event.getLocalState() is ReorderDrag)) {
            return false
        }
        val drag: ReorderDrag = event.getLocalState() as ReorderDrag
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            return true
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
            updateReorderAutoScroll(target, event)
            movePlaceholder(rows, drag.placeholder, targetTop + event.getY())
            return true
        }
        if (event.getAction() == DragEvent.ACTION_DROP) {
            stopReorderAutoScroll()
            if (drag.finished) {
                return true
            }
            val destination: Int = rows.indexOfChild(drag.placeholder) - 1
            rows.removeView(drag.placeholder)
            rows.addView(drag.row, destination + 1)
            reorderItems!!.remove(drag.item)
            reorderItems!!.add(destination, drag.item)
            drag.dropped = true
            drag.finished = true
            return true
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            stopReorderAutoScroll()
            if (!drag.finished && rows.indexOfChild(drag.placeholder) >= 0) {
                val destination: Int = rows.indexOfChild(drag.placeholder)
                rows.removeView(drag.placeholder)
                rows.addView(drag.row, destination)
                drag.finished = true
            }
            return true
        }
        return true
    }

    private fun movePlaceholder(rows: LinearLayout, placeholder: View, y: Float) {
        rows.removeView(placeholder)
        rows.addView(placeholder, reorderTargetIndex(rows, y))
    }

    private fun reorderTargetIndex(rows: LinearLayout, y: Float): Int {
        for (i in 1 until rows.getChildCount()) {
            val child: View = rows.getChildAt(i)
            if (y < child.getTop() + child.getHeight() / 2f) {
                return i
            }
        }
        return rows.getChildCount()
    }

    private fun rebuildList() {
        rebuilding = true
        if (reordering) {
            rebuildReorderList()
            rebuilding = false
            return
        }
        sortItems()
        list.removeAllViews()
        val itemInputs: ArrayList<ItemInput> = ArrayList()

        if (items.isEmpty()) {
            val empty: TextView = label("No items yet.", 16, muted, false)
            empty.setGravity(Gravity.CENTER)
            empty.setPadding(0, dp(24), 0, dp(24))
            list.addView(empty)
            rebuilding = false
            return
        }

        for (item in items) {
            if (item.inCart) {
                addCompletedItemCard(item)
                continue
            }

            val card: LinearLayout = column()
            card.setPadding(
                    dp(ShoppingStyle.ITEM_CARD_HORIZONTAL_PADDING_DP),
                    dp(ShoppingStyle.ITEM_CARD_VERTICAL_PADDING_DP),
                    dp(ShoppingStyle.ITEM_CARD_HORIZONTAL_PADDING_DP),
                    dp(ShoppingStyle.ITEM_CARD_VERTICAL_PADDING_DP)
            )
            card.setBackgroundColor(altBackground())
            list.addView(card, matchWrap(bottom(ShoppingStyle.ITEM_CARD_GAP_DP)))

            val topLine: LinearLayout = row()
            topLine.setGravity(Gravity.CENTER_VERTICAL)
            card.addView(topLine)

            val cart: Button = button("☐", cardBg, accent)
            cart.setTextSize(22f)
            topLine.addView(cart, LinearLayout.LayoutParams(
                    dp(ShoppingStyle.CONTROL_HEIGHT_DP),
                    dp(ShoppingStyle.CONTROL_HEIGHT_DP)
            ))

            val name: EditText = input("Item", false)
            name.setText(item.name)
            val nameParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, dp(ShoppingStyle.CONTROL_HEIGHT_DP), 1f)
            nameParams.leftMargin = dp(ShoppingStyle.FIELD_GAP_DP)
            topLine.addView(name, nameParams)
            itemInputs.add(ItemInput(item, name, 0))

            val delete: Button = button("×", cardBg, accent)
            delete.setTextSize(22f)
            val deleteParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                    dp(ShoppingStyle.CONTROL_HEIGHT_DP),
                    dp(ShoppingStyle.CONTROL_HEIGHT_DP)
            )
            deleteParams.leftMargin = dp(ShoppingStyle.FIELD_GAP_DP)
            topLine.addView(delete, deleteParams)

            val fields: LinearLayout = row()
            card.addView(fields, matchWrap(top(ShoppingStyle.FIELD_GAP_DP)))

            val blankPrice: Boolean = item.price == 0.0 && item.name.trim { it <= ' ' }.isEmpty() && !item.inCart
            val price: EditText = input(if (blankPrice) "" else if (item.byWeight) "Price/" + weightUnit else "Price", true)
            if (blankPrice) {
                price.setText(priceStartText())
            } else {
                price.setText(money.format(item.price))
            }
            fields.addView(inputBox(price), weightWrap(1f))
            itemInputs.add(ItemInput(item, price, 1))

            val weight: EditText?
            if (item.byWeight) {
                weight = input("Weight", true)
                weight.setText(if (item.qty == 0.0) "" else trimNumber(item.qty))
                fields.addView(fieldBox(weightUnit, weight), weightWrap(1f, left(ShoppingStyle.FIELD_GAP_DP)))
                itemInputs.add(ItemInput(item, weight, 2))
            } else {
                if (item.qty < 1) {
                    item.qty = 1.0
                }
                weight = null
                fields.addView(quantityControl(item), weightWrap(1f, left(8)))
            }

            val mode: Button = button(if (item.byWeight) weightUnit else "Qty", panelSoft, panelIcon)
            mode.setTextSize(12f)
            val modeParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(dp(52), dp(ShoppingStyle.CONTROL_HEIGHT_DP))
            modeParams.leftMargin = dp(ShoppingStyle.FIELD_GAP_DP)
            fields.addView(mode, modeParams)

            price.addTextChangedListener(object : SimpleWatcher() {
                override fun afterTextChanged(s: Editable) {
                    if (quickCentsEntry) {
                        formatMoneyAsCents(price)
                    }
                }
            })

            val watcher: SimpleWatcher = object : SimpleWatcher() {
                override fun afterTextChanged(s: Editable) {
                    if (rebuilding) {
                        return
                    }
                    item.name = name.getText().toString()
                    item.price = parseMoney(price, 0.0)
                    if (item.byWeight && weight != null) {
                        item.qty = Math.max(0.0, parseDouble(weight, 0.0))
                    }
                    saveItems()
                    recalc()
                }
            }
            name.addTextChangedListener(watcher)
            price.addTextChangedListener(watcher)
            if (weight != null) {
                weight.addTextChangedListener(watcher)
            }

            name.setOnFocusChangeListener({ v, hasFocus ->
                if (hasFocus) {
                    scrollInputIntoView(name)
                }
             })
            price.setOnFocusChangeListener({ v, hasFocus ->
                if (hasFocus) {
                    scrollInputIntoView(price)
                } else if (!quickCentsEntry) {
                    formatPriceInput(price)
                }
             })
            if (weight != null) {
                weight.setOnFocusChangeListener({ v, hasFocus ->
                    if (hasFocus) {
                        scrollInputIntoView(weight)
                    }
                 })
            }
            mode.setOnClickListener({ v ->
                item.byWeight = !item.byWeight
                if (item.byWeight) {
                    // A measured amount is intentionally unknown until entered.
                    item.qty = 0.0
                } else if (item.qty == 0.0) {
                    item.qty = 1.0
                }
                saveItems()
                rebuildList()
                recalc()
             })
            delete.setOnClickListener({ v ->
                items.remove(item)
                saveItems()
                rebuildList()
                recalc()
             })
            cart.setOnClickListener(completeItem@{ v ->
                item.name = name.getText().toString().trim { it <= ' ' }
                item.price = parseMoney(price, 0.0)
                if (item.byWeight && weight != null) {
                    item.qty = Math.max(0.0, parseDouble(weight, 0.0))
                }
                if (!item.isReadyForCart(quickEntry)) {
                    if (item.name.isEmpty() && !quickEntry) {
                        name.setError("Add an item name")
                    } else if (item.price <= 0) {
                        price.setError("Add a price")
                    } else if (item.byWeight && weight != null) {
                        weight.setError("Add a weight")
                    }
                    return@completeItem
                }
                item.inCart = true
                saveItems()
                rebuildList()
                recalc()
             })

            if (item === focusAfterRebuild) {
                focusAfterRebuild = null
                val focus: EditText = if (quickEntry) price else name
                focus.postDelayed({ focusAndShowKeyboard(focus) }, 120)
            }
        }

        wireItemFieldNavigation(itemInputs)

        rebuilding = false
    }

    private fun rebuildReorderList() {
        list.removeAllViews()
        list.addView(label("Press and hold an item, then drag it to a new position.", 14, muted, false), matchWrap(bottom(8)))
        if (reorderItems == null || reorderItems!!.isEmpty()) {
            val empty: TextView = label("No items yet.", 16, muted, false)
            empty.setGravity(Gravity.CENTER)
            empty.setPadding(0, dp(24), 0, dp(24))
            list.addView(empty)
            return
        }
        wireReorderDragTarget(list, reorderItems!!)
        for (item in reorderItems!!) {
            addReorderRow(list, reorderItems!!, item)
        }
    }

    private fun addCompletedItemCard(item: ShoppingItem) {
        val card: LinearLayout = column()
        card.setPadding(dp(10), dp(7), dp(10), dp(7))
        card.setBackgroundColor(completedCardBg)
        list.addView(card, matchWrap(bottom(8)))

        val line: LinearLayout = row()
        line.setGravity(Gravity.CENTER_VERTICAL)
        card.addView(line)

        val cart: Button = button("☑", completedCardBg, accent)
        cart.setTextSize(21f)
        line.addView(cart, LinearLayout.LayoutParams(
                dp(ShoppingStyle.COMPLETED_CONTROL_SIZE_DP),
                dp(ShoppingStyle.COMPLETED_CONTROL_SIZE_DP)
        ))

        val summary: TextView = label(item.name, 16, text, true)
        line.addView(summary, weightWrap(1f))

        val details: TextView = label(completedItemDetails(item), 13, muted, false)
        card.addView(details, matchWrap(top(1)))

        cart.setOnClickListener({ v ->
            item.inCart = false
            saveItems()
            focusAfterRebuild = item
            rebuildList()
            recalc()
         })
    }

    private fun completedItemDetails(item: ShoppingItem): String {
        if (item.byWeight) {
            return trimNumber(item.qty) + " " + weightUnit + " × " + money.format(item.price) + "/" + weightUnit + " = " +
                    money.format(item.price * item.qty)
        }
        return trimNumber(item.qty) + " × " + money.format(item.price) + " = " +
                money.format(item.price * item.qty)
    }

    private fun wireItemFieldNavigation(itemInputs: ArrayList<ItemInput>) {
        for (i in 0 until itemInputs.size) {
            val current: ItemInput = itemInputs.get(i)
            current.input.setImeOptions(EditorInfo.IME_ACTION_NEXT)
            val index: Int = i
            current.input.setOnEditorActionListener(nextField@{ v, actionId, event ->
                if (!isEnterAction(actionId, event)) {
                    return@nextField false
                }
                if (current.field == 1) {
                    formatPriceInput(current.input)
                    // Finishing the price on the final item always starts the next item.
                    // The saved new-item focus preference decides whether that item opens
                    // at its name or price field; quick cents only changes price formatting.
                    if (isLastIncompleteItem(current.item)) {
                        val item: ShoppingItem = addItemAfter(current.item)
                        focusAfterRebuild = item
                        rebuildList()
                        recalc()
                        return@nextField true
                    }
                }
                if (current.field == 2 && isLastIncompleteItem(current.item)) {
                    val item: ShoppingItem = addItemAfter(current.item)
                    focusAfterRebuild = item
                    rebuildList()
                    recalc()
                    return@nextField true
                }
                if (index < itemInputs.size - 1) {
                    focusAndShowKeyboard(itemInputs.get(index + 1).input)
                    return@nextField true
                }
                return@nextField true
             })
        }
    }

    private fun isLastIncompleteItem(candidate: ShoppingItem): Boolean {
        var last: ShoppingItem? = null
        for (item in items) {
            if (!item.inCart) {
                last = item
            }
        }
        return candidate === last
    }

    private fun addItemAfter(after: ShoppingItem): ShoppingItem {
        sortItems()
        val item: ShoppingItem = ShoppingItem()
        item.qty = 1.0
        val index: Int = items.indexOf(after)
        if (index < 0 || index >= items.size - 1) {
            items.add(item)
        } else {
            items.add(index + 1, item)
        }
        for (i in 0 until items.size) {
            items.get(i).order = (i + 1) * 10
        }
        saveItems()
        return item
    }

    private fun isEnterAction(actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
            return true
        }
        return event != null
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && event.getAction() == KeyEvent.ACTION_UP
    }

    private fun recalc() {
        val totals: CartTotals = CartTotals.calculate(items, taxRate)
        subtotalView.setText(money.format(totals.subtotal))
        taxView.setText(money.format(totals.tax))
        totalView.setText(money.format(totals.total))
        remainingView.setText(if (budget > 0) money.format(budget - totals.total) else "--")
        remainingView.setTextColor(if (budget > 0 && budget - totals.total < 0) danger else text)
    }

    private fun metric(parent: LinearLayout, label: String): TextView {
        val box: LinearLayout = column()
        val value: TextView = label("\$0.00", 16, text, true)
        box.addView(label(label, 12, muted, false))
        box.addView(value)
        parent.addView(box, weightWrap(1f))
        return value
    }

    private fun clearList() {
        for (item in items) {
            item.price = 0.0
            item.qty = 1.0
            item.inCart = false
        }
        saveItems()
        rebuildList()
        recalc()
    }

    private fun deleteList() {
        items.clear()
        saveItems()
        rebuildList()
        recalc()
    }

    private fun rebuildUi() {
        buildUi()
        rebuildList()
        recalc()
    }

    private fun field(label: String, input: EditText): LinearLayout {
        val wrap: LinearLayout = column()
        wrap.addView(label(label, 12, muted, false))
        val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(ShoppingStyle.CONTROL_HEIGHT_DP)
        )
        params.topMargin = dp(4)
        wrap.addView(input, params)
        return wrap
    }

    private fun inputBox(input: EditText): LinearLayout {
        val wrap: LinearLayout = column()
        wrap.addView(input, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(ShoppingStyle.CONTROL_HEIGHT_DP)
        ))
        return wrap
    }

    private fun fieldBox(prefixText: String, input: EditText): LinearLayout {
        val wrap: LinearLayout = row()
        wrap.setGravity(Gravity.CENTER_VERTICAL)
        wrap.setPadding(dp(ShoppingStyle.INPUT_HORIZONTAL_PADDING_DP), 0, dp(ShoppingStyle.INPUT_HORIZONTAL_PADDING_DP), 0)
        wrap.setMinimumHeight(dp(ShoppingStyle.CONTROL_HEIGHT_DP))
        wrap.setBackgroundColor(inputBg)

        val prefix: TextView = label(prefixText + ":", 15, text, false)
        wrap.addView(prefix)

        input.setBackgroundColor(Color.TRANSPARENT)
        input.setPadding(dp(6), 0, 0, 0)
        wrap.addView(input, LinearLayout.LayoutParams(
                0,
                dp(ShoppingStyle.CONTROL_HEIGHT_DP), 1f))
        return wrap
    }

    private fun quantityControl(item: ShoppingItem): LinearLayout {
        val wrap: LinearLayout = row()
        wrap.setGravity(Gravity.CENTER_VERTICAL)
        wrap.setPadding(dp(3), 0, dp(3), 0)
        wrap.setMinimumHeight(dp(ShoppingStyle.CONTROL_HEIGHT_DP))
        wrap.setBackgroundColor(inputBg)

        val minus: Button = button("−", panelSoft, panelIcon)
        minus.setTextSize(20f)
        wrap.addView(minus, LinearLayout.LayoutParams(dp(30), dp(ShoppingStyle.CONTROL_HEIGHT_DP)))

        val value: TextView = label("Qty\n" + trimNumber(item.qty), 13, text, true)
        value.setGravity(Gravity.CENTER)
        value.setLines(2)
        wrap.addView(value, LinearLayout.LayoutParams(0, dp(ShoppingStyle.CONTROL_HEIGHT_DP), 1f))

        val plus: Button = button("+", panelSoft, panelIcon)
        plus.setTextSize(20f)
        wrap.addView(plus, LinearLayout.LayoutParams(dp(30), dp(ShoppingStyle.CONTROL_HEIGHT_DP)))

        minus.setEnabled(item.qty > 1)
        minus.setTextColor(if (minus.isEnabled()) panelIcon else muted)
        minus.setOnClickListener({ v ->
            if (item.qty > 1) {
                item.qty--
                value.setText("Qty\n" + trimNumber(item.qty))
                minus.setEnabled(item.qty > 1)
                minus.setTextColor(if (minus.isEnabled()) panelIcon else muted)
                saveItems()
                recalc()
            }
         })
        plus.setOnClickListener({ v ->
            item.qty++
            value.setText("Qty\n" + trimNumber(item.qty))
            minus.setEnabled(true)
            minus.setTextColor(panelIcon)
            saveItems()
            recalc()
         })
        return wrap
    }

    private fun input(hint: String, number: Boolean): EditText {
        val input: EditText = EditText(this)
        input.setHint(hint)
        input.setHintTextColor(ShoppingStyle.INPUT_HINT)
        input.setTextColor(text)
        input.setTextSize(16f)
        input.setSingleLine(true)
        input.setGravity(Gravity.CENTER_VERTICAL)
        input.setIncludeFontPadding(false)
        input.setPadding(dp(ShoppingStyle.INPUT_HORIZONTAL_PADDING_DP), 0, dp(ShoppingStyle.INPUT_HORIZONTAL_PADDING_DP), 0)
        input.setMinHeight(0)
        input.setMinimumHeight(0)
        input.setBackgroundColor(inputBg)
        if (number) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        }
        return input
    }

    private fun button(textValue: String, background: Int, foreground: Int): Button {
        val button: Button = Button(this)
        button.setText(textValue)
        button.setTextColor(foreground)
        button.setTextSize(14f)
        button.setAllCaps(false)
        button.setTypeface(Typeface.DEFAULT_BOLD)
        button.setGravity(Gravity.CENTER)
        button.setIncludeFontPadding(false)
        button.setPadding(0, 0, 0, 0)
        button.setMinHeight(0)
        button.setMinimumHeight(0)
        button.setMinWidth(0)
        button.setMinimumWidth(0)
        button.setBackgroundColor(background)
        return button
    }

    private fun focusAndShowKeyboard(input: EditText) {
        input.requestFocus()
        input.setSelection(input.getText().length)
        scrollInputIntoView(input)
        val imm: InputMethodManager? = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun scrollInputIntoView(input: EditText) {
        if (::scroll.isInitialized) {
            scroll.postDelayed({
                val rect: Rect = Rect()
                val itemCard: View = itemCardFor(input)
                itemCard.getDrawingRect(rect)
                scroll.offsetDescendantRectToMyCoords(itemCard, rect)
                val padding: Int = dp(12)
                val viewportHeight: Int = scroll.getHeight()
                val visibleHeight: Int = viewportHeight - padding * 2
                val targetTop: Int = if (rect.height() <= visibleHeight) rect.top - padding else rect.top
                scroll.smoothScrollTo(0, Math.max(0, targetTop))
             }, 260)
        }
    }

    private fun itemCardFor(input: View): View {
        var current: View = input
        while (current.getParent() is View && current.getParent() !== list) {
            current = current.getParent() as View
        }
        return current
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean): TextView {
        val label: TextView = TextView(this)
        label.setText(value)
        label.setTextColor(color)
        label.setTextSize(size.toFloat())
        label.setIncludeFontPadding(true)
        if (bold) {
            label.setTypeface(Typeface.DEFAULT_BOLD)
        }
        return label
    }

    private fun row(): LinearLayout {
        val row: LinearLayout = LinearLayout(this)
        row.setOrientation(LinearLayout.HORIZONTAL)
        return row
    }

    private fun column(): LinearLayout {
        val column: LinearLayout = LinearLayout(this)
        column.setOrientation(LinearLayout.VERTICAL)
        return column
    }

    private fun matchWrap(margins: LinearLayout.LayoutParams): LinearLayout.LayoutParams {
        val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(margins.leftMargin, margins.topMargin, margins.rightMargin, margins.bottomMargin)
        return params
    }

    private fun dialogNameInputParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        )
    }

    private fun weightWrap(weight: Float): LinearLayout.LayoutParams {
        return weightWrap(weight, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun weightWrap(weight: Float, margins: LinearLayout.LayoutParams): LinearLayout.LayoutParams {
        val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        params.setMargins(margins.leftMargin, margins.topMargin, margins.rightMargin, margins.bottomMargin)
        return params
    }

    private fun top(value: Int): LinearLayout.LayoutParams {
        val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, 0)
        params.topMargin = dp(value)
        return params
    }

    private fun bottom(value: Int): LinearLayout.LayoutParams {
        val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, 0)
        params.bottomMargin = dp(value)
        return params
    }

    private fun left(value: Int): LinearLayout.LayoutParams {
        val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, 0)
        params.leftMargin = dp(value)
        return params
    }

    private fun dp(value: Int): Int {
        return (value * getResources().getDisplayMetrics().density + 0.5f).toInt()
    }

    private fun altBackground(): Int {
        return cardBg
    }

    private fun contrastFor(color: Int): Int {
        val brightness: Double = (Color.red(color) * 0.299) + (Color.green(color) * 0.587) + (Color.blue(color) * 0.114)
        return if (brightness > 150) Color.rgb(7, 20, 28) else text
    }

    private fun sortItems() {
        Collections.sort(items, compareItems@{ first, second ->
            if (first.inCart != second.inCart) {
                return@compareItems if (first.inCart) 1 else -1
            }
            return@compareItems Integer.compare(first.order, second.order)
         })
    }

    private fun applyListView(rawList: String) {
        sortItems()
        val originalItems: ArrayList<ShoppingItem> = ArrayList(items)
        val updatedItems: ArrayList<ShoppingItem> = ArrayList()
        val used: BooleanArray = BooleanArray(originalItems.size)
        val names: ArrayList<String> = cleanListNames(rawList)

        for (i in 0 until names.size) {
            val name: String = names.get(i)
            var item: ShoppingItem? = findUnusedItemByName(originalItems, used, name)
            if (item == null && i < originalItems.size && !used[i]) {
                item = originalItems.get(i)
                used[i] = true
            }
            if (item == null) {
                item = ShoppingItem()
                item.qty = 1.0
            }
            item.name = name
            item.order = (updatedItems.size + 1) * 10
            updatedItems.add(item)
        }

        items.clear()
        items.addAll(updatedItems)
        saveItems()
        rebuildList()
        recalc()
    }

    private fun findUnusedItemByName(originalItems: ArrayList<ShoppingItem>, used: BooleanArray, name: String): ShoppingItem? {
        for (i in 0 until originalItems.size) {
            val item: ShoppingItem = originalItems.get(i)
            if (!used[i] && item.name.equals(name)) {
                used[i] = true
                return item
            }
        }
        return null
    }

    private fun currentItemNameList(): String {
        sortItems()
        val builder: StringBuilder = StringBuilder()
        for (item in items) {
            if (builder.length > 0) {
                builder.append('\n')
            }
            builder.append(item.name)
        }
        return builder.toString()
    }

    private fun joinListNames(names: ArrayList<String>): String {
        val builder: StringBuilder = StringBuilder()
        for (name in names) {
            if (builder.length > 0) {
                builder.append('\n')
            }
            builder.append(name)
        }
        return builder.toString()
    }

    private fun cleanListNames(rawList: String): ArrayList<String> {
        val names: ArrayList<String> = ArrayList()
        val lines: List<String> = rawList.replace('\r', '\n').split("\n")
        for (line in lines) {
            val name: String = cleanListItemName(line)
            if (!name.isEmpty()) {
                names.add(name)
            }
        }
        return names
    }

    private fun cleanListItemName(rawName: String): String {
        var name: String = rawName.trim { it <= ' ' }
        name = name.replace(Regex("^[\\u2022\\u2023\\u25E6\\u2043\\u2219*+-]\\s+"), "")
        name = name.replace(Regex("^\\d+[.)]\\s+"), "")
        name = name.replace(Regex("^\\[[ xX]\\]\\s+"), "")
        return name.trim { it <= ' ' }.replace(Regex("\\s+"), " ")
    }

    private fun nextOrder(): Int {
        var max: Int = 0
        for (item in items) {
            max = Math.max(max, item.order)
        }
        return max + 10
    }

    private fun multilineListInput(): EditText {
        val input: EditText = EditText(this)
        input.setHint("One item per line")
        input.setHintTextColor(ShoppingStyle.INPUT_HINT)
        input.setTextColor(text)
        input.setTextSize(16f)
        input.setMinLines(8)
        input.setMaxLines(14)
        input.setGravity(Gravity.TOP or Gravity.START)
        input.setPadding(dp(12), dp(10), dp(12), dp(10))
        input.setBackgroundColor(inputBg)
        input.setSingleLine(false)
        input.setInputType(InputType.TYPE_CLASS_TEXT
                or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        return input
    }

    private fun saveSettings() {
        store.saveSettings(taxRate, budget)
    }

    private fun saveItems() {
        store.saveItems(items)
    }

    private fun load() {
        taxRate = store.taxRate()
        budget = store.budget()
        money = store.currencyFormat()
        quickCentsEntry = store.quickCentsEntry()
        quickEntry = store.quickEntry()
        weightUnit = store.weightUnit()
        store.loadItems(items)
    }

    private fun parseDouble(input: EditText, fallback: Double): Double {
        try {
            val raw: String = input.getText().toString().trim { it <= ' ' }
            if (raw.isEmpty()) {
                return fallback
            }
            return java.lang.Double.parseDouble(raw)
        } catch (ex: NumberFormatException) {
            return fallback
        }
    }

    private fun parseMoney(input: EditText, fallback: Double): Double {
        val raw: String = input.getText().toString()
        return if (quickCentsEntry) money.parseQuick(raw, fallback) else money.parseDirect(raw, fallback)
    }

    private fun formatPriceInput(input: EditText) {
        if (priceHasNoAmount(input)) {
            if (!priceStartText().equals(input.getText().toString())) {
                input.setText(priceStartText())
            }
            return
        }
        val value: Double = parseMoney(input, 0.0)
        val formatted: String = money.format(value)
        if (!formatted.equals(input.getText().toString())) {
            input.setText(formatted)
            input.setSelection(input.getText().length)
        }
    }

    private fun priceStartText(): String {
        return money.symbol
    }

    private fun priceHasNoAmount(input: EditText): Boolean {
        val raw: String = input.getText().toString().replace(money.symbol, "").trim { it <= ' ' }
        return raw.replace(Regex("\\D"), "").isEmpty()
    }

    private fun formatMoneyAsCents(input: EditText) {
        if (formattingPrice) {
            return
        }
        val digits: String = input.getText().toString().replace(Regex("\\D"), "")
        var cents: Long = 0
        try {
            if (!digits.isEmpty()) {
                cents = java.lang.Long.parseLong(digits)
            }
        } catch (ignored: NumberFormatException) {
            // Keep the input usable if an unusually long number is pasted.
        }
        val formatted: String = if (digits.isEmpty()) priceStartText() else money.format(cents / 100.0)
        if (!formatted.equals(input.getText().toString())) {
            formattingPrice = true
            input.setText(formatted)
            input.setSelection(formatted.length)
            formattingPrice = false
        }
    }

    private fun trimNumber(value: Double): String {
        if (value == Math.rint(value)) {
            return value.toLong().toString()
        }
        return value.toString()
    }

    private class ItemInput(val item: ShoppingItem, val input: EditText, val field: Int)

    private class ReorderDrag(val row: View, val item: ShoppingItem) {
        lateinit var placeholder: View
        var dropped = false
        var finished = false
    }

    private enum class SavedListAction {
        SAVE,
        LOAD,
        EDIT
    }

    private abstract class SimpleWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        }
    }
}
