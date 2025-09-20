package com.Otakudesu

import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DesuStream : ExtractorApi() {
    override val name = "DesuStream"
    override val mainUrl = "https://desustream.me"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            Log.e("Desustream", "Url = " + url.toString())
            val document = app.get(url).document
            val scripts = document.select("script").map { it.data() }

            var videoUrl: String = ""

            for (script in scripts) {
                if (script.contains("var vs =")) {
                    // regex untuk ambil file:"...."
                    val regex = Regex("""file\s*:\s*"([^"]+)"""")
                    val match = regex.find(script)
                    videoUrl = match?.groupValues?.get(1).toString()
                    break
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
               callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    videoUrl
                ) {
                    this.quality = getQuality(videoUrl)
                }
            ) 
        }   

        } catch (e: Exception) {
            Log.e("Desustream", "Error: ${e.message}")
        }
    }

    private fun getQuality(videoUrl: String): Int {
        val itagRegex = Regex("""itag=(\d+)""")
        val itag = itagRegex.find(videoUrl)?.groupValues?.get(1)?.toIntOrNull() ?: return Qualities.Unknown.value

        return when (itag) {
            18 -> 360
            22 -> 720
            37 -> 1080
            else -> Qualities.Unknown.value
        }
    }
}
