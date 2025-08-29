package com.Kuramanime

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

open class PixelDrain : ExtractorApi() {
    override val name            = "PixelDrain"
    override val mainUrl         = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mId = Regex("/u/(.*)").find(url)?.groupValues?.get(1)
        if (mId.isNullOrEmpty())
        {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url
                ) {
                    this.referer = url
                }
            )
        }
        else {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    "$mainUrl/api/file/${mId}?download",
                ) {
                    this.referer = url
                }
            )
        }
    }
}

class KuramaDrive : ExtractorApi() {
    override val name            = "KuramaDrive"
    override val mainUrl         = "https://v1.kuramadrive.com/"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val document = app.get(url, timeout = 50L).document
        val fileId = url.substringAfter("kdrive/")
        val domain = document.select("div.server-button button").attr("data-domain").toString()
        val url = "https://v1.kuramadrive.com/api/v1/drive/file/$fileId/check"
        val client = OkHttpClient()

        val tokenUrl = app.get("https://v1.kuramadrive.com/api/v1/var/js/master.js").text
        val token = Regex("""globalBearerToken:\s*'([^']+)'""").find(tokenUrl)?.groupValues?.get(1)



        val formBody = FormBody.Builder()
        .add("domain", "$domain")
        .add("tokens", "")
        .build()

        val request = Request.Builder()
        .url(url)
        .post(formBody)
        .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
        .addHeader("authorization", "Bearer $token")
        .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.d("KuramaDrive", "kuramaDrive gagal")
            } else {
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)

                    val fileUrl = json
                        .getJSONObject("data")
                        .getString("url")

                    println("Link video: $fileUrl")

                    return listOf (
                        newExtractorLink(
                            name,
                            name,
                            fileUrl,
                        ){
                            this.referer = url
                        }
                    ) 
                }
            }
        }
       return null
    }
}

