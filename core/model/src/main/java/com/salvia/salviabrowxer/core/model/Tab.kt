package com.salvia.salviabrowxer.core.model

import java.util.UUID

data class Tab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",
    val isPrivate: Boolean = false,
    val isDesktopMode: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastVisited: Long = System.currentTimeMillis(),
    val favicon: String? = null,
    val position: Int = 0
)