package com.example.myapplication.ui.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.CreateTaskRequest
import com.example.myapplication.data.DateFilter
import com.example.myapplication.data.SortOption
import com.example.myapplication.data.Task
import com.example.myapplication.data.TaskFilters
import com.example.myapplication.data.TaskRepository
import com.example.myapplication.data.TaskStats
import com.example.myapplication.data.TaskStatus
import com.example.myapplication.data.UpdateTaskRequest
import kotlinx.coroutines.launch
import java.util.Date

class TasksViewModel : ViewModel() {

    private val repository = TaskRepository()

    // LiveData for tasks
    private val _tasks = MutableLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> = _tasks

    // LiveData for loading state
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData for error messages
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // LiveData for task statistics
    private val _taskStats = MutableLiveData<TaskStats>()
    val taskStats: LiveData<TaskStats> = _taskStats

    // Current filter status
    private val _currentFilter = MutableLiveData<TaskStatus?>()
    val currentFilter: LiveData<TaskStatus?> = _currentFilter

    // Current filters and sorting
    private val _currentFilters = MutableLiveData<TaskFilters>()
    val currentFilters: LiveData<TaskFilters> = _currentFilters

    init {
        _currentFilters.value = TaskFilters() // Default filters
        loadAllTasks()
        loadTaskStats()
    }

    // Load tasks with enhanced filters
    fun loadTasksWithFilters(filters: TaskFilters) {
        android.util.Log.d("TasksViewModel", "Loading tasks with filters: $filters")
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = repository.getTasksWithFilters(filters)
            _isLoading.value = false

            if (result.isSuccess) {
                val tasks = result.getOrNull() ?: emptyList()
                android.util.Log.d("TasksViewModel", "Loaded ${tasks.size} filtered tasks")
                _tasks.value = tasks
                _currentFilters.value = filters
                // Update legacy filter for compatibility
                _currentFilter.value = filters.status
            } else {
                android.util.Log.e("TasksViewModel", "Error loading filtered tasks: ${result.exceptionOrNull()?.message}")
                _error.value = result.exceptionOrNull()?.message ?: "Failed to load tasks"
            }
        }
    }

    // Update sorting
    fun updateSorting(sortOption: SortOption) {
        val currentFilters = _currentFilters.value ?: TaskFilters()
        val newFilters = currentFilters.copy(sortOption = sortOption)
        loadTasksWithFilters(newFilters)
    }

    // Update date filter
    fun updateDateFilter(dateFilter: DateFilter, startDate: Date? = null, endDate: Date? = null) {
        val currentFilters = _currentFilters.value ?: TaskFilters()
        val newFilters = currentFilters.copy(
            dateFilter = dateFilter,
            customStartDate = startDate,
            customEndDate = endDate
        )
        loadTasksWithFilters(newFilters)
    }

    // Legacy methods - updated to use new filtering system
    fun loadAllTasks() {
        android.util.Log.d("TasksViewModel", "loadAllTasks() called")
        val filters = TaskFilters(status = null)
        loadTasksWithFilters(filters)
    }

    fun loadTasksByStatus(status: TaskStatus) {
        val currentFilters = _currentFilters.value ?: TaskFilters()
        val newFilters = currentFilters.copy(status = status)
        loadTasksWithFilters(newFilters)
    }

    // Load tasks for date range
    fun loadTasksByDateRange(startDate: Date, endDate: Date) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = repository.getTasksByDateRange(startDate, endDate)
            _isLoading.value = false

            if (result.isSuccess) {
                _tasks.value = result.getOrNull() ?: emptyList()
                _currentFilter.value = null
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to load tasks"
            }
        }
    }

    // Create new task
    fun createTask(name: String, details: String, dueDate: Date, userId: String) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val request = CreateTaskRequest(name, details, dueDate, userId)
            val result = repository.createTask(request)

            if (result.isSuccess) {
                // Refresh tasks after creating
                refreshCurrentView()
                loadTaskStats()
            } else {
                _isLoading.value = false
                _error.value = result.exceptionOrNull()?.message ?: "Failed to create task"
            }
        }
    }

    // Update existing task
    fun updateTask(taskId: String, request: UpdateTaskRequest) {
        viewModelScope.launch {
            val result = repository.updateTask(taskId, request)

            if (result.isSuccess) {
                refreshCurrentView()
                loadTaskStats()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to update task"
            }
        }
    }

    // Toggle task completion
    fun toggleTaskCompletion(taskId: String) {
        viewModelScope.launch {
            val result = repository.toggleTaskCompletion(taskId)

            if (result.isSuccess) {
                refreshCurrentView()
                loadTaskStats()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to update task"
            }
        }
    }

    // Delete task
    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val result = repository.deleteTask(taskId)

            if (result.isSuccess) {
                refreshCurrentView()
                loadTaskStats()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to delete task"
            }
        }
    }

    // Search tasks
    fun searchTasks(query: String) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = repository.searchTasks(query)
            _isLoading.value = false

            if (result.isSuccess) {
                _tasks.value = result.getOrNull() ?: emptyList()
                _currentFilter.value = null
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to search tasks"
            }
        }
    }

    // Load task statistics
    private fun loadTaskStats() {
        viewModelScope.launch {
            val result = repository.getTaskStats()

            if (result.isSuccess) {
                _taskStats.value = result.getOrNull()
            } else {
                // Don't show error for stats, it's not critical
                _taskStats.value = TaskStats(0, 0, 0, 0)
            }
        }
    }

    // Refresh current view based on current filters
    private fun refreshCurrentView() {
        val currentFilters = _currentFilters.value ?: TaskFilters()
        loadTasksWithFilters(currentFilters)
    }

    // Clear error message
    fun clearError() {
        _error.value = null
    }

    // Refresh data
    fun refresh() {
        val currentFilters = _currentFilters.value ?: TaskFilters()
        loadTasksWithFilters(currentFilters)
        loadTaskStats()
    }
}