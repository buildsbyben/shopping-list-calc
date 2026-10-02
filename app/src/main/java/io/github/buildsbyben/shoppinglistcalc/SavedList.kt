package io.github.buildsbyben.shoppinglistcalc

internal class SavedList(@JvmField val name: String, itemNames: ArrayList<String>) {
    @JvmField val itemNames: ArrayList<String> = ArrayList(itemNames)
}
