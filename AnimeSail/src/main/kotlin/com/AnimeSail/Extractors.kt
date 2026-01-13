package com.AnimeSail

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*

/* ===================== GOFILE ===================== */

class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false
    private val mainApi = "https://api.gofile.io"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)")
            .find(url)?.groupValues?.get(1) ?: return

        val token = app.get("$mainApi/createAccount")
            .parsedSafe<Account>()?.data?.get("token") ?: return

        val websiteToken = app.get("$mainUrl/dist/js/alljs.js").text.let {
            Regex("fetchData.wt\\s*=\\s*\"([^\"]+)\"")
                .find(it)?.groupValues?.get(1)
        } ?: return

        app.get("$mainApi/getContent?contentId=$id&token=$token&wt=$websiteToken")
            .parsedSafe<Source>()?.data?.contents?.forEach { (_, value) ->
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        value["link"] ?: return@forEach
                    ) {
                        this.headers = mapOf(
                            "Cookie" to "accountToken=$token"
                        )
                    }
                )
            }
    }

    data class Account(
        @JsonProperty("data") val data: HashMap<String, String>? = null
    )

    data class Data(
        @JsonProperty("contents")
        val contents: HashMap<String, HashMap<String, String>>? = null
    )

    data class Source(
        @JsonProperty("data") val data: Data? = null
    )
}

/* ===================== ACEFILE ===================== */

class Acefile : ExtractorApi() {
    override val name = "Acefile"
    override val mainUrl = "https://acefile.co"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = "/(?:d|download|player|f|file)/(\\w+)"
            .toRegex().find(url)?.groupValues?.get(1) ?: return

        val script = getAndUnpack(app.get("$mainUrl/player/$id").text)

        val service = """service\s*=\s*['"]([^'"]+)"""
            .toRegex().find(script)?.groupValues?.get(1) ?: return

        val serverUrl = """['"](\S+check&id\S+?)['"]"""
            .toRegex().find(script)?.groupValues?.get(1)
            ?.replace("\"+service+\"", service) ?: return

        val video = app.get(serverUrl, referer = "$mainUrl/")
            .parsedSafe<Source>()?.data ?: return

        callback.invoke(
            newExtractorLink(
                name,
                name,
                video
            )
        )
    }

    data class Source(
        val data: String? = null
    )
}

/* ===================== KRAKENFILES ===================== */

class Krakenfiles : ExtractorApi() {
    override val name = "Krakenfiles"
    override val mainUrl = "https://krakenfiles.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = Regex("/(?:view|embed-video)/([\\da-zA-Z]+)")
            .find(url)?.groupValues?.get(1) ?: return

        val doc = app.get("$mainUrl/embed-video/$id").document
        val link = doc.selectFirst("source")?.attr("src") ?: return

        callback.invoke(
            newExtractorLink(
                name,
                name,
                httpsify(link)
            )
        )
    }
}

/* ===================== MP4UPLOAD ===================== */

class Mp4Upload : ExtractorApi() {
    override val name = "Mp4Upload"
    override val mainUrl = "https://www.mp4upload.com"
    override val requiresReferer = true

    private val ua =
        "Mozilla/5.0 (Android 13; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(
            url,
            referer = referer ?: mainUrl,
            headers = mapOf(
                "User-Agent" to ua
            )
        ).document

        val videoUrl = doc.selectFirst("video source")
            ?.attr("src") ?: return

        callback.invoke(
            newExtractorLink(
                name,
                name,
                httpsify(videoUrl)
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}