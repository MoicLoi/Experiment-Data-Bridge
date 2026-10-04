package me.moiclathy.edb.core.config.merge

data class MergeResult(

    val merged: Map<String, Any?>,

    val conflicts: List<MergeConflict>
) {

    val hasConflicts: Boolean
        get() = conflicts.isNotEmpty()
}