package ua.magerko.olxradar.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ua.magerko.olxradar.R
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.databinding.FragmentFindingsBinding

class FindingsFragment : Fragment() {

    companion object {
        const val ARG_IS_REJECTED = "arg_is_rejected"

        fun newInstance(isRejected: Boolean = false): FindingsFragment {
            return FindingsFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_REJECTED, isRejected)
                }
            }
        }
    }

    private var _binding: FragmentFindingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: FindingsViewModel
    private lateinit var adapter: FindingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), ViewModelFactory(requireContext()))[FindingsViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFindingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val initialRejected = savedInstanceState?.getBoolean(ARG_IS_REJECTED)
            ?: arguments?.getBoolean(ARG_IS_REJECTED, false)
            ?: false
        viewModel.setRejectedTab(initialRejected)

        setupTabs(initialRejected)
        setupRecycler()
        setupSwipeToDelete()
        setupObservers()

        binding.swipeRefreshFindings.setOnRefreshListener {
            binding.swipeRefreshFindings.isRefreshing = false
        }
    }

    private fun setupTabs(initialRejected: Boolean) {
        val initialIndex = if (initialRejected) 1 else 0
        binding.tabLayoutFindings.getTabAt(initialIndex)?.select()
        updateTabUi(initialRejected)

        binding.tabLayoutFindings.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val isRejected = tab?.position == 1
                viewModel.setRejectedTab(isRejected)
                updateTabUi(isRejected)
                (activity as? MainActivity)?.onTabSwitchedFromFragment(isRejected)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    fun selectTab(position: Int) {
        if (_binding == null) return
        val tab = binding.tabLayoutFindings.getTabAt(position)
        if (tab != null && !tab.isSelected) {
            tab.select()
        } else {
            val isRejected = position == 1
            viewModel.setRejectedTab(isRejected)
            updateTabUi(isRejected)
        }
    }

    private fun updateTabUi(isRejected: Boolean) {
        if (_binding == null) return
        if (isRejected) {
            binding.ivEmptyIcon.setImageResource(R.drawable.ic_rejected)
            binding.tvEmptyTitle.text = getString(R.string.rejected_empty_title)
            binding.tvEmptySubtitle.text = getString(R.string.rejected_empty_subtitle)
        } else {
            binding.ivEmptyIcon.setImageResource(R.drawable.ic_radar)
            binding.tvEmptyTitle.text = getString(R.string.findings_empty_title)
            binding.tvEmptySubtitle.text = getString(R.string.findings_empty_subtitle)
        }
    }

    private fun setupRecycler() {
        adapter = FindingsAdapter(
            onItemClick = { finding ->
                viewModel.markAsRead(finding)
                FindingDetailsBottomSheetFragment.newInstance(finding)
                    .show(childFragmentManager, "finding_details")
            },
            onDeleteClick = { finding ->
                deleteWithUndo(finding)
            },
            onAiDetailsClick = { finding ->
                FindingDetailsBottomSheetFragment.newInstance(finding)
                    .show(childFragmentManager, "finding_details")
            },
            onOpenOlxClick = { finding ->
                viewModel.markAsRead(finding)
                FindingDetailsBottomSheetFragment.newInstance(finding)
                    .show(childFragmentManager, "finding_details")
            }
        )

        binding.recyclerFindings.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFindings.adapter = adapter
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION && position < adapter.currentList.size) {
                    val finding = adapter.currentList[position]
                    deleteWithUndo(finding)
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerFindings)
    }

    fun deleteWithUndo(finding: Finding) {
        viewModel.deleteFinding(finding)
        Snackbar.make(binding.root, R.string.findings_deleted_snackbar, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_undo) {
                viewModel.restoreFinding(finding)
            }
            .show()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentFindings.collect { findings ->
                        adapter.submitList(findings)
                        binding.layoutEmptyFindings.visibility =
                            if (findings.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.isRejectedTabState.collect { isRejected ->
                        val targetPosition = if (isRejected) 1 else 0
                        if (binding.tabLayoutFindings.selectedTabPosition != targetPosition) {
                            binding.tabLayoutFindings.getTabAt(targetPosition)?.select()
                        }
                        updateTabUi(isRejected)
                    }
                }

                launch {
                    combine(viewModel.watches, viewModel.selectedWatchIdState) { watches, selectedId ->
                        watches to selectedId
                    }.collect { (watches, selectedId) ->
                        updateWatchChips(watches, selectedId)
                    }
                }
            }
        }
    }

    private fun updateWatchChips(watches: List<ua.magerko.olxradar.data.model.Watch>, selectedId: Long?) {
        val chipGroup = binding.chipGroupWatches
        chipGroup.setOnCheckedStateChangeListener(null)

        while (chipGroup.childCount > 1) {
            chipGroup.removeViewAt(1)
        }

        binding.chipAllWatches.isChecked = (selectedId == null)

        val idToWatchIdMap = mutableMapOf<Int, Long>()

        for (watch in watches) {
            val viewId = View.generateViewId()
            idToWatchIdMap[viewId] = watch.id
            val chip = Chip(requireContext()).apply {
                id = viewId
                text = watch.query
                isCheckable = true
                isChecked = (watch.id == selectedId)
                contentDescription = getString(R.string.cd_filter_chip)
            }
            chipGroup.addView(chip)
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty() || checkedIds.contains(R.id.chip_all_watches)) {
                binding.chipAllWatches.isChecked = true
                viewModel.filterByWatch(null)
            } else {
                val checkedViewId = checkedIds.first()
                val targetWatchId = idToWatchIdMap[checkedViewId]
                viewModel.filterByWatch(targetWatchId)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(ARG_IS_REJECTED, viewModel.isRejectedTabState.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
