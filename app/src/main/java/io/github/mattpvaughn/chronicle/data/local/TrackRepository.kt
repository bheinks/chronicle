package io.github.mattpvaughn.chronicle.data.local

import androidx.lifecycle.LiveData
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository.Companion.TRACK_NOT_FOUND
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.plex.PlexMediaApi
import io.github.mattpvaughn.chronicle.data.plex.model.asTrackList
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Singleton


interface ITrackRepository {
    /**
     * Load all tracks from the network corresponding to the book with id == [bookId], add them to
     * the local [TrackDatabase], and return them
     */
    suspend fun loadTracksForAudiobook(bookId: Int): List<MediaItemTrack>

    /**
     * Update the value of [MediaItemTrack.cached] to [isCached] for a [MediaItemTrack] with
     * [MediaItemTrack.id] == [trackId] in the [TrackDatabase]
     */
    suspend fun updateCachedStatus(trackId: Int, isCached: Boolean)

    /** Return all tracks in the [TrackDatabase]  */
    fun getAllTracks(): LiveData<List<MediaItemTrack>>
    suspend fun getAllTracksAsync(): List<MediaItemTrack>

    /**
     * Return a [LiveData<List<MediaItemTrack>>] containing all [MediaItemTrack]s where
     * [MediaItemTrack.parentKey] == [bookId]
     */
    fun getTracksForAudiobook(bookId: Int): LiveData<List<MediaItemTrack>>
    suspend fun getTracksForAudiobookAsync(id: Int): List<MediaItemTrack>

    /** Update the value of [MediaItemTrack.progress] == [trackProgress] and
     * [MediaItemTrack.lastViewedAt] == [lastViewedAt] for the track where
     * [MediaItemTrack.id] == [trackId]
     */
    suspend fun updateTrackProgress(trackProgress: Long, trackId: Int, lastViewedAt: Long)

    /**
     * Return a [MediaItemTrack] where [MediaItemTrack.id] == [id], or null if no such
     * [MediaItemTrack] exists
     */
    suspend fun getTrackAsync(id: Int): MediaItemTrack?

    /**
     * Return the [MediaItemTrack.parentKey] for a [MediaItemTrack] where [MediaItemTrack.id] == [trackId]
     */
    suspend fun getBookIdForTrack(trackId: Int): Int

    /** Remove all [MediaItemTrack] from the [TrackDatabase] */
    suspend fun clear()

    /**
     * Return a [List<MediaItemTrack>] containing all [MediaItemTrack] where [MediaItemTrack.cached] == true
     */
    suspend fun getCachedTracks(): List<MediaItemTrack>

    /** Returns the number of [MediaItemTrack] where [MediaItemTrack.parentKey] == [bookId] */
    suspend fun getTrackCountForBookAsync(bookId: Int): Int

    /**
     * Returns the number of [MediaItemTrack] where [MediaItemTrack.parentKey] == [bookId] and
     * [MediaItemTrack.cached] == true
     */
    suspend fun getCachedTrackCountForBookAsync(bookId: Int): Int

    /** Sets [MediaItemTrack.cached] to false for all [MediaItemTrack] in [TrackDatabase] */
    suspend fun uncacheAll()

    /**
     * Loads all [MediaItemTrack]s available on the server into the load DB and returns a [List]
     * of them
     */
    suspend fun loadAllTracksAsync(): List<MediaItemTrack>
    suspend fun loadAllTracks(): LiveData<List<MediaItemTrack>>

    companion object {
        /**
         * The value representing the [MediaItemTrack.id] for any track which does not exist in the
         * [TrackDatabase]
         */
        const val TRACK_NOT_FOUND: Int = -23
    }
}

