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

    // Search state
    private val _searchQuery = MutableLiveData<String>()
    val searchQuery: LiveData<String> = _searchQuery

    private val _isSearchActive = MutableLiveData<Boolean>()
    val isSearchActive: LiveData<Boolean> = _isSearchActive

    init {
        _currentFilters.value = TaskFilters() // Default filters
        _searchQuery.value = ""
        _isSearchActive.value = false
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

    // Search functionality
    fun searchTasks(query: String) {
        android.util.Log.d("TasksViewModel", "Search query: '$query'")
        _searchQuery.value = query
        _isSearchActive.value = query.isNotBlank()

        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val currentFilters = _currentFilters.value ?: TaskFilters()
            val result = repository.searchTasksWithFilters(query, currentFilters)
            _isLoading.value = false

            if (result.isSuccess) {
                val tasks = result.getOrNull() ?: emptyList()
                android.util.Log.d("TasksViewModel", "Search returned ${tasks.size} tasks")
                _tasks.value = tasks
            } else {
                android.util.Log.e("TasksViewModel", "Error searching tasks: ${result.exceptionOrNull()?.message}")
                _error.value = result.exceptionOrNull()?.message ?: "Failed to search tasks"
            }
        }
    }

    // Clear search
    fun clearSearch() {
        _searchQuery.value = ""
        _isSearchActive.value = false
        // Reload tasks with current filters
        val currentFilters = _currentFilters.value ?: TaskFilters()
        loadTasksWithFilters(currentFilters)
    }

    // Check if search is active
    fun isSearching(): Boolean = _isSearchActive.value == true
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