package com.example.animewiki.domain.model

import java.util.Collections

class AnimeFilters(
    val format: AnimeFormat? = null,
    val includeAdultContent: Boolean = false,
    genres: Set<String> = emptySet()
) {
    val genres: Set<String> = Collections.unmodifiableSet(genres.toSet())

    val isEmpty: Boolean
        get() = format == null && !includeAdultContent && genres.isEmpty()

    val activeCount: Int
        get() = listOfNotNull(format).size +
            (if (includeAdultContent) 1 else 0) +
            genres.size

    fun copy(
        format: AnimeFormat? = this.format,
        includeAdultContent: Boolean = this.includeAdultContent,
        genres: Set<String> = this.genres
    ): AnimeFilters = AnimeFilters(
        format = format,
        includeAdultContent = includeAdultContent,
        genres = genres
    )

    override fun equals(other: Any?): Boolean =
        this === other || other is AnimeFilters &&
            format == other.format &&
            includeAdultContent == other.includeAdultContent &&
            genres == other.genres

    override fun hashCode(): Int {
        var result = format?.hashCode() ?: 0
        result = 31 * result + includeAdultContent.hashCode()
        result = 31 * result + genres.hashCode()
        return result
    }

    override fun toString(): String =
        "AnimeFilters(" +
            "format=$format, " +
            "includeAdultContent=$includeAdultContent, " +
            "genres=$genres" +
            ")"
}
