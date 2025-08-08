package com.Samehadaku

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Samehadaku : MainAPI() {
    
    override var mainUrl = ""
    
    override var name = "Samehadaku"
    override var hasMainPage = true
    override var lang = "id"
    override var supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "anime-terbaru" to "Terbaru",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page", timeout = 50L).document
        val home = document.select("li").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.thumb a").attr("title")
        val href = fixUrl(this.select("div.thumb a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.thumb a img").attr("src").toString())
        // val quality = getQualityFromString(this.select("span.mli-quality").text())
        return newMovieSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            // this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("${mainUrl}?s=$query", timeout = 50L).document
            val results =document.select("div.animepostx").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load (url: String): LoadResponse? {
        val document = app.get(url, timeout = 50L).document
        val title =
            document.selectFirst("h1.entry-title")?.text()?.trim().toString().substringBefore("Sub Indo")
        val poster = document.select("div.thumb img").attr("src").toString()
        val description = document.selectFirst("div.entry-content.entry-content-single")?.text()?.trim()
        val tvtag = TvType.Anime
        val genre = document.select("div.genre-info").select("a").map { it.text() }
        return {
            val episodes = mutableListOf<Episode>()
            document.select("div.lstepsiode.listeps").amap { info -> 
                info.select("ul li div.epsleft span.lchx a").forEach { it ->
                    val name = it.select("a").text().trim()
                    val href = it.select("a").attr("href") ?: ""
                    val Rawepisode = it.select("a").text().substringAfter("Episode").trim().toIntOrNull()
                    episode.add(
                        newEpisode(href)
                        {
                            this.episode=Rawepisode
                            this.name=name
                        }
                    )
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
            }

        }
    } 

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("div.html5-video-container video").forEach {
            val href = it.attr("src")
            loadExtractor(href,subtitleCallback, callback)
        }
        return true
    }
}