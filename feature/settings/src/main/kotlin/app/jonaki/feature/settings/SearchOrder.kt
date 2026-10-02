package app.jonaki.feature.settings

/**
 * The search order after [key] moves by [offset] places (-1 up, +1 down).
 * The caller stores the result; SettingsActions.onSearchServiceMove reports the move.
 */
fun moveInOrder(order: List<String>, key: String, offset: Int): List<String> {
    val from = order.indexOf(key)
    val to = from + offset
    if (from == -1 || to !in order.indices) {
        return order
    }
    val reordered = order.toMutableList()
    reordered.removeAt(from)
    reordered.add(to, key)
    return reordered
}
