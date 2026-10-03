package io.github.buildsbyben.shoppinglistcalc

import android.app.AlertDialog
import android.os.Looper
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowPopupMenu

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivityRegressionTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var store: ShoppingListStore

    @Before fun setUp() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("shopping_calc", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = ShoppingListStore(prefs)
    }

    @After fun tearDown() {
        if (::controller.isInitialized) controller.pause().stop().destroy()
    }

    private fun launch() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
    }

    private fun seed(vararg names: String) {
        store.saveItems(names.mapIndexed { i, name -> ShoppingItem().apply {
            this.name = name; order = (i + 1) * 10; price = (i + 1).toDouble()
        } })
    }

    private fun views(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun screen() = views(activity.window.decorView)
    private fun button(text: String, root: View = activity.window.decorView): Button =
        views(root).filterIsInstance<Button>().first { it.text.toString() == text }
    private fun inputs(root: View = activity.window.decorView) = views(root).filterIsInstance<EditText>()
    private fun loaded(): ArrayList<ShoppingItem> = arrayListOf<ShoppingItem>().also { store.loadItems(it) }
    private fun dialog(): AlertDialog {
        // Dialog.onShow installs custom button listeners on the main queue.
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowAlertDialog.getLatestAlertDialog()
    }
    private fun chooseAction(title: String) {
        button("⋮").performClick()
        val popup = ShadowPopupMenu.getLatestPopupMenu()
        val item = (0 until popup.menu.size()).map { popup.menu.getItem(it) }.first { it.title == title }
        shadowOf(popup).onMenuItemClickListener.onMenuItemClick(item)
    }
    private fun chooseDialogItem(index: Int) { shadowOf(dialog()).clickOnItem(index) }
    private fun clickDialogButton(which: Int) {
        dialog().getButton(which).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun positive() { clickDialogButton(AlertDialog.BUTTON_POSITIVE) }

    @Test fun addingEditingAndReopeningPreservesCartAndTotals() {
        store.saveSettings(7.5, 10.0)
        launch()
        assertTrue(screen().filterIsInstance<TextView>().any { it.text.toString() == "No items yet." })
        button("+ Item").performClick()
        inputs()[0].setText("Milk")
        inputs()[1].setText("3.25")
        button("+").performClick()
        assertEquals(2.0, loaded().single().qty, 0.0)
        assertTrue(screen().filterIsInstance<TextView>().any { it.text.toString() == "$6.99" })
        controller.recreate()
        activity = controller.get()
        assertEquals("Milk", inputs()[0].text.toString())
        assertEquals(3.25, loaded().single().price, 0.0)
    }

    @Test fun duplicateItemEditAndDeleteAffectOnlySelectedRow() {
        seed("Milk", "Milk")
        launch()
        inputs()[0].setText("First")
        assertEquals(listOf("First", "Milk"), loaded().map { it.name })
        button("×").performClick()
        assertEquals("Milk", loaded().single().name)
        assertEquals(2.0, loaded().single().price, 0.0)
    }

    @Test fun cartValidationCompletionAndRestoreKeepValues() {
        launch()
        button("+ Item").performClick()
        button("☐").performClick()
        assertNotNull(inputs()[0].error)
        assertFalse(loaded().single().inCart)
        inputs()[0].setText("Milk")
        inputs()[1].setText("2.5")
        button("☐").performClick()
        assertTrue(loaded().single().inCart)
        button("☑").performClick()
        assertFalse(loaded().single().inCart)
        assertEquals(2.5, loaded().single().price, 0.0)
    }

    @Test fun weightedItemRequiresPositiveWeightAndRecalculates() {
        seed("Apples")
        launch()
        button("Qty").performClick()
        assertTrue(loaded().single().byWeight)
        assertEquals(0.0, loaded().single().qty, 0.0)
        button("☐").performClick()
        assertNotNull(inputs()[2].error)
        inputs()[1].setText("2.40")
        inputs()[2].setText("1.25")
        assertTrue(screen().filterIsInstance<TextView>().any { it.text.toString() == "$3.00" })
        button("☐").performClick()
        assertTrue(loaded().single().inCart)
    }

    @Test fun priceImeNextAddsNewItemAndQuickEntryAcceptsUnnamedItem() {
        store.saveQuickEntry(true)
        store.saveQuickCentsEntry(true)
        launch()
        button("+ Item").performClick()
        inputs()[1].setText("1234")
        assertEquals(12.34, loaded().single().price, 0.0)
        inputs()[1].onEditorAction(EditorInfo.IME_ACTION_NEXT)
        assertEquals(2, loaded().size)
        button("☐").performClick()
        assertTrue(loaded().first().inCart)
    }

    @Test fun editListDialogNormalizesPastedNamesAndPreservesMatchedValues() {
        seed("Milk", "Bread", "Milk")
        launch()
        chooseAction("Edit List")
        inputs(dialog().window!!.decorView).single().setText("- Bread\n1. Milk\n[x] Milk\n  New  item ")
        positive()
        assertEquals(listOf("Bread", "Milk", "Milk", "New item"), loaded().map { it.name })
        assertEquals(listOf(2.0, 1.0, 3.0, 0.0), loaded().map { it.price })
    }

    @Test fun clearAndDeleteDialogsHaveDistinctEffectsAndCancelIsSafe() {
        seed("Milk")
        store.saveSavedLists(listOf(SavedList("Weekly", arrayListOf("Bread"))))
        launch()
        chooseAction("Clear / Delete list")
        clickDialogButton(AlertDialog.BUTTON_NEGATIVE)
        assertEquals(1.0, loaded().single().price, 0.0)
        chooseAction("Clear / Delete list")
        clickDialogButton(AlertDialog.BUTTON_NEUTRAL)
        assertEquals("Milk", loaded().single().name)
        assertEquals(0.0, loaded().single().price, 0.0)
        chooseAction("Clear / Delete list")
        positive()
        assertTrue(loaded().isEmpty())
        assertEquals("Weekly", store.savedLists().single().name)
    }

    @Test fun savedListSaveAddReplaceAndDeleteUseDialogsWithoutLosingCart() {
        seed("Milk", "Bread")
        launch()
        chooseAction("Saved lists"); chooseDialogItem(0)
        inputs(dialog().window!!.decorView).single().setText("Weekly")
        positive()
        assertEquals(listOf("Milk", "Bread"), store.savedLists().single().itemNames)
        chooseAction("Saved lists"); chooseDialogItem(1); chooseDialogItem(0)
        clickDialogButton(AlertDialog.BUTTON_NEUTRAL)
        assertEquals(listOf("Milk", "Bread", "Milk", "Bread"), loaded().map { it.name })
        chooseAction("Saved lists"); chooseDialogItem(1); chooseDialogItem(0); positive()
        assertEquals(listOf("Milk", "Bread"), loaded().map { it.name })
        assertEquals(listOf(0.0, 0.0), loaded().map { it.price })
        chooseAction("Saved lists"); chooseDialogItem(2); chooseDialogItem(0)
        clickDialogButton(AlertDialog.BUTTON_NEUTRAL); positive()
        assertTrue(store.savedLists().isEmpty())
        assertEquals(2, loaded().size)
    }

    @Test fun resumeReloadsSettingsAndSettingsMenuLaunchesActivity() {
        seed("Milk")
        launch()
        chooseAction("Settings")
        assertEquals(SettingsActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
        controller.pause()
        store.saveCurrencyFormat(CurrencyFormat("EUR", true, ',', '\u0000', 2))
        store.saveSettings(10.0, 5.0)
        controller.resume()
        assertTrue(screen().filterIsInstance<TextView>().any { it.text.toString() == "1,10 EUR" })
    }

    @Test fun reorderSaveAndCancelPreserveDuplicateIdentity() {
        seed("Milk", "Milk", "Bread")
        launch()
        chooseAction("Reorder items")
        // Gesture delivery is device-tested; exercise the exact staged collection
        // that the drag listener mutates, then the real Save/Cancel buttons.
        val field = MainActivity::class.java.getDeclaredField("reorderItems").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val staged = field.get(activity) as ArrayList<ShoppingItem>
        java.util.Collections.swap(staged, 0, 2)
        button("Cancel").performClick()
        assertEquals(listOf(1.0, 2.0, 3.0), loaded().map { it.price })
        chooseAction("Reorder items")
        @Suppress("UNCHECKED_CAST")
        val next = field.get(activity) as ArrayList<ShoppingItem>
        java.util.Collections.swap(next, 0, 1)
        button("Save").performClick()
        assertEquals(listOf(2.0, 1.0, 3.0), loaded().map { it.price })
        assertEquals(listOf(10, 20, 30), loaded().map { it.order })
    }
}
