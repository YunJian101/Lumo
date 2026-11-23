package com.lumo.player.data

import com.lumo.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class Library(
    val guid: String,
    val title: String,
    val poster: String?,
)

data class MediaItem(
    val guid: String,
    val title: String,
    val type: String,
    val poster: String?,
    val tvTitle: String?,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val singleChildGuid: String?,
    val overview: String,
    val voteAverage: String,
    val duration: Long,
    val ts: Long,
    val watched: Int,
    val resolutions: List<String>,
    val year: String?,
    val genres: List<Int>,
    val createTime: Long,
) {
    val displayTitle: String
        get() = when {
            !tvTitle.isNullOrEmpty() && episodeNumber > 0 -> "$tvTitle 第 $episodeNumber 集"
            !tvTitle.isNullOrEmpty() -> tvTitle
            else -> title
        }

    val playableGuid: String
        get() = singleChildGuid?.takeIf { it.isNotEmpty() } ?: guid
}

data class ItemDetail(
    val guid: String,
    val title: String,
    val overview: String,
    val poster: String?,
    val backdrops: String?,
    val voteAverage: String,
    val type: String,
    val numberEpisodes: Int,
    val numberSeasons: Int,
    val parentGuid: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
)

data class PlayInfo(
    val guid: String,
    val mediaGuid: String,
    val ts: Long,
    val duration: Long,
)

data class Quality(val resolution: String, val bitrate: Long)

data class StreamInfo(
    val videoCodec: String,
    val width: Int,
    val height: Int,
    val bitrate: Long,
    val audioCodec: String,
    val channels: Int,
    val hdr: String,
    val fps: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val videoProfile: String,
    val bitDepth: Int,
    val colorSpace: String,
    val colorTransfer: String,
    val audioSampleRate: String,
    val audioLanguage: String,
    val qualities: List<Quality> = emptyList(),
    val videoGuid: String = "",
    val audioGuid: String = "",
)

data class Person(
    val name: String,
    val role: String,
    val job: String,
    val profilePath: String?,
)

data class Genre(val id: Int, val name: String)

data class TagOptions(
    val genres: List<Genre>,
    val resolutions: List<String>,
)

object FnApi {

    private val API_KEY = BuildConfig.FN_API_KEY
    private val API_SECRET = BuildConfig.FN_API_SECRET

    var host: String = ""
    var token: String = ""

