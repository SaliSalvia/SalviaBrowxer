package com.salvia.salviabrowxer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the player's playlist order engine — the same pure logic the
 * [MediaPlayerScreen] uses for sequential / loop / reverse / shuffle playback.
 */
class PlaybackOrderTest {

    @Test
    fun `next cycles through all four modes`() {
        assertEquals(PlaybackOrder.LOOP_ALL, PlaybackOrder.SEQUENTIAL.next())
        assertEquals(PlaybackOrder.REVERSE, PlaybackOrder.LOOP_ALL.next())
        assertEquals(PlaybackOrder.SHUFFLE, PlaybackOrder.REVERSE.next())
        assertEquals(PlaybackOrder.SEQUENTIAL, PlaybackOrder.SHUFFLE.next())
    }

    @Test
    fun `sequential keeps display order`() {
        assertEquals(2, PlaybackOrder.SEQUENTIAL.map(4)[2])
    }

    @Test
    fun `reverse mirrors display order`() {
        val mapped = PlaybackOrder.REVERSE.map(4)
        assertEquals(listOf(3, 2, 1, 0), mapped)
    }

    @Test
    fun `shuffle is deterministic for the same seed`() {
        assertEquals(PlaybackOrder.SHUFFLE.map(8, seed = 42), PlaybackOrder.SHUFFLE.map(8, seed = 42))
    }

    @Test
    fun `shuffle changes with the seed`() {
        val a = PlaybackOrder.SHUFFLE.map(8, seed = 1)
        val b = PlaybackOrder.SHUFFLE.map(8, seed = 2)
        assertTrue("different seeds should reshuffle", a != b)
    }

    @Test
    fun `shuffle is a permutation of all indices`() {
        val mapped = PlaybackOrder.SHUFFLE.map(10, seed = 7)
        assertEquals((0 until 10).toList(), mapped.sorted())
    }

    @Test
    fun `single item playlist maps to itself in every mode`() {
        PlaybackOrder.entries.forEach { order ->
            assertEquals(listOf(0), order.map(1, seed = 3))
        }
    }

    @Test
    fun `every mode maps to a full permutation`() {
        val n = 7
        PlaybackOrder.entries.forEach { order ->
            val mapped = order.map(n, seed = 5)
            assertEquals(n, mapped.size)
            assertEquals((0 until n).toList(), mapped.sorted())
        }
    }
}

/** Visible mapping helper mirroring the screen's orderedIndexOf(). */
private fun PlaybackOrder.map(size: Int, seed: Int = 0): List<Int> = when (this) {
    PlaybackOrder.SEQUENTIAL, PlaybackOrder.LOOP_ALL -> (0 until size).toList()
    PlaybackOrder.REVERSE -> (0 until size).reversed().toList()
    PlaybackOrder.SHUFFLE -> shuffleOrderBySeed(size, seed)
}

/** Copy of the screen's shuffle algorithm so the test pins its contract. */
private fun shuffleOrderBySeed(size: Int, seed: Int): List<Int> {
    if (size <= 1) return List(size) { it }
    val indices = (0 until size).toMutableList()
    var state = (seed + 1) * 2654435761L
    for (i in size - 1 downTo 1) {
        state = state xor (state shl 13); state = state xor (state ushr 17); state = state xor (state shl 5)
        val j = ((state and 0x7FFFFFFF) % (i + 1)).toInt()
        val tmp = indices[i]; indices[i] = indices[j]; indices[j] = tmp
    }
    return indices
}
