package com.example.myapplication.ui.tasks

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.data.DateFilter
import com.example.myapplication.data.SortOption
import com.example.myapplication.data.Task
import com.example.myapplication.data.TaskFilters
import com.example.myapplication.data.TaskStatus
import com.example.myapplication.databinding.FragmentTasksBinding
import com.google.firebase.auth.FirebaseAuth
import java.util.*

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var tasksViewModel: TasksViewModel
    private lateinit var taskAdapter: TaskAdapter

    // Current filters
    private var currentDateFilter = DateFilter.ALL
    private var currentSortOption = SortOption.DUE_DATE_ASC
    private var customStartDate: Date? = null
    private var customEndDate: Date? = null

    // Activity result launcher for add/edit task
    private val addEditTaskLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d("TasksFragment", "Activity result received: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            android.util.Log.d("TasksFragment", "Task added/edited successfully, refreshing list")
            // Refresh the task list after adding/editing
            tasksViewModel.refresh()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        tasksViewModel = ViewModelProvider(this)[TasksViewModel::class.java]
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerView()
        setupFilterChips()
        setupDateFilter()
        setupSortButton()
        setupSearchFunctionality()
        observeViewModel()

        // Initialize with default display
        initializeDefaultView()

        return root
    }

    private fun initializeDefaultView() {
        // Set default text for sort button (shortened)
        binding.btnSort.text = "Sort: Due Date"

        // Set default date filter
        binding.dropdownDateFilter.setText(currentDateFilter.displayName, false)
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onTaskClick = { task ->
                onTaskClick(task)
            },
            onTaskToggle = { task ->
                onTaskToggle(task)
            },
            onTaskLongClick = { task ->
                onTaskLongClick(task)
            }
        )

        binding.recyclerViewTasks.apply {
            adapter = taskAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupFilters.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedStatus = when {
                binding.chipAll.isChecked -> null
                binding.chipPending.isChecked -> TaskStatus.PENDING
                binding.chipOverdue.isChecked -> TaskStatus.OVERDUE
                binding.chipCompleted.isChecked -> TaskStatus.COMPLETED
                checkedIds.isEmpty() -> {
                    // If no chips are selected, default to "All"
                    binding.chipAll.isChecked = true
                    null
                }
                else -> null
            }

            // Apply filters with current settings
            applyFilters(selectedStatus)
        }

        // Retry button
        binding.buttonRetry.setOnClickListener {
            tasksViewModel.refresh()
        }
    }

    private fun setupDateFilter() {
        // Setup date filter dropdown
        val dateFilterOptions = DateFilter.values().map { it.displayName }.toTypedArray()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dateFilterOptions)
        binding.dropdownDateFilter.setAdapter(adapter)

        binding.dropdownDateFilter.setOnItemClickListener { _, _, position, _ ->
            val selectedDateFilter = DateFilter.values()[position]
            onDateFilterSelected(selectedDateFilter)
        }
    }

    private fun setupSortButton() {
        binding.btnSort.setOnClickListener {
            showSortDialog()
        }
    }

    private fun applyFilters(status: TaskStatus?) {
        val filters = TaskFilters(
            status = status,
            dateFilter = currentDateFilter,
            customStartDate = customStartDate,
            customEndDate = customEndDate,
            sortOption = currentSortOption
        )

        // If search is active, search with filters, otherwise just load with filters
        if (tasksViewModel.isSearching()) {
            val currentQuery = tasksViewModel.searchQuery.value ?: ""
            tasksViewModel.searchTasks(currentQuery)
        } else {
            tasksViewModel.loadTasksWithFilters(filters)
        }
    }

    private fun onDateFilterSelected(dateFilter: DateFilter) {
        when (dateFilter) {
            DateFilter.CUSTOM_RANGE -> {
                showDateRangePicker()
            }
            else -> {
                currentDateFilter = dateFilter
                customStartDate = null
                customEndDate = null
                applyFilters(getCurrentSelectedStatus())
            }
        }
    }

    private fun getCurrentSelectedStatus(): TaskStatus? {
        return when {
            binding.chipPending.isChecked -> TaskStatus.PENDING
            binding.chipOverdue.isChecked -> TaskStatus.OVERDUE
            binding.chipCompleted.isChecked -> TaskStatus.COMPLETED
            else -> null
        }
    }

    private fun showSortDialog() {
        val sortOptions = SortOption.values().map { it.displayName }.toTypedArray()
        val currentIndex = SortOption.values().indexOf(currentSortOption)

        AlertDialog.Builder(requireContext())
            .setTitle("Sort Tasks")
            .setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                currentSortOption = SortOption.values()[which]
                // Use shortened text for button display
                val shortName = when (currentSortOption) {
                    SortOption.DUE_DATE_ASC -> "Due Date"
                    SortOption.DUE_DATE_DESC -> "Due Date ↓"
                    SortOption.NAME_ASC -> "Name A-Z"
                    SortOption.NAME_DESC -> "Name Z-A"
                    SortOption.CREATED_DATE_ASC -> "Oldest"
                    SortOption.CREATED_DATE_DESC -> "Newest"
                }
                binding.btnSort.text = shortName
                applyFilters(getCurrentSelectedStatus())
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDateRangePicker() {
        val calendar = Calendar.getInstance()

        // Start date picker
        DatePickerDialog(requireContext(), { _, startYear, startMonth, startDay ->
            val startCalendar = Calendar.getInstance()
            startCalendar.set(startYear, startMonth, startDay, 0, 0, 0)
            customStartDate = startCalendar.time

            // End date picker
            DatePickerDialog(requireContext(), { _, endYear, endMonth, endDay ->
                val endCalendar = Calendar.getInstance()
                endCalendar.set(endYear, endMonth, endDay, 23, 59, 59)
                customEndDate = endCalendar.time

                currentDateFilter = DateFilter.CUSTOM_RANGE
                binding.dropdownDateFilter.setText("Custom Range", false)
                applyFilters(getCurrentSelectedStatus())

            }, startYear, startMonth, startDay + 1).show()

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setupSearchFunctionality() {
        // Search toggle button
        binding.btnToggleSearch.setOnClickListener {
            toggleSearchVisibility()
        }

        // Search text change listener with debounce
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            private var searchRunnable: Runnable? = null

            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { binding.searchEditText.removeCallbacks(it) }
                searchRunnable = Runnable {
                    val query = s.toString().trim()
                    tasksViewModel.searchTasks(query)
                }
                binding.searchEditText.postDelayed(searchRunnable, 300) // 300ms debounce
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Clear search button
        binding.btnClearSearch.setOnClickListener {
            binding.searchEditText.text?.clear()
            tasksViewModel.clearSearch()
        }

        // Handle search submit
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchEditText.text.toString().trim()
                tasksViewModel.searchTasks(query)
                // Hide keyboard
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    private fun toggleSearchVisibility() {
        val isVisible = binding.searchCard.visibility == View.VISIBLE

        if (isVisible) {
            // Hide search
            binding.searchCard.visibility = View.GONE
            binding.btnToggleSearch.text = "Search"
            binding.searchEditText.text?.clear()
            tasksViewModel.clearSearch()
        } else {
            // Show search
            binding.searchCard.visibility = View.VISIBLE
            binding.btnToggleSearch.text = "Hide"
            binding.searchEditText.requestFocus()
            // Show keyboard
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun observeViewModel() {
        // Observe tasks
        tasksViewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            taskAdapter.submitList(tasks)
            updateEmptyState(tasks.isEmpty() && !(tasksViewModel.isLoading.value ?: false))
        }

        // Observe loading state
        tasksViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

            // Hide other states when loading
            if (isLoading) {
                binding.layoutEmptyState.visibility = View.GONE
                binding.layoutErrorState.visibility = View.GONE
            }
        }

        // Observe errors
        tasksViewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                showErrorState(error)
                // Also show toast for delete errors
                if (error.contains("delete", ignoreCase = true)) {
                    Toast.makeText(requireContext(), "Failed to delete task: $error", Toast.LENGTH_LONG).show()
                }
                tasksViewModel.clearError()
            } else {
                binding.layoutErrorState.visibility = View.GONE
            }
        }

        // Observe task stats (optional - for future use)
        tasksViewModel.taskStats.observe(viewLifecycleOwner) { stats ->
            // You can use these stats later for dashboard or statistics
        }

        // Observe search state
        tasksViewModel.isSearchActive.observe(viewLifecycleOwner) { isActive ->
            updateEmptyStateForSearch(isActive)
        }

        tasksViewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            // Update UI based on search query if needed
        }
    }

    private fun updateEmptyStateForSearch(isSearchActive: Boolean) {
        val isEmpty = tasksViewModel.tasks.value?.isEmpty() == true &&
                !(tasksViewModel.isLoading.value ?: false)

        if (isEmpty && isSearchActive) {
            binding.textEmptyState.text = "No tasks found for your search"
        } else {
            updateEmptyState(isEmpty)
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewTasks.visibility = if (isEmpty) View.GONE else View.VISIBLE

        // Update empty state message based on current filter
        val message = when {
            binding.chipPending.isChecked -> "No pending tasks"
            binding.chipOverdue.isChecked -> "No overdue tasks"
            binding.chipCompleted.isChecked -> "No completed tasks"
            else -> "No tasks found"
        }
        binding.textEmptyState.text = message
    }

    private fun showErrorState(errorMessage: String) {
        binding.layoutErrorState.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE
        binding.recyclerViewTasks.visibility = View.GONE
        binding.textErrorMessage.text = errorMessage
    }

    private fun onTaskClick(task: Task) {
        // Open task for editing
        val intent = AddEditTaskActivity.newEditIntent(requireContext(), task.id)
        addEditTaskLauncher.launch(intent)
    }

    private fun onTaskToggle(task: Task) {
        // Toggle task completion
        tasksViewModel.toggleTaskCompletion(task.id)

        val message = if (task.isCompleted) "Task marked as pending" else "Task completed!"
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun onTaskLongClick(task: Task): Boolean {
        // Show context menu with delete option
        showTaskContextMenu(task)
        return true
    }

    private fun showTaskContextMenu(task: Task) {
        val options = arrayOf("Edit Task", "Delete Task")

        AlertDialog.Builder(requireContext())
            .setTitle(task.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onTaskClick(task) // Edit task
                    1 -> confirmDeleteTask(task) // Delete task
                }
            }
            .show()
    }

    private fun confirmDeleteTask(task: Task) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete \"${task.name}\"?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteTask(task)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTask(task: Task) {
        tasksViewModel.deleteTask(task.id)
        Toast.makeText(requireContext(), "Task \"${task.name}\" deleted", Toast.LENGTH_SHORT).show()
    }

    // Public method to be called from MainActivity FAB
    fun addNewTask() {
        val intent = AddEditTaskActivity.newIntent(requireContext())
        addEditTaskLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}