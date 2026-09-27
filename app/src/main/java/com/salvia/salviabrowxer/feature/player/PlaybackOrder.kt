package com.salvia.salviabrowxer.feature.player

import androidx.annotation.StringRes
import com.salvia.salviabrowxer.R

/**
 * Playback order for the built-in player — InShot-style queue control.
 *
 * @property labelRes string resource shown on the order chip
 */
enum class PlaybackOrder(@StringRes val labelRes: Int) {
    /** In order, stops at the end of the playlist. */
    SEQUENTIAL(R.string.player_order_sequential),

    /** Cycles the playlist forever. */
    LOOP_ALL(R.string.player_order_loop),

    /** Plays the playlist backwards. */
    REVERSE(R.string.player_order_reverse),

    /** Shuffled playback (reshuffles each pass). */
    SHUFFLE(R.string.player_order_shuffle);

    /** Next mode in the tap cycle on the order chip. */
    fun next(): PlaybackOrder = entries[(ordinal + 1) % entries.size]
}