@Singleton
class TrackRepository(
    private val trackDao: TrackDao,
    private val prefsRepo: PrefsRepo
) : ITrackRepository {
    override suspend fun loadTracksForAudiobook(bookId: Int): List<MediaItemTrack> {
        return withContext(Dispatchers.IO) {
            val networkTracks = PlexMediaApi.retrofitService.retrieveTracksForAlbum(bookId).asTrackList()
            val localTracks = trackDao.getAllTracksAsync()
            return@withContext mergeNetworkTracks(networkTracks, localTracks)

        }
    }

    override suspend fun updateCachedStatus(trackId: Int, isCached: Boolean) {
        withContext(Dispatchers.IO) {
            trackDao.updateCachedStatus(trackId, isCached)
        }
    }

    override fun getAllTracks(): LiveData<List<MediaItemTrack>> {
        return trackDao.getAllTracks()
    }

    override suspend fun getAllTracksAsync(): List<MediaItemTrack> {
        return withContext(Dispatchers.IO) {
            trackDao.getAllTracksAsync()
        }
    }


    override fun getTracksForAudiobook(bookId: Int): LiveData<List<MediaItemTrack>> {
        return trackDao.getTracksForAudiobook(bookId, prefsRepo.offlineMode)
    }

    override suspend fun getTracksForAudiobookAsync(id: Int): List<MediaItemTrack> {
        return withContext(Dispatchers.IO) {
            trackDao.getTracksForAudiobookAsync(id, prefsRepo.offlineMode)
        }
    }

    override suspend fun updateTrackProgress(
        trackProgress: Long,
        trackId: Int,
        lastViewedAt: Long
    ) {
        withContext(Dispatchers.IO) {
            trackDao.updateProgress(
                trackProgress = trackProgress,
                trackId = trackId,
                lastViewedAt = lastViewedAt
            )
        }
    }

    override suspend fun getTrackAsync(id: Int): MediaItemTrack? {
        return trackDao.getTrackAsync(id)
    }

    override suspend fun getBookIdForTrack(trackId: Int): Int {
        return withContext(Dispatchers.IO) {
            trackDao.getTrackAsync(trackId)?.parentKey ?: TRACK_NOT_FOUND
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            trackDao.clear()
        }
    }

    override suspend fun getCachedTracks(): List<MediaItemTrack> {
        return withContext(Dispatchers.IO) {
            trackDao.getCachedTracksAsync(isCached = true)
        }
    }

    override suspend fun getTrackCountForBookAsync(bookId: Int): Int {
        return withContext(Dispatchers.IO) {
            trackDao.getTrackCountForAudiobookAsync(bookId)
        }
    }

    override suspend fun getCachedTrackCountForBookAsync(bookId: Int): Int {
        return withContext(Dispatchers.IO) {
            trackDao.getCachedTrackCountForBookAsync(bookId)
        }
    }

    override suspend fun uncacheAll() {
        withContext(Dispatchers.IO) {
            trackDao.uncacheAll()
        }
    }

    override suspend fun loadAllTracks(): LiveData<List<MediaItemTrack>> {
        return withContext(Dispatchers.IO) {
            loadAllTracksAsync()
            return@withContext trackDao.getAllTracks()
        }
    }

    override suspend fun loadAllTracksAsync(): List<MediaItemTrack> {
        return withContext(Dispatchers.IO) {
            val networkTracks = PlexMediaApi.retrofitService.retrieveAllTracksInLibrary(Injector.get().plexPrefs().getLibrary()!!.id).asTrackList()
            val localTracks = trackDao.getAllTracksAsync()
            return@withContext mergeNetworkTracks(networkTracks, localTracks)
        }
    }

    /**
     * Merges a list of tracks from the network into the DB by comparing to local tracks and using
     * using logic [MediaItemTrack.merge] to determine what data to keep from each
     */
    private fun mergeNetworkTracks(
        networkTracks: List<MediaItemTrack>,
        localTracks: List<MediaItemTrack>
    ): List<MediaItemTrack> {
        val mergedTracks = networkTracks.map { networkTrack ->
            val localTrack = localTracks.find { it.id == networkTrack.id }
            if (localTrack != null) {
                return@map MediaItemTrack.merge(
                    networkTrack = networkTrack,
                    localTrack = localTrack
                )
            } else {
                return@map networkTrack
            }
        }
        trackDao.insertAll(mergedTracks)
        return mergedTracks
    }
}