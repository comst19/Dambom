package com.comst19.dambom.feature.library

internal data class LibrarySelectionState(
    val isActive: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    fun toggle(id: String): LibrarySelectionState =
        copy(
            isActive = true,
            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id,
        )

    fun selectAll(ids: Collection<String>): LibrarySelectionState = copy(isActive = true, selectedIds = ids.toSet())

    fun clear(): LibrarySelectionState = LibrarySelectionState()
}
