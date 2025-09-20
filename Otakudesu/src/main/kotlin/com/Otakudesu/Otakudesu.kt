package com.Otakudesu

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element

@Serializable
data class AnilistData(val data: Media)

@Serializable
data class Media(val Media: MediaItem?)

@Serializable
data class MediaItem(val coverImage: CoverImage?)

@Serializable
data class CoverImage(val medium: String?)

class Otakudesu : MainAPI() {

    override var mainUrl = "https://otakudesu.best"

    override var name = "Otakudesu"
    override var hasMainPage = true
    override var lang = "id"
    override var supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "jadwal-rilis" to "Jadwal Rilis",
        "ongoing-anime" to "Terbaru"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data == "jadwal-rilis") {
            val document = app.get("$mainUrl/jadwal-rilis", timeout = 50L).document
            val home = mutableListOf<HomePageList>()

            document.select("div.kglist321").forEach { dayDiv ->
                val dayName = dayDiv.selectFirst("h2")?.text()?.trim() ?: return@forEach

                val animeTitles = dayDiv.select("ul li a").map { it.text().trim() }
                val animeListLinks = dayDiv.select("ul li a").map { fixUrl(it.attr("href")) }

                val animeList = coroutineScope {
                    animeTitles.mapIndexed { index, title ->
                        async {
                            val poster = fetchCoverFromAnilist(title)
                            newAnimeSearchResponse(title, animeListLinks[index], TvType.Anime) {
                                this.posterUrl = poster
                            }
                        }
                    }.awaitAll()
                }

                if (animeList.isNotEmpty()) {
                    home.add(HomePageList(dayName, animeList))
                }
            }

            return HomePageResponse(home)
        }

        val document = app.get("$mainUrl/${request.data}/page/$page", timeout = 50L).document
        val home = document.select("div.venz li").mapNotNull { it.toPageResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private suspend fun fetchCoverFromAnilist(title: String): String? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val query = """
                query (${'$'}search: String) {
                  Media(search: ${'$'}search, type: ANIME) {
                    coverImage {
                      medium
                    }
                  }
                }
            """.trimIndent()

            val variables = """
              {
                "search": "$title"
              }
            """.trimIndent()

            val jsonBody = """
                {
                  "query": "$query",
                  "variables": $variables
                }
            """.trimIndent()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonBody.toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val result = Json.decodeFromString<AnilistData>(responseBody)
                    result.data.Media?.coverImage?.medium
                } else null
            } else null
        } catch (e: Exception) {
            Log.e("Anilist fetch error", e.toString())
            null
        }
    }

    private fun Element.toPageResult(): SearchResponse {
        val title = this.select("a div.thumbz h2.jdlflm").text()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a div.thumbz img").attr("src").toString())
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("h2 a").text().let {
            it.substring(0, listOf(
                it.indexOf("(Episode"),
                it.indexOf("Sub Indo"),
                it.indexOf("Subtitle"),
                it.indexOf("BD")
            ).filter { idx -> idx >= 0 }.minOrNull() ?: it.length)
        }.trim()

        val href = fixUrl(this.select("h2 a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src").toString())

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query&post_type=anime", timeout = 50L).document
        return document.select("div.page ul.chivsrc li").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, timeout = 50L).document
        val title = document.selectFirst("div.infozingle p:nth-child(1) span")?.text()?.trim().toString().substringAfter(":")
        val poster = document.select("div.fotoanime img").attr("src").toString()
        val description = document.selectFirst("div.sinopc")?.text()?.trim()
        val genre = document.select("div.infozingle p:nth-child(11) span").select("a").map { it.text() }

        val episodeListDiv = document.select("div.episodelist")
            .firstOrNull { div ->
                div.selectFirst(".monktit")?.text()?.contains("Episode List", ignoreCase = true) == true
            }

        val episodes = episodeListDiv?.select("ul li")?.map {
            val href = fixUrl(it.select("a").attr("href"))
            val episode = it.select("a").text().substringAfter("Episode").substringBefore("Subtitle").substringBefore("(End)").trim().toIntOrNull()
            newEpisode(href).apply {
                this.name = "Episode $episode"
                this.season = season
                this.episode = episode
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genre
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val desu = document.select("div.responsive-embed-stream iframe").attr("src")
        Log.d("Mohiro", desu.toString())
        loadExtractor(desu, subtitleCallback, callback)

        document.select("div.download ul li").map { el ->
            el.select("a").apmap {
                val res = app.get(it.attr("href"))
                val finalUrl = res.url
                Log.d("Mohiro", finalUrl.toString())
                loadFixedExtractor(
                    fixUrl(finalUrl),
                    el.select("strong").text().substringAfter("Mp4"),
                    "$mainUrl/",
                    subtitleCallback,
                    callback
                )
            }
        }
        return true
    }

    private suspend fun loadFixedExtractor(
        url: String,
        name: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            CoroutineScope(Dispatchers.IO).launch {
                callback.invoke(
                    newExtractorLink(
                        link.name,
                        link.name,
                        link.url,
                    ) {
                        this.quality = name.fixQuality()
                    }
                )
            }
        }
    }

    private fun String.fixQuality(): Int {
        return when (this.uppercase()) {
            "4K" -> Qualities.P2160.value
            "FULLHD" -> Qualities.P1080.value
            "MP4HD" -> Qualities.P720.value
            else -> this.filter { it.isDigit() }.toIntOrNull() ?: Qualities.Unknown.value
        }
    }
}
