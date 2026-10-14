package com.salvia.salviabrowxer.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The destination table, including the four ways a chosen folder stops working.
 *
 * The rule that matters: a folder that cannot be written to never costs the user a download. Every
 * failure path ends with the file staying in the app's own storage, and the caller is told why.
 */
class DestinationPolicyTest {

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3ADownload"

    @Test
    fun `internal mode keeps the file private, wherever the tree is`() {
        assertEquals(DestinationPolicy.Plan.KeepPrivate, DestinationPolicy.decide("internal", tree, treeReachable = true))
        assertEquals(DestinationPolicy.Plan.KeepPrivate, DestinationPolicy.decide("internal", null, treeReachable = false))
    }

    @Test
    fun `tree mode with a reachable folder moves the file there`() {
        val plan = DestinationPolicy.decide("tree", tree, treeReachable = true)

        assertTrue(plan is DestinationPolicy.Plan.MoveToTree)
        assertEquals(tree, (plan as DestinationPolicy.Plan.MoveToTree).treeUri)
    }

    @Test
    fun `tree mode without a folder chosen keeps the file and says why`() {
        assertEquals(
            DestinationPolicy.Plan.KeptPrivate(DestinationPolicy.Reason.NO_TREE_CHOSEN),
            DestinationPolicy.decide("tree", null, treeReachable = false)
        )
        assertEquals(
            DestinationPolicy.Plan.KeptPrivate(DestinationPolicy.Reason.NO_TREE_CHOSEN),
            DestinationPolicy.decide("tree", "   ", treeReachable = true)
        )
    }

    @Test
    fun `a folder that vanished, a card that was removed or a revoked grant all keep the file private`() {
        // All three are the same thing to the table: the tree does not resolve right now.
        assertEquals(
            DestinationPolicy.Plan.KeptPrivate(DestinationPolicy.Reason.TREE_UNREACHABLE),
            DestinationPolicy.decide("tree", tree, treeReachable = false)
        )
    }

    @Test
    fun `an unknown mode is never treated as the chosen folder`() {
        // A stored value from a future version, or a corrupted preference: private storage is the
        // only safe reading of "I do not know where this should go".
        assertEquals(DestinationPolicy.Plan.KeepPrivate, DestinationPolicy.decide("", tree, treeReachable = true))
        assertEquals(DestinationPolicy.Plan.KeepPrivate, DestinationPolicy.decide("TREE", tree, treeReachable = true))
    }
}
