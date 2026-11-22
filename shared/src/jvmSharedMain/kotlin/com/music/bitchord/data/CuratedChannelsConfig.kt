package com.music.bitchord.data

import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Metadata for an approved YouTube Music channel or record label.
 *
 * @property browseId YouTube Music channel browseId starting with "UC"
 * @property name Canonical name of the artist or music company
 * @property tag Descriptive tag, e.g. "Label" or "Artist"
 */
data class CuratedChannel(
    val browseId: String,
    val name: String,
    val tag: String = "Official",
)

/**
 * Central configuration for the curated personal fork of BitChord.
 *
 * Only tracks and releases originating from the approved YouTube Music
 * channels defined here are surfaced on Home, searchable, or permitted
 * in playback and AutoPlay queues.
 */
object CuratedChannelsConfig {

    /**
     * Master switch for the curated channel allowlist policy.
     * When true, only tracks originating from [APPROVED_CHANNELS] can be viewed, searched, or played.
     */
    var isCuratedModeEnabled: Boolean = true

    /**
     * Explicit allowlist of approved YouTube Music artist and music company browseIds (`UC...`).
     * Easily update or append additional verified IDs here.
     */
    val APPROVED_CHANNELS: List<CuratedChannel> = listOf(
        // Indian Record Labels
        CuratedChannel("UCq-Fj5jknLsUf-MWSy4_brA", "T-Series", "Label"),
        CuratedChannel("UC56gTxNs4f9xZ7Pa2i5xNzg", "Sony Music India", "Label"),
        CuratedChannel("UCF_fDSgPpBGu38zsU6edsmw", "Zee Music Company", "Label"),
        CuratedChannel("UCbTLwN10NoCU4WDzLf1JMOA", "YRF", "Label"),
        CuratedChannel("UCNAqa2ch65vZXmNuQfDauLw", "Tips Official", "Label"),
        CuratedChannel("UCpmfBg4mE6Ih_pylpq_iEsg", "Saregama Music", "Label"),
        CuratedChannel("UCwA3WDWvVIdpkfXN3tP9lEA", "Speed Records", "Label"),
        CuratedChannel("UCvmsWpUbb4h-e9g5kx5Z9_A", "T-Series Apna Punjab", "Label"),

        // Prominent Artists
        CuratedChannel("UCnxgu_q643642LuLsP11UWw", "A. R. Rahman", "Artist"),
        CuratedChannel("UC4-y_qT51x3b0kXF83e_0_Q", "Arijit Singh", "Artist"),
        CuratedChannel("UCqhnX4jA0A5paNd1v-zEysw", "Anirudh Ravichander", "Artist"),
        CuratedChannel("UCeoiTXZ_4YwH5vM07Ue0aPg", "Pritam", "Artist"),
        CuratedChannel("UC28U_Xv36FjWw4_y6jA_nAw", "Sidhu Moose Wala", "Artist"),
        CuratedChannel("UCh4H_6Vupz69gYgtvK9u8DA", "Diljit Dosanjh", "Artist"),
        CuratedChannel("UCCy_q-4bC38u3_1J0g8_d-Q", "Shreya Ghoshal", "Artist"),
        CuratedChannel("UC8D_NXdGv_oFw4-aYF9zQ_g", "Karan Aujla", "Artist"),
        CuratedChannel("UC0n8_P3S1V1FpLqVv7r2z3A", "Badshah", "Artist"),
        CuratedChannel("UC5C_7mO_P8w3K0b6_bB6t2g", "DIVINE", "Artist"),
    )

    /** Set of approved channel browse IDs for O(1) lookup. */
    val ALLOWED_BROWSE_IDS: Set<String> get() = APPROVED_CHANNELS.mapTo(HashSet()) { it.browseId }

    /** Normalized names of approved artists and labels for fallback matching. */
    val ALLOWED_NAMES_NORMALIZED: Set<String> get() = APPROVED_CHANNELS.mapTo(HashSet()) {
        it.name.lowercase(Locale.ROOT).trim()
    }

