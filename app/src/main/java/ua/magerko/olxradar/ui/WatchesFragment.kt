package ua.magerko.olxradar.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import ua.magerko.olxradar.R
import ua.magerko.olxradar.databinding.FragmentWatchesBinding

class WatchesFragment : Fragment() {

    private var _binding: FragmentWatchesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: WatchesViewModel
    private lateinit var adapter: WatchesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), ViewModelFactory(requireContext()))[WatchesViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWatchesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecycler()
        setupFab()
        setupObservers()
    }

    private fun setupRecycler() {
        adapter = WatchesAdapter(
            onCardClick = { watch ->
                // C8: Edit watch in BottomSheet
                EditWatchBottomSheetFragment.newInstance(watch.id)
                    .show(parentFragmentManager, "edit_watch")
            },
            onToggleActive = { watch, isActive ->
                viewModel.toggleActive(watch, isActive)
            },
            onDuplicateClick = { watch ->
                viewModel.duplicateWatch(watch)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.watch_duplicated_toast, watch.query),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDeleteClick = { watch ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete_watch_confirm_title)
                    .setMessage(getString(R.string.delete_watch_confirm_msg, watch.query))
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        viewModel.deleteWatch(watch)
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
        )

        binding.recyclerWatches.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerWatches.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddWatch.setOnClickListener {
            // C8: Create watch in BottomSheet
            EditWatchBottomSheetFragment.newInstance(null)
                .show(parentFragmentManager, "create_watch")
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.watchesUi.collect { watches ->
                    adapter.submitList(watches)
                    binding.layoutEmptyWatches.visibility =
                        if (watches.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
