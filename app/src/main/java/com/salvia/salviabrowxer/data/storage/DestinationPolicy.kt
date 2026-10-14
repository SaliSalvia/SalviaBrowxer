package com.salvia.salviabrowxer.data.storage

/**
 * Where one finished download goes, decided from settings and from what the folder can still do.
 *
 * The user picks a folder with the system picker, and the app then has exactly one way to write
 * there: a document in a tree it holds a persisted grant for. That permission can stop existing —
 * the card is removed, the folder is deleted in Files, the grant is revoked in the system settings —
 * and a download that has just finished must not be lost because of it. So the decision is a small,
 * pure table with one rule above all: **when in doubt, keep the file where it is.** Losing a finished
 * download to respect a preference is never the answer; the app falls back to its own storage and
 * says so.
 */
object DestinationPolicy {

    /** What to do with the file the transfer has just finished producing. */
    sealed interface Plan {
        /** Leave the file in the app's own directory. */
        data object KeepPrivate : Plan

        /** Move it into [treeUri]. The move only counts once the document exists in full. */
        data class MoveToTree(val treeUri: String) : Plan

        /** Wanted to move it, could not: the file stays private and [reason] is shown. */
        data class KeptPrivate(val reason: Reason) : Plan
    }

    enum class Reason {
        /** The mode says "chosen folder" but no folder has been chosen yet. */
        NO_TREE_CHOSEN,

        /** The folder no longer resolves: removed card, deleted folder, or a revoked grant. */
        TREE_UNREACHABLE
    }

    /**
     * @param mode [com.salvia.salviabrowxer.data.datastore.SettingsDataStore.DESTINATION_INTERNAL] or
     *   `DESTINATION_TREE`
     * @param treeUri the remembered tree, or null when the user never chose one
     * @param treeReachable whether that tree still resolves *right now*
     */
    fun decide(mode: String, treeUri: String?, treeReachable: Boolean): Plan {
        if (mode != MODE_TREE) return Plan.KeepPrivate
        val tree = treeUri?.takeIf { it.isNotBlank() } ?: return Plan.KeptPrivate(Reason.NO_TREE_CHOSEN)
        if (!treeReachable) return Plan.KeptPrivate(Reason.TREE_UNREACHABLE)
        return Plan.MoveToTree(tree)
    }

    const val MODE_TREE = "tree"
}
