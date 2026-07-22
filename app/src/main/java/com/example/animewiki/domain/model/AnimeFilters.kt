package com.example.animewiki.domain.model

data class AnimeFilters(
    val format: AnimeFormat? = null,
    val rating: AnimeAgeRating? = null,
    val genreIds: Set<Int> = emptySet()
) {
    val isEmpty: Boolean
        get() = format == null && rating == null && genreIds.isEmpty()

    val activeCount: Int
        get() = listOfNotNull(format, rating).size + genreIds.size

    val genresQuery: String?
        get() = genreIds.sorted().joinToString(",").ifBlank { null }
}
