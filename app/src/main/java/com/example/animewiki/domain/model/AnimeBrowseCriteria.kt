package com.example.animewiki.domain.model

class AnimeBrowseCriteria private constructor(
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

    override fun equals(other: Any?): Boolean =
        this === other || other is AnimeBrowseCriteria &&
            query == other.query && filters == other.filters

    override fun hashCode(): Int = 31 * query.hashCode() + filters.hashCode()

    override fun toString(): String =
        "AnimeBrowseCriteria(query=$query, filters=$filters)"
}
