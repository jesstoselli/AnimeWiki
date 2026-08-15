package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.graphql.AnimeDetailsQuery
import com.example.animewiki.graphql.SearchAnimeQuery
import com.example.animewiki.graphql.builder.Data
import com.example.animewiki.graphql.builder.MediaBuilder
import com.example.animewiki.graphql.builder.buildFuzzyDate
import com.example.animewiki.graphql.builder.buildMedia
import com.example.animewiki.graphql.builder.buildMediaCoverImage
import com.example.animewiki.graphql.builder.buildMediaRank
import com.example.animewiki.graphql.builder.buildMediaTitle
import com.example.animewiki.graphql.builder.buildMediaTrailer
import com.example.animewiki.graphql.builder.buildPage
import com.example.animewiki.graphql.builder.buildStudio
import com.example.animewiki.graphql.builder.buildStudioConnection
import com.example.animewiki.graphql.fragment.AnimeCacheFields
import com.example.animewiki.graphql.fragment.AnimeCardFields
import com.example.animewiki.graphql.type.MediaFormat
import com.example.animewiki.graphql.type.MediaRankType
import com.example.animewiki.graphql.type.MediaStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniListMapperTest {

    @Test
    fun `uses nonblank fallbacks and rescales score`() {
        val anime = requireNotNull(
            cardFragment {
                id = 154587
                title = buildMediaTitle {
                    english = " "
                    romaji = "Sousou no Frieren"
                }
                coverImage = buildMediaCoverImage {
                    extraLarge = ""
                    large = "https://img.example/frieren.jpg"
                }
                averageScore = 91
            }.toDomain()
        )

        assertEquals("Sousou no Frieren", anime.title)
        assertEquals("https://img.example/frieren.jpg", anime.imageUrl)
        assertEquals(9.1, anime.score ?: 0.0, 0.001)
    }

    @Test
    fun `rejects blank required title and image`() {
        val noTitle = cardFragment {
            id = 1
            title = buildMediaTitle {
                english = " "
                romaji = ""
            }
            coverImage = buildMediaCoverImage { large = "https://img.example/1.jpg" }
        }
        val noImage = cardFragment {
            id = 2
            title = buildMediaTitle { english = "Valid" }
            coverImage = buildMediaCoverImage {
                extraLarge = " "
                large = ""
            }
        }

        assertNull(noTitle.toDomain())
        assertNull(noImage.toDomain())
    }

    @Test
    fun `strips supported AniList html deterministically`() {
        assertEquals(
            "Line one.\nLine two. & \"more\"",
            " Line one.<br><i>Line two.</i> &amp; &quot;more&quot; ".stripAniListHtml()
        )
    }

    @Test
    fun `normalizes AniList paragraph spacing`() {
        assertEquals(
            "First paragraph.\n\nSecond paragraph.\n\n(Source: Example)",
            "First paragraph.<br>\n<br>\nSecond paragraph.\n\n\n(Source: Example)"
                .stripAniListHtml()
        )
    }

    @Test
    fun `cache mapping preserves all current details and skips malformed list children`() {
        val fragment = cacheFragment {
            id = 154587
            title = buildMediaTitle { english = "Frieren: Beyond Journey's End" }
            coverImage = buildMediaCoverImage { extraLarge = "https://img.example/frieren.jpg" }
            averageScore = 91
            episodes = 28
            format = MediaFormat.TV
            description = "Journey<br><i>continues</i> &amp; more"
            genres = listOf("Adventure", null, "Fantasy")
            studios = buildStudioConnection {
                nodes = listOf(
                    buildStudio { name = "Madhouse" },
                    null,
                    buildStudio { name = "CloverWorks" }
                )
            }
            status = MediaStatus.FINISHED
            duration = 24
            startDate = buildFuzzyDate {
                year = 2023
                month = 10
                day = 4
            }
            endDate = buildFuzzyDate {
                year = 2024
                month = 3
                day = 22
            }
            rankings = listOf(
                null,
                buildMediaRank {
                    rank = 4
                    type = MediaRankType.POPULAR
                    allTime = true
                },
                buildMediaRank {
                    rank = 3
                    type = MediaRankType.RATED
                    allTime = false
                },
                buildMediaRank {
                    rank = 1
                    type = MediaRankType.RATED
                    allTime = true
                }
            )
            trailer = buildMediaTrailer {
                id = "abc123"
                site = "YouTube"
            }
        }

        val anime = requireNotNull(fragment.toDomain())
        val entity = requireNotNull(fragment.toEntity(pageIndex = 8))

        assertMappedDetails(anime)
        assertCachedEntity(anime, entity)
    }

    private fun assertMappedDetails(anime: Anime) {
        assertEquals(154587, anime.id)
        assertEquals("Frieren: Beyond Journey's End", anime.title)
        assertEquals("https://img.example/frieren.jpg", anime.imageUrl)
        assertEquals(9.1, anime.score ?: 0.0, 0.001)
        assertEquals(28, anime.episodes)
        assertEquals("TV", anime.type)
        assertEquals(2023, anime.year)
        assertEquals("Journey\ncontinues & more", anime.synopsis)
        assertEquals(listOf("Adventure", "Fantasy"), anime.genres)
        assertEquals(listOf("Madhouse", "CloverWorks"), anime.studios)
        assertEquals("Oct 4, 2023 to Mar 22, 2024", anime.aired)
        assertEquals("Finished", anime.status)
        assertNull(anime.rating)
        assertEquals("24 min per ep", anime.duration)
        assertEquals(1, anime.rank)
        assertEquals("abc123", anime.trailerYoutubeId)
    }

    private fun assertCachedEntity(anime: Anime, entity: AnimeEntity) {
        assertEquals(anime.id, entity.id)
        assertEquals(anime.title, entity.title)
        assertEquals(anime.imageUrl, entity.imageUrl)
        assertEquals(anime.score, entity.score)
        assertEquals(anime.episodes, entity.episodes)
        assertEquals(anime.type, entity.type)
        assertEquals(anime.year, entity.year)
        assertEquals(anime.synopsis, entity.synopsis)
        assertEquals(anime.genres, entity.genres)
        assertEquals(anime.studios, entity.studios)
        assertEquals(anime.aired, entity.aired)
        assertEquals(anime.status, entity.status)
        assertNull(entity.rating)
        assertEquals(anime.duration, entity.duration)
        assertEquals(anime.rank, entity.rank)
        assertEquals(anime.trailerYoutubeId, entity.trailerYoutubeId)
        assertEquals(8, entity.pageIndex)
    }

    @Test
    fun `uses season year or start year and formats year-only range compactly`() {
        val startYearFallback = requireNotNull(
            cacheFragment {
                id = 1
                title = buildMediaTitle { english = "Start year" }
                coverImage = buildMediaCoverImage { large = "https://img.example/1.jpg" }
                startDate = buildFuzzyDate { year = 2023 }
                endDate = buildFuzzyDate { year = 2024 }
            }.toDomain()
        )
        val explicitSeasonYear = requireNotNull(
            cacheFragment {
                id = 2
                title = buildMediaTitle { english = "Season year" }
                coverImage = buildMediaCoverImage { large = "https://img.example/2.jpg" }
                seasonYear = 2025
                startDate = buildFuzzyDate { year = 2023 }
            }.toDomain()
        )

        assertEquals(2023, startYearFallback.year)
        assertEquals("2023 - 2024", startYearFallback.aired)
        assertEquals(2025, explicitSeasonYear.year)
    }

    @Test
    fun `maps known AniList format and status values to English display strings`() {
        val cases = listOf(
            Triple(MediaFormat.TV, MediaStatus.FINISHED, "TV" to "Finished"),
            Triple(MediaFormat.TV_SHORT, MediaStatus.RELEASING, "TV Short" to "Releasing"),
            Triple(MediaFormat.MOVIE, MediaStatus.NOT_YET_RELEASED, "Movie" to "Not Yet Released"),
            Triple(MediaFormat.SPECIAL, MediaStatus.CANCELLED, "Special" to "Cancelled"),
            Triple(MediaFormat.OVA, MediaStatus.HIATUS, "OVA" to "Hiatus"),
            Triple(MediaFormat.ONA, null, "ONA" to null),
            Triple(MediaFormat.MUSIC, null, "Music" to null),
            Triple(MediaFormat.MANGA, null, "Manga" to null),
            Triple(MediaFormat.NOVEL, null, "Novel" to null),
            Triple(MediaFormat.ONE_SHOT, null, "One Shot" to null)
        )

        cases.forEachIndexed { index, (format, status, expected) ->
            val anime = requireNotNull(
                cacheFragment {
                    id = index + 1
                    title = buildMediaTitle { english = "Anime $index" }
                    coverImage = buildMediaCoverImage { large = "https://img.example/$index.jpg" }
                    this.format = format
                    this.status = status
                }.toDomain()
            )

            assertEquals(expected.first, anime.type)
            assertEquals(expected.second, anime.status)
        }
    }

    @Test
    fun `keeps trailer id only for YouTube`() {
        val anime = requireNotNull(
            cacheFragment {
                id = 1
                title = buildMediaTitle { english = "Trailer" }
                coverImage = buildMediaCoverImage { large = "https://img.example/trailer.jpg" }
                trailer = buildMediaTrailer {
                    id = "other-site"
                    site = "dailymotion"
                }
            }.toDomain()
        )

        assertNull(anime.trailerYoutubeId)
    }

    private fun cardFragment(block: MediaBuilder.() -> Unit): AnimeCardFields =
        requireNotNull(
            SearchAnimeQuery.Data {
                Page = buildPage { media = listOf(buildMedia(block)) }
            }.Page?.media?.singleOrNull()?.animeCardFields
        )

    private fun cacheFragment(block: MediaBuilder.() -> Unit): AnimeCacheFields =
        requireNotNull(
            AnimeDetailsQuery.Data {
                Media = buildMedia(block)
            }.Media?.animeCacheFields
        )
}
