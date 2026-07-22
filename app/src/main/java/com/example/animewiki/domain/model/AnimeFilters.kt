package com.example.animewiki.domain.model

import java.util.Collections

class AnimeFilters(
    val format: AnimeFormat? = null,
    val rating: AnimeAgeRating? = null,
    genreIds: Set<Int> = emptySet()
) {
    val genreIds: Set<Int> = Collections.unmodifiableSet(genreIds.toSet())

    val isEmpty: Boolean
        get() = format == null && rating == null && genreIds.isEmpty()

    val activeCount: Int
        get() = listOfNotNull(format, rating).size + genreIds.size

    val genresQuery: String?
        get() = genreIds.sorted().joinToString(",").ifBlank { null }

    fun copy(
        format: AnimeFormat? = this.format,
        rating: AnimeAgeRating? = this.rating,
        genreIds: Set<Int> = this.genreIds
    ): AnimeFilters = AnimeFilters(format, rating, genreIds)

    override fun equals(other: Any?): Boolean =
        this === other || other is AnimeFilters &&
            format == other.format && rating == other.rating && genreIds == other.genreIds

    override fun hashCode(): Int {
        var result = format?.hashCode() ?: 0
        result = 31 * result + (rating?.hashCode() ?: 0)
        result = 31 * result + genreIds.hashCode()
        return result
    }

    override fun toString(): String =
        "AnimeFilters(format=$format, rating=$rating, genreIds=$genreIds)"
}
