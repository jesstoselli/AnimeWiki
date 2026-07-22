package com.example.animewiki.domain.model

data class AnimeBrowseCriteria private constructor(
    val query: String,
    val filters: AnimeFilters
) {
    val isDefault: Boolean
        get() = query.isBlank() && filters.isEmpty

    companion object {
        fun create(
            query: String = "",
            filters: AnimeFilters = AnimeFilters()
        ): AnimeBrowseCriteria = AnimeBrowseCriteria(
            query = query.trim(),
            filters = filters
        )
    }
}
