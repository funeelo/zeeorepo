package com.Otakudesu

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        "complete-anime" to "Complete",
        "ongoing-anime" to "Ongoing"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        
        if (request.data == "jadwal-rilis") {
            val doc = app.get("$mainUrl/jadwal-rilis", timeout = 50L).document
            val home = mutableListOf<HomePageList>()

            doc.select("div.kglist321").forEach { dayDiv ->
                val dayName = dayDiv.selectFirst("h2")?.text()?.trim() ?: return@forEach
                val animeList = dayDiv.select("ul li a").map { el ->
                    val title = el.text().trim()
                    val href = fixUrl(el.attr("href"))
                    newAnimeSearchResponse(title, href, TvType.Anime)
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

    private fun Element.toPageResult(): SearchResponse? {
        val title = this.select("a div.thumbz h2.jdlflm").text()
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a div.thumbz img").attr("src").toString())
        if (title.isBlank() || href.isBlank()) return null
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    
}

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
                        fixUrl(app.get(it.attr("href")).url),
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
