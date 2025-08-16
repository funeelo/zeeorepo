package com.Anoboy

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getAndUnpack
import okhttp3.FormBody
import org.json.JSONObject
import java.net.URI

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

        try {
            val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.get(1) ?: return
            val responseText = app.post("$mainApi/accounts").text
            val json = JSONObject(responseText)
            val token = json.getJSONObject("data").getString("token")

            val globalJs = app.get("$mainUrl/dist/js/global.js").text
            val wt = Regex("""appdata\.wt\s*=\s*["']([^"']+)["']""")
                .find(globalJs)?.groupValues?.getOrNull(1) ?: return

            val responseTextfile = app.get(
                "$mainApi/contents/$id?wt=$wt",
                headers = mapOf("Authorization" to "Bearer $token")
            ).text

            val fileDataJson = JSONObject(responseTextfile)

            val data = fileDataJson.getJSONObject("data")
            val children = data.getJSONObject("children")
            children.keys().asSequence().forEach { childId ->
                val fileObj = children.getJSONObject(childId)

                val link = fileObj.getString("link")
                val fileName = fileObj.getString("name")
                val fileSize = fileObj.getLong("size")

                callback.invoke(
                    newExtractorLink(
                        "Gofile",
                        "Gofile",
                        link
                    ) {
                        this.quality = getQuality(fileName)
                        this.headers = mapOf("Cookie" to "accountToken=$token")
                    }
                )
            }
            //val firstFileId = children.keys().asSequence().first()
            

            
        } catch (e: Exception) {
            Log.e("Gofile", "Error occurred: ${e.message}")
        }
    }

    private fun getQuality(fileName: String?): Int {
        val match = Regex("(\\d{3,4})|(\\d+)[kK]").find(fileName ?: "")
        return when {
            match?.groupValues?.getOrNull(1)?.isNotEmpty() == true -> 
                match.groupValues[1].toIntOrNull() ?: Qualities.Unknown.value
            match?.groupValues?.getOrNull(2)?.isNotEmpty() == true -> {
                val num = match.groupValues[2].toIntOrNull()
                if (num == 1) 1080 else num?.times(1000) ?: Qualities.Unknown.value
            }
            else -> Qualities.Unknown.value
        }
    }
}

class Mp4Upload : ExtractorApi() {
    override var name = "Mp4Upload"
    override var mainUrl = "https://www.mp4upload.com"
    private val srcRegex = Regex("""player\.src\("(.*?)"""")
    private val srcRegex2 = Regex("""player\.src\([\w\W]*src: "(.*?)"""")

    override val requiresReferer = true
    private val idMatch = Regex("""mp4upload\.com/(embed-|)([A-Za-z0-9]*)""")
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val realUrl = idMatch.find(url)?.groupValues?.get(2)?.let { id ->
            "$mainUrl/embed-$id.html"
        } ?: url
        val response = app.get(realUrl)
        val unpackedText = getAndUnpack(response.text)

        val heightRegex = Regex("""<iframe[^>]*src=["']https?://[^"']*embed-[^"']*["'][^>]*height\s*=\s*"?(\d{3,4})""", RegexOption.IGNORE_CASE)
        val quality = heightRegex.find(unpackedText)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
            ?: Qualities.Unknown.value

        srcRegex.find(unpackedText)?.groupValues?.get(1)?.let { link ->
            return listOf(
                newExtractorLink(
                    name,
                    name,
                    link,
                ) {
                    this.referer = url
                    this.quality = quality ?: Qualities.Unknown.value
                }
            )
        }
        srcRegex2.find(unpackedText)?.groupValues?.get(1)?.let { link ->
            return listOf(
                newExtractorLink(
                    name,
                    name,
                    link,
                ) {
                    this.referer = url
                    this.quality = quality ?: Qualities.Unknown.value
                }
            )
        }
        return null
    }
}