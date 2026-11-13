private fun isStableValue(newValue: String, previousValues: MutableList<String>): Boolean {
    if (previousValues.size < 3) {
        previousValues.add(newValue)
        return false
    }

    previousValues.add(newValue)
    if (previousValues.size > 5) previousValues.removeAt(0)

    val unique = previousValues.toSet()
    return unique.size == 1
}