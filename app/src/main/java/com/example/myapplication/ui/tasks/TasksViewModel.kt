package com.example.myapplication.ui.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.CreateTaskRequest
import com.example.myapplication.data.Task
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

    init {
        loadAllTasks()
        loadTaskStats()
    }

    // Load all tasks
    fun loadAllTasks() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = repository.getAllTasks()
            _isLoading.value = false

            if (result.isSuccess) {
                _tasks.value = result.getOrNull() ?: emptyList()
                _currentFilter.value = null
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to load tasks"
            }
        }
    }

    // Load tasks by status
    fun loadTasksByStatus(status: TaskStatus) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = repository.getTasksByStatus(status)
            _isLoading.value = false

            if (result.isSuccess) {
                _tasks.value = result.getOrNull() ?: emptyList()
                _currentFilter.value = status
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to load tasks"
            }
        }
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

    // Refresh current view based on current filter
    private fun refreshCurrentView() {
        val filter = _currentFilter.value
        if (filter != null) {
            loadTasksByStatus(filter)
        } else {
            loadAllTasks()
        }
    }

    // Clear error message
    fun clearError() {
        _error.value = null
    }

    // Refresh data
    fun refresh() {
        refreshCurrentView()
        loadTaskStats()
    }
}