    /** Cache of verified curated video IDs encountered in sessions. */
    private val curatedVideoIds = ConcurrentHashMap.newKeySet<String>()

    /** Cache of curated pool songs available for AutoPlay fallback. */
    private val curatedPoolSongs = ConcurrentHashMap<String, Song>()

    /**
     * Records a known curated track into the session registry.
     */
    fun recordAllowedTrack(song: Song) {
        if (song.videoId.isNotBlank()) {
            curatedVideoIds.add(song.videoId)
            curatedPoolSongs.putIfAbsent(song.videoId, song)
        }
    }

    /**
     * Returns true if the videoId is known to belong to a curated track.
     */
    fun isCuratedVideoId(videoId: String): Boolean {
        return curatedVideoIds.contains(videoId)
    }

    /**
     * Checks if a channel browseId is in the approved allowlist.
     */
    fun isAllowedChannel(browseId: String?): Boolean {
        if (!isCuratedModeEnabled) return true
        if (browseId == null) return false
        return browseId in ALLOWED_BROWSE_IDS
    }

    /**
     * Checks if a [Song] belongs to any approved YouTube Music channel or artist.
     */
    fun isAllowedSong(song: Song): Boolean {
        if (!isCuratedModeEnabled) return true
        if (curatedVideoIds.contains(song.videoId)) return true

        // 1. Direct lead artistId match
        if (song.artistId != null && song.artistId in ALLOWED_BROWSE_IDS) {
            recordAllowedTrack(song)
            return true
        }

        // 2. Any credited artist browseId match
        if (song.artists.any { it.browseId != null && it.browseId in ALLOWED_BROWSE_IDS }) {
            recordAllowedTrack(song)
            return true
        }

        // 3. Normalized artist name matching fallback (if browseId was missing from byline)
        val songArtistNorm = song.artist.lowercase(Locale.ROOT)
        if (ALLOWED_NAMES_NORMALIZED.any { name -> songArtistNorm.contains(name) }) {
            recordAllowedTrack(song)
            return true
        }

        return false
    }

    /**
     * Checks if an artist/title pair belongs to a curated channel.
     */
    fun isCuratedTrack(title: String, artist: String): Boolean {
        val artistNorm = artist.lowercase(Locale.ROOT)
        return ALLOWED_NAMES_NORMALIZED.any { name -> artistNorm.contains(name) }
    }

    /**
     * Checks if a search [BrowseItem] (artist, album, playlist) is allowed.
     */
    fun isAllowedBrowseItem(item: BrowseItem): Boolean {
        if (!isCuratedModeEnabled) return true
        if (item.type == BrowseType.ARTIST) {
            return item.browseId in ALLOWED_BROWSE_IDS
        }
        val subNorm = item.subtitle.lowercase(Locale.ROOT)
        val titleNorm = item.title.lowercase(Locale.ROOT)
        return ALLOWED_NAMES_NORMALIZED.any { name -> subNorm.contains(name) || titleNorm.contains(name) }
    }

    /**
     * Checks if a [ShelfItem] is allowed on the curated Home screen.
     */
    fun isAllowedShelfItem(item: ShelfItem): Boolean {
        if (!isCuratedModeEnabled) return true
        if (item.videoId != null && curatedVideoIds.contains(item.videoId)) return true
        if (item.browseId != null && item.browseId in ALLOWED_BROWSE_IDS) return true
        val subNorm = item.subtitle.lowercase(Locale.ROOT)
        val titleNorm = item.title.lowercase(Locale.ROOT)
        return ALLOWED_NAMES_NORMALIZED.any { name -> subNorm.contains(name) || titleNorm.contains(name) }
    }

    /**
     * Supplies fallback tracks from the curated pool when YouTube's algorithmic
     * radio recommendation produces no candidates matching the allowlist.
     */
    fun getCuratedFallbackTracks(limit: Int): List<Song> {
        return curatedPoolSongs.values.shuffled().take(limit)
    }
}