    private val jsonType = "application/json".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val needsAuth = path.contains("/sys/img") || path.contains("/media/")
            if (token.isNotEmpty() && needsAuth) {
                chain.proceed(request.newBuilder().header("Authorization", token).build())
            } else {
                chain.proceed(request)
            }
        }
        .build()

    fun httpClient(): OkHttpClient = client

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun authx(path: String, data: String): String {
        val dataHash = md5(data)
        val nonce = (100000..999999).random().toString()
        val timestamp = System.currentTimeMillis().toString()
        val sign = md5("${API_KEY}_${path}_${nonce}_${timestamp}_${dataHash}_${API_SECRET}")
        return "nonce=$nonce&timestamp=$timestamp&sign=$sign"
    }

    private suspend fun call(
        method: String,
        path: String,
        body: String? = null,
        withToken: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val pathOnly = path.substringBefore('?')
        val query = path.substringAfter('?', "")
        val authData = if (method == "GET") query else (body ?: "")
        val builder = Request.Builder()
            .url(host + path)
            .header("authx", authx(pathOnly, authData))
            .header("x-trim-client", "web")
        if (withToken && token.isNotEmpty()) {
            builder.header("Authorization", token)
        }
        if (body != null) {
            builder.method(method, body.toRequestBody(jsonType))
        } else {
            builder.method(method, null)
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (text.isEmpty()) JSONObject() else JSONObject(text)
        }
    }

    suspend fun login(hostUrl: String, user: String, pass: String): String {
        var normalized = hostUrl.trim().trimEnd('/')
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        host = normalized
        val body = JSONObject()
            .put("username", user)
            .put("password", pass)
            .toString()
        val result = call("POST", "/v/api/v1/login", body, withToken = false)
        if (result.optInt("code") != 0) {
            throw IllegalStateException(result.optString("msg", "登录失败"))
        }
        val newToken = result.optJSONObject("data")?.optString("token").orEmpty()
        if (newToken.isEmpty()) throw IllegalStateException("登录失败：没有拿到 token")
        token = newToken
        return newToken
    }

    suspend fun libraries(): List<Library> {
        val result = call("GET", "/v/api/v1/mediadb/list")
        val array = result.optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Library(
                guid = item.optString("guid"),
                title = item.optString("title"),
                poster = item.optString("poster").ifEmpty { null },
            )
        }
    }

    suspend fun items(
        ancestorGuid: String,
        pageSize: Int = 30,
        page: Int = 1,
        genres: List<Int> = emptyList(),
        resolution: String? = null,
        sortColumn: String = "create_time",
        sortType: String = "DESC",
    ): List<MediaItem> {
        val tags = JSONObject()
            .put("type", JSONArray(listOf("Movie", "TV", "Directory", "Video")))
        if (genres.isNotEmpty()) tags.put("genres", JSONArray(genres))
        if (!resolution.isNullOrEmpty()) tags.put("resolution", JSONArray(listOf(resolution)))
        val body = JSONObject()
            .put("ancestor_guid", ancestorGuid)
            .put("tags", tags)
            .put("exclude_grouped_video", 1)
            .put("sort_type", sortType)
            .put("sort_column", sortColumn)
            .put("page_size", pageSize)
            .put("page", page)
            .toString()
        val result = call("POST", "/v/api/v1/item/list", body)
        val array = result.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
        return (0 until array.length()).map { index -> parseItem(array.getJSONObject(index)) }
    }

    suspend fun allItems(ancestorGuid: String, maxItems: Int = 600): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        var page = 1
        while (result.size < maxItems) {
            val batch = items(ancestorGuid, pageSize = 100, page = page)
            result += batch
            if (batch.size < 100) break
            page++
        }
        return result.take(maxItems)
    }

    suspend fun search(query: String): List<MediaItem> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val result = call("GET", "/v/api/v1/search/list?q=$encoded")
        val array = result.optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).map { index -> parseItem(array.getJSONObject(index)) }
    }

    suspend fun seasons(guid: String): List<MediaItem> {
        val result = call("GET", "/v/api/v1/season/list/$guid")
        val array = result.optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).map { index -> parseItem(array.getJSONObject(index)) }
    }

    suspend fun episodes(seasonGuid: String): List<MediaItem> {
        val result = call("GET", "/v/api/v1/episode/list/$seasonGuid")
        val array = result.optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).map { index -> parseItem(array.getJSONObject(index)) }
    }

    suspend fun reportProgress(itemGuid: String, mediaGuid: String, ts: Long) {
        if (itemGuid.isEmpty() || mediaGuid.isEmpty()) return
        val body = JSONObject()
            .put("item_guid", itemGuid)
            .put("media_guid", mediaGuid)
            .put("ts", ts)
            .toString()
        runCatching { call("POST", "/v/api/v1/play/record", body) }
    }

    suspend fun playQuality(mediaGuid: String, level: Int): Boolean {
        val body = JSONObject()
            .put("media_guid", mediaGuid)
            .put("level", level)
            .toString()
        val result = runCatching { call("POST", "/v/api/v1/play/quality", body) }.getOrNull()
        return result?.optInt("code") == 0
    }

    suspend fun playLink(
        itemGuid: String,
        mediaGuid: String,
        videoGuid: String,
        audioGuid: String,
        subtitleGuid: String = "",
    ): String? {
        val body = JSONObject()
            .put("item_guid", itemGuid)
            .put("media_guid", mediaGuid)
            .put("video_guid", videoGuid)
            .put("audio_guid", audioGuid)
            .put("subtitle_guid", subtitleGuid)
            .toString()
        val result = runCatching { call("POST", "/v/api/v1/play/play", body) }.getOrNull()
            ?: return null
        return result.optJSONObject("data")?.optString("play_link")?.ifEmpty { null }
    }

    suspend fun transcodeLink(
        mediaGuid: String,
        videoGuid: String,
        videoEncoder: String,
        resolution: String,
        bitrate: Long,
        startTimestamp: Long,
        audioGuid: String,
        audioEncoder: String,
        channels: Int,
    ): String? {
        val body = JSONObject()
            .put("media_guid", mediaGuid)
            .put("video_guid", videoGuid)
            .put("video_encoder", videoEncoder)
            .put("resolution", resolution)
            .put("bitrate", bitrate)
            .put("startTimestamp", startTimestamp)
            .put("audio_encoder", audioEncoder)
            .put("audio_guid", audioGuid)
            .put("channels", channels)
            .toString()
        val result = runCatching { call("POST", "/v/api/v1/play/play", body) }.getOrNull()
            ?: return null
        return result.optJSONObject("data")?.optString("play_link")?.ifEmpty { null }
    }

    suspend fun resetQuality(
        itemGuid: String,
        mediaGuid: String,
        videoGuid: String,
        audioGuid: String,
        resolution: String,
        bitrate: Long,
        startTimestamp: Long,
    ): Boolean {
        val link = playLink(itemGuid, mediaGuid, videoGuid, audioGuid) ?: return false
        val body = JSONObject()
            .put("req", "media.resetQuality")
            .put("reqid", "1234567890ABCDEF2s")
            .put("playLink", link)
            .put("quality", JSONObject().put("resolution", resolution).put("bitrate", bitrate))
            .put("startTimestamp", startTimestamp)
            .put("clearCache", true)
            .toString()
        val result = runCatching { call("POST", "/v/api/v1/media/p", body) }.getOrNull()
            ?: return false
        return result.optJSONObject("data")?.optString("result") == "succ"
    }

    suspend fun playHistory(): List<MediaItem> {
        val result = call("GET", "/v/api/v1/play/list")
        val array = result.optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).map { index -> parseItem(array.getJSONObject(index)) }
    }

    suspend fun persons(itemGuid: String): List<Person> {
        val result = call("POST", "/v/api/v1/person/list/$itemGuid", "{}")
        val array = result.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            Person(
                name = item.optString("name"),
                role = item.optString("role"),
                job = item.optString("job"),
                profilePath = item.optString("profile_path").ifEmpty { null },
            )
        }.sortedBy { it.name }
    }

    suspend fun genres(): List<Genre> {
        val result = call("GET", "/v/api/v1/tag/genres")
        val array = result.optJSONArray("data") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optInt("id")
            Genre(id, genreName(id, item.optString("value")))
        }
    }

    suspend fun tagOptions(): TagOptions {
        val result = call("GET", "/v/api/v1/tag/list")
        val data = result.optJSONObject("data") ?: return TagOptions(emptyList(), emptyList())
        val names = runCatching { genres() }.getOrDefault(emptyList()).associateBy { it.id }
        val genreIds = data.optJSONArray("genres")?.let { array ->
            (0 until array.length()).map { array.optInt(it) }
        } ?: emptyList()
        val resolutions = data.optJSONArray("resolutions")?.let { array ->
            (0 until array.length()).map { array.optString(it) }
        } ?: emptyList()
        return TagOptions(
            genres = genreIds.mapNotNull { names[it] },
            resolutions = resolutions,
        )
    }

    private fun genreName(id: Int, fallback: String): String = when (id) {
        1 -> "动作"
        2 -> "冒险"
        3 -> "动画"
        4 -> "喜剧"
        5 -> "犯罪"
        6 -> "纪录"
        7 -> "剧情"
        8 -> "家庭"
        9 -> "科幻奇幻"
        10 -> "历史"
        11 -> "恐怖"
        12 -> "音乐"
        13 -> "悬疑"
        14 -> "爱情"
        16 -> "电视电影"
        17 -> "惊悚"
        18 -> "战争政治"
        19 -> "西部"
        30 -> "动作冒险"
        else -> fallback
    }

    private fun parseItem(item: JSONObject): MediaItem = MediaItem(
        guid = item.optString("guid"),
        title = item.optString("title"),
        type = item.optString("type"),
        poster = item.optString("poster").ifEmpty { null },
        tvTitle = item.optString("tv_title").ifEmpty { null },
        episodeNumber = item.optInt("episode_number"),
        seasonNumber = item.optInt("season_number"),
        singleChildGuid = item.optString("single_child_guid").ifEmpty { null },
        overview = item.optString("overview"),
        voteAverage = item.optString("vote_average"),
        duration = item.optLong("duration"),
        ts = item.optLong("ts"),
        watched = item.optInt("watched"),
        resolutions = item.optJSONObject("media_stream")
            ?.optJSONArray("resolutions")
            ?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }
            ?: emptyList(),
        year = run {
            val date = item.optString("air_date").ifEmpty { item.optString("release_date") }
            val year = date.take(4)
            if (year.length == 4 && year.all { it.isDigit() }) year else null
        },
        genres = item.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).map { arr.optInt(it) }
        } ?: emptyList(),
        createTime = item.optLong("create_time"),
    )

    suspend fun itemGenres(guid: String): List<Int> {
        val result = call("GET", "/v/api/v1/item/$guid")
        val array = result.optJSONObject("data")?.optJSONArray("genres") ?: return emptyList()
        return (0 until array.length()).map { array.optInt(it) }
    }

    suspend fun itemDetail(guid: String): ItemDetail {        val result = call("GET", "/v/api/v1/item/$guid")
        val item = result.optJSONObject("data") ?: throw IllegalStateException("详情读取失败")
        return ItemDetail(
            guid = item.optString("guid"),
            title = item.optString("title"),
            overview = item.optString("overview"),
            poster = item.optString("posters").ifEmpty { null },
            backdrops = item.optString("backdrops").ifEmpty { null },
            voteAverage = item.optString("vote_average"),
            type = item.optString("type"),
            numberEpisodes = item.optInt("number_of_episodes"),
            numberSeasons = item.optInt("number_of_seasons"),
            parentGuid = item.optString("parent_guid"),
            episodeNumber = item.optInt("episode_number"),
            seasonNumber = item.optInt("season_number"),
        )
    }

    suspend fun playInfo(itemGuid: String): PlayInfo {
        val body = JSONObject().put("item_guid", itemGuid).toString()
        val result = call("POST", "/v/api/v1/play/info", body)
        val data = result.optJSONObject("data") ?: throw IllegalStateException("播放信息读取失败")
        val item = data.optJSONObject("item")
        return PlayInfo(
            guid = data.optString("guid"),
            mediaGuid = data.optString("media_guid"),
            ts = data.optLong("ts"),
            duration = item?.optLong("duration") ?: 0L,
        )
    }

    suspend fun stream(mediaGuid: String): StreamInfo {
        val body = JSONObject()
            .put("media_guid", mediaGuid)
            .put("ip", "127.0.0.1")
            .put("header", JSONObject().put("User-Agent", JSONArray(listOf("Lumo/1.0"))))
            .put("level", 1)
            .toString()
        val result = call("POST", "/v/api/v1/stream", body)
        val data = result.optJSONObject("data") ?: return StreamInfo(
            "", 0, 0, 0, "", 0, "", "", "", "", 0, "", 0, "", "", "", "",
        )
        val file = data.optJSONObject("file_stream")
        val video = data.optJSONObject("video_stream")
        val audios = data.optJSONArray("audio_streams")
        val audio = if (audios != null && audios.length() > 0) audios.getJSONObject(0) else null
        return StreamInfo(
            videoCodec = video?.optString("codec_name").orEmpty(),
            width = video?.optInt("width") ?: 0,
            height = video?.optInt("height") ?: 0,
            bitrate = video?.optLong("bps") ?: 0L,
            audioCodec = audio?.optString("codec_name").orEmpty(),
            channels = audio?.optInt("channels") ?: 0,
            hdr = video?.optString("color_range_type").orEmpty(),
            fps = video?.optString("r_frame_rate").orEmpty(),
            filePath = file?.optString("path").orEmpty(),
            fileName = file?.optString("file_name").orEmpty(),
            fileSize = file?.optLong("size") ?: 0L,
            videoProfile = video?.optString("profile").orEmpty(),
            bitDepth = video?.optInt("bit_depth") ?: 0,
            colorSpace = video?.optString("color_space").orEmpty(),
            colorTransfer = video?.optString("color_transfer").orEmpty(),
            audioSampleRate = audio?.optString("sample_rate").orEmpty(),
            audioLanguage = audio?.optString("language").orEmpty(),
            qualities = data.optJSONArray("qualities")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val q = arr.optJSONObject(i) ?: return@mapNotNull null
                    Quality(q.optString("resolution"), q.optLong("bitrate"))
                }
            } ?: emptyList(),
            videoGuid = video?.optString("guid").orEmpty(),
            audioGuid = audio?.optString("guid").orEmpty(),
        )
    }

    fun imageUrl(path: String?, width: Int = 400): String? {
        if (path.isNullOrEmpty()) return null
        val prefix = if (path.startsWith("/")) path else "/$path"
        return "$host/v/api/v1/sys/img$prefix?w=$width"
    }

    fun playUrl(mediaGuid: String): String = "$host/v/api/v1/media/range/$mediaGuid"
}
