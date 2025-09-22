package com.example.myapplication.ui.tasks

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.data.Task
import com.example.myapplication.data.TaskStatus
import com.example.myapplication.databinding.FragmentTasksBinding
import com.google.firebase.auth.FirebaseAuth

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var tasksViewModel: TasksViewModel
    private lateinit var taskAdapter: TaskAdapter

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
        observeViewModel()

        return root
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
            when {
                binding.chipAll.isChecked -> tasksViewModel.loadAllTasks()
                binding.chipPending.isChecked -> tasksViewModel.loadTasksByStatus(TaskStatus.PENDING)
                binding.chipOverdue.isChecked -> tasksViewModel.loadTasksByStatus(TaskStatus.OVERDUE)
                binding.chipCompleted.isChecked -> tasksViewModel.loadTasksByStatus(TaskStatus.COMPLETED)
                checkedIds.isEmpty() -> {
                    // If no chips are selected, default to "All"
                    binding.chipAll.isChecked = true
                    tasksViewModel.loadAllTasks()
                }
            }
        }

        // Retry button
        binding.buttonRetry.setOnClickListener {
            tasksViewModel.refresh()
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
                tasksViewModel.clearError()
            } else {
                binding.layoutErrorState.visibility = View.GONE
            }
        }

        // Observe task stats (optional - for future use)
        tasksViewModel.taskStats.observe(viewLifecycleOwner) { stats ->
            // You can use these stats later for dashboard or statistics
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
        // TODO: Show context menu (edit, delete options)
        // This will be implemented in Step 7 (Task Deletion)
        Toast.makeText(requireContext(), "Long press: ${task.name}", Toast.LENGTH_SHORT).show()
        return true
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