package io.github.mattpvaughn.chronicle.features.bookdetails

import android.support.v4.media.session.MediaControllerCompat
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import io.github.mattpvaughn.chronicle.data.local.IBookRepository
import io.github.mattpvaughn.chronicle.data.local.ITrackRepository
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.model.MediaItemTrack
import io.github.mattpvaughn.chronicle.data.plex.CachedFileManager
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.features.player.FakeMediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.IMediaServiceConnection
import io.github.mattpvaughn.chronicle.features.player.ProgressUpdater
import io.github.mattpvaughn.chronicle.getOrAwaitValue
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random


@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class AudiobookDetailsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @RelaxedMockK
    lateinit var mockMediaController: MediaControllerCompat
    lateinit var fakeMediaServiceConnection: IMediaServiceConnection

    @RelaxedMockK
    lateinit var mockPlexPrefsRepo: PlexPrefsRepo

    @RelaxedMockK
    lateinit var mockCachedFileManager: CachedFileManager

    @RelaxedMockK
    private lateinit var mockProgressUpdater: ProgressUpdater

    @MockK
    private lateinit var mockBookRepository: IBookRepository

    @MockK
    private lateinit var mockTrackRepository: ITrackRepository

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)

        // Mock android.util.Log statically b/c not Android system stuff not automatically mocked
        // for unit tests
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        MockKAnnotations.init(this)

        fakeMediaServiceConnection = FakeMediaServiceConnection(mockMediaController)

    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // reset main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    @Test
    fun testOnToggleSummaryView_showDetails() {
        val viewModel = createViewModel()

        viewModel.shouldShowExtendedDetails(true)

        assertThat(viewModel.showExtendedDetails.getOrAwaitValue(), `is`(true))
    }

    @Test
    fun testOnToggleSummaryView_hideDetails() {
        val viewModel = createViewModel()

        viewModel.shouldShowExtendedDetails(false)

        assertThat(viewModel.showExtendedDetails.getOrAwaitValue(), `is`(false))
    }

    @Test
    fun testPlay_transportControlsCalled() {
        val randomIdAudiobook = defaultAudiobook.copy(id = Random.nextInt(until = 10000))
        val viewModel = createViewModel(audiobook = randomIdAudiobook)

        viewModel.play()

        verify(exactly = 1) {
            fakeMediaServiceConnection.transportControls?.playFromMediaId(
                randomIdAudiobook.id.toString(),
                any()
            )
        }
    }

    @Test
    fun testJumpToTrack_transportControlsCalled() {
        val randomIdAudiobook = defaultAudiobook.copy(id = Random.nextInt(until = 10000))
        val correspondingTrackList =
            defaultTrackList.map { it.copy(parentKey = randomIdAudiobook.id) }
        val viewModel =
            createViewModel(audiobook = randomIdAudiobook, tracks = correspondingTrackList)

        viewModel.jumpToTrack(correspondingTrackList.first())

        verify {
            fakeMediaServiceConnection.transportControls?.playFromMediaId(
                randomIdAudiobook.id.toString(),
                any()
            )
        }
    }

    @Test
    fun testOnCacheButtonClick_TracksNotLoadedAudiobookNotCached() {
        val audiobook = defaultAudiobook.copy(isCached = false)
        val viewModel = createViewModel(audiobook = audiobook, tracks = emptyList())

        // Attach an observer so cacheStatus emits events
        val cacheStatus = viewModel.cacheStatus
        cacheStatus.observeForever { }
        viewModel.onCacheButtonClick()

        assertThat(
            cacheStatus.getOrAwaitValue(),
            `is`(AudiobookDetailsViewModel.CacheStatus.CACHING)
        )
        verify { mockCachedFileManager.downloadTracks(emptyList()) }
    }

    @Test
    fun testOnCacheButtonClick_TracksNotLoadedAudiobookCached() {
        val audiobook = defaultAudiobook.copy(isCached = true)
        val viewModel = spyk(createViewModel(audiobook = audiobook, tracks = emptyList()))

        // Attach an observer so cacheStatus emits events
        viewModel.showBottomSheet.observeForever {}
        viewModel.cacheStatus.observeForever {}
        viewModel.onCacheButtonClick()

        assertThat(
            viewModel.cacheStatus.getOrAwaitValue(),
            `is`(AudiobookDetailsViewModel.CacheStatus.CACHED)
        )
        verify(exactly = 0) { mockCachedFileManager.downloadTracks(any()) }
        assertThat(viewModel.showBottomSheet.getOrAwaitValue(), `is`(true))
    }

    @Test
    fun testOnCacheButtonClick_WhileCaching() {
        val uncachedAudiobook = defaultAudiobook.copy(isCached = false)
        val uncachedTracks = defaultTrackList.map { it.copy(cached = false) }
        val viewModel =
            spyk(createViewModel(audiobook = uncachedAudiobook, tracks = uncachedTracks))

        // Start caching
        viewModel.cacheStatus.observeForever {}
        viewModel.onCacheButtonClick()
        assertThat(
            viewModel.cacheStatus.getOrAwaitValue(),
            `is`(AudiobookDetailsViewModel.CacheStatus.CACHING)
        )

        // Cancel caching
        viewModel.onCacheButtonClick()
        assertThat(
            viewModel.cacheStatus.getOrAwaitValue(),
            `is`(AudiobookDetailsViewModel.CacheStatus.NOT_CACHED)
        )
        verify(exactly = 1) { mockCachedFileManager.downloadTracks(any()) }
        verify(exactly = 1) { mockCachedFileManager.cancelCaching() }
    }


    private val defaultAudiobook = Audiobook(id = 22)
    private val defaultTrackList = listOf(MediaItemTrack(parentKey = 22))

    // Create a viewmodel with optional audiobook and track list info, where the repos only emit
    // the provided tracks and audiobooks
    private fun createViewModel(
        bookRepository: IBookRepository = mockBookRepository,
        trackRepository: ITrackRepository = mockTrackRepository,
        audiobook: Audiobook = defaultAudiobook,
        tracks: List<MediaItemTrack> = defaultTrackList
    ): AudiobookDetailsViewModel {
        every { mockBookRepository.getAudiobook(any()) } returns MutableLiveData(audiobook)
        every { mockTrackRepository.getTracksForAudiobook(any()) } returns MutableLiveData(tracks)
        coEvery { mockTrackRepository.loadTracksForAudiobook(any()) } returns tracks
        return AudiobookDetailsViewModel(
            bookRepository = bookRepository,
            trackRepository = trackRepository,
            cachedFileManager = mockCachedFileManager,
            inputAudiobook = Audiobook(id = audiobook.id, isCached = audiobook.isCached),
            plexPrefsRepo = mockPlexPrefsRepo,
            mediaServiceConnection = fakeMediaServiceConnection,
            progressUpdater = mockProgressUpdater
        )
    }


}