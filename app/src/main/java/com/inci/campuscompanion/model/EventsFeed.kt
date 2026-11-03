package com.inci.campuscompanion.model

data class EventsFeed(
    val events: List<CampusEvent>,
    val canLoadMore: Boolean
)
