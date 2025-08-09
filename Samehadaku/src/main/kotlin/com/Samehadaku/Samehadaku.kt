package com.Samehadaku

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Samehadaku : MainAPI() {
    
    override var mainUrl = "https://v1.samehadaku.how"
    
    override var name = "Samehadaku"
    override var hasMainPage = true
    override var lang = "id"
    override var supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "daftar-anime-2/?title=&status=&type=&order=update" to "Terbaru",
        "daftar-anime-2/?title=&status=&type=TV&order=popular" to "TV Populer",
        "daftar-anime-2/?title=&status=&type=OVA&order=title" to "OVA",
        "daftar-anime-2/?title=&status=&type=Movie&order=title" to "Movie",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}", timeout = 50L).document
        val home = document.select("div.animposx").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("a").attr("title")
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a div.content-thumb img").attr("src").toString())
        // val quality = getQualityFromString(this.select("span.mli-quality").text())
        return newMovieSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            // this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("${mainUrl}?s=$query", timeout = 50L).document
            val results =document.select("div.animposx").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load (url: String): LoadResponse {
        val document = app.get(url, timeout = 50L).document
        val title =
            document.selectFirst("h1.entry-title")?.text()?.trim().toString().substringBefore("Sub Indo")
        val poster = document.select("div.thumb img").attr("src").toString()
        val description = document.selectFirst("div.entry-content.entry-content-single p")?.text()?.trim()
        val tvtag = if (url.contains("anime")) TvType.TvSeries else TvType.AnimeMovie
        val genre = document.select("div.genre-info").select("a").map { it.text() }
        val episodes = mutableListOf<Episode>()
            document.select("div.lstepsiode.listeps").amap { info -> 
                info.select("ul li div.epsleft span.lchx a").forEach { it ->
                    val name = it.select("a").text().trim()
                    val href = it.select("a").attr("href") ?: ""
                    val Rawepisode = it.select("a").text().substringAfter("Episode").trim().toIntOrNull()
                    episodes.add(
                        newEpisode(href)
                        {
                            this.episode=Rawepisode
                            this.name=name
                        }
                    )
                }
            }
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genre
        }
    } 

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("div#downloadb li").forEach {
           val href = it.attr("href")
            loadExtractor(href,subtitleCallback, callback)
        }
        return true
    }
}