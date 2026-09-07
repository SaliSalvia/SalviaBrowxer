package com.salvia.salviabrowxer.ui.utils

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.draggable(
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit = {}
) = composed {
    var isDragging by remember { mutableStateOf(false) }
    pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                isDragging = true
                onDragStart()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                onDrag(dragAmount)
            },
            onDragEnd = {
                isDragging = false
                onDragEnd()
            }
        )
    }
}
