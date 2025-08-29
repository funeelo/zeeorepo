package com.AnimeSail

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Element
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AnimeSail : MainAPI() {
    override var mainUrl = "https://154.26.137.28"

    override var name = "AnimeSail"
    override var hasMainPage = true
    override var lang = "id"
    override var supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "" to "Episode Terbaru",
        "rilisan-anime-terbaru" to "Anime Terbaru",
        "rilisan-donghua-terbaru" to "Donghua Terbaru",
    )

    private suspend fun request(url: String, ref: String? = null): NiceResponse {
        return app.get(
            url,
            headers =
                mapOf(
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                    "Cookie" to "_as_ipin_tz=Asia/Jakarta; _as_ipin_lc=en-US; _as_ipin_ct=ID"
                ),
            referer = ref
        )
    }

    private suspend fun getProperAnimeLink(uri: String): String {
        val document = request(uri).document
        val url = fixUrl(document.select("div.breadcrumb span:nth-child(3) a").attr("href"))

        return url
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = request("$mainUrl/${request.data}/page/$page").document
        val home = document.select("div.listupd article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private suspend fun Element.toSearchResult(): SearchResponse {
        val title = this.select("a").attr("title").toString().substringBefore("Episode").trim()
        val href = getProperAnimeLink(fixUrl(this.select("a").attr("href")))
        val posterUrl = fixUrlNull(this.select("div.limit img").attr("src").toString())
        
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("${mainUrl}?s=$query", timeout = 50L).document
            val results =document.select("div.column-content a:has(div.amv)").mapNotNull { it.toSearchResult() }
        return results
    }

    override suspend fun load (url: String): LoadResponse {
        val document = request(url).document
        val title = document.select("h1.entry-title").text().substringBefore("Subtitle").trim()
        val poster = document.select("div.entry-content.serial-info img").attr("src")
        val description = document.select("div.entry-content.serial-info p:nth-child(2)").text().trim()
        val tags = document.select("table tr:has(th:matchesOwn(^\\s*Genre:)) td").text().trim().split(", ")
        val episode: List<Episode>  = document.select("ul.daftar li").map {
            val href = fixUrl(it.select("a").attr("href"))
            val episode = it.select("a").text().substringAfter("Episode").substringBefore("Subtitle").trim().toIntOrNull()

            newEpisode(href) {
                this.name = "Episode $episode"
                this.episode = episode
            }
        }.reversed()
        
        return newAnimeLoadResponse(title, url, TvType.Anime) {
            posterUrl = poster
            plot = description
            this.tags = tags
            addEpisodes(DubStatus.Subbed, episode)
        }   
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = request(data).document
        val realLink = fixUrl(document.select("center:has(a.singledl) a").attr("href"))
        val realPage = request(realLink).document
        realPage.select("table a").forEach { li->
            val url = li.attr("data-href")
            loadExtractor(url, subtitleCallback, callback)
        }
        
        return true
    }

}