package io.github.mattpvaughn.chronicle.features.home

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.github.mattpvaughn.chronicle.R
import io.github.mattpvaughn.chronicle.application.Injector
import io.github.mattpvaughn.chronicle.application.MainActivity
import io.github.mattpvaughn.chronicle.data.model.Audiobook
import io.github.mattpvaughn.chronicle.data.plex.PlexPrefsRepo
import io.github.mattpvaughn.chronicle.databinding.FragmentHomeBinding
import io.github.mattpvaughn.chronicle.features.library.AudiobookAdapter
import io.github.mattpvaughn.chronicle.features.library.AudiobookSearchAdapter
import io.github.mattpvaughn.chronicle.features.library.LibraryFragment
import io.github.mattpvaughn.chronicle.features.settings.PrefsRepo


class HomeFragment : Fragment() {
    private lateinit var viewModel: HomeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val prefsRepo: PrefsRepo = Injector.get().prefsRepo()
        val plexPrefsRepo: PlexPrefsRepo = Injector.get().plexPrefs()
        viewModel = HomeViewModel(
            bookRepository = Injector.get().bookRepo(),
            trackRepository = Injector.get().trackRepo(),
            plexPrefsRepo = plexPrefsRepo,
            prefsRepo = prefsRepo
        )

        val binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        binding.recentlyAddedRecyclerview.adapter = makeAudiobookAdapter()
        binding.onDeckRecyclerview.adapter = makeAudiobookAdapter()
        binding.downloadedRecyclerview.adapter = makeAudiobookAdapter()
        binding.searchResultsList.adapter =
            AudiobookSearchAdapter(object : LibraryFragment.AudiobookClick {
                override fun onClick(audiobook: Audiobook) {
                    openAudiobookDetails(audiobook)
                }
            })

        (activity as MainActivity).setSupportActionBar(binding.toolbar)

        // Inflate the layout for this fragment
        return binding.root
    }

    private fun makeAudiobookAdapter(): AudiobookAdapter {
        return AudiobookAdapter(
            isVertical = false,
            audiobookClick = object : LibraryFragment.AudiobookClick {
                override fun onClick(audiobook: Audiobook) {
                    openAudiobookDetails(audiobook)
                }
            }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.home_menu, menu)
        val searchView = menu.findItem(R.id.search).actionView as SearchView
        val searchItem = menu.findItem(R.id.search) as MenuItem

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                viewModel.setSearchActive(true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                viewModel.setSearchActive(false)
                return true
            }
        })

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Do nothing
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText != null) {
                    viewModel.search(newText)
                }
                return true
            }

        })
    }

    fun openAudiobookDetails(audiobook: Audiobook) {
        val navController = findNavController()
        // Ensure nav controller doesn't queue two actions if a navigation event occurs twice in
        // rapid succession
        if (navController.currentDestination?.id == R.id.nav_home) {
            val action = HomeFragmentDirections.actionNavHomeToAudiobookDetailsFragment2(
                audiobook.isCached,
                audiobook.id
            )
            navController.navigate(action)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() = HomeFragment()
    }

}