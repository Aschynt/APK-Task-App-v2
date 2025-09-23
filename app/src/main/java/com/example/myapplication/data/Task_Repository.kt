package com.example.myapplication.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.Date

class TaskRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getCurrentUserId(): String {
        val uid = auth.currentUser?.uid
        android.util.Log.d("TaskRepository", "Current user ID: $uid")
        return uid ?: throw IllegalStateException("User not authenticated")
    }

    private fun getUserTasksCollection() = db.collection("users")
        .document(getCurrentUserId())
        .collection("tasks").also {
            android.util.Log.d("TaskRepository", "Query path: users/${getCurrentUserId()}/tasks")
        }

    // CREATE - Add new task
    suspend fun createTask(request: CreateTaskRequest): Result<String> {
        return try {
            val task = request.toTask()
            android.util.Log.d("TaskRepository", "Creating task: ${task.name} for user: ${task.userId}")
            android.util.Log.d("TaskRepository", "Task details - due: ${task.dueDate}, created: ${task.createdDate}")
            val docRef = getUserTasksCollection().add(task).await()
            android.util.Log.d("TaskRepository", "Task created with ID: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("TaskRepository", "Failed to create task", e)
            Result.failure(e)
        }
    }

    // READ - Get all tasks for current user
    suspend fun getAllTasks(): Result<List<Task>> {
        return try {
            val snapshot = getUserTasksCollection()
                .orderBy("dueDate", Query.Direction.ASCENDING)
                .get()
                .await()

            android.util.Log.d("TaskRepository", "Raw documents received: ${snapshot.documents.size}")
            snapshot.documents.forEach { doc ->
                android.util.Log.d("TaskRepository", "Document ${doc.id}: ${doc.data}")
            }

            val tasks = snapshot.toObjects(Task::class.java)
            android.util.Log.d("TaskRepository", "Converted tasks: ${tasks.size}")
            Result.success(tasks)
        } catch (e: Exception) {
            android.util.Log.e("TaskRepository", "Error in getAllTasks", e)
            Result.failure(e)
        }
    }

    // READ - Get tasks by status
    suspend fun getTasksByStatus(status: TaskStatus): Result<List<Task>> {
        return try {
            android.util.Log.d("TaskRepository", "Filtering tasks by status: $status")
            val tasks = when (status) {
                TaskStatus.COMPLETED -> {
                    val snapshot = getUserTasksCollection()
                        .whereEqualTo("completed", true)
                        .get()
                        .await()
                    android.util.Log.d("TaskRepository", "Completed tasks query returned ${snapshot.size()} documents")
                    snapshot.toObjects(Task::class.java)
                }
                TaskStatus.PENDING -> {
                    val allTasks = getUserTasksCollection()
                        .whereEqualTo("completed", false)
                        .get()
                        .await()
                        .toObjects(Task::class.java)
                    android.util.Log.d("TaskRepository", "Non-completed tasks: ${allTasks.size}")
                    val pendingTasks = allTasks.filter { !it.isOverdue() }
                    android.util.Log.d("TaskRepository", "Pending tasks after filter: ${pendingTasks.size}")
                    pendingTasks
                }
                TaskStatus.OVERDUE -> {
                    val allTasks = getUserTasksCollection()
                        .whereEqualTo("completed", false)
                        .get()
                        .await()
                        .toObjects(Task::class.java)
                    android.util.Log.d("TaskRepository", "Non-completed tasks: ${allTasks.size}")
                    val overdueTasks = allTasks.filter { it.isOverdue() }
                    android.util.Log.d("TaskRepository", "Overdue tasks after filter: ${overdueTasks.size}")
                    overdueTasks
                }
            }
            Result.success(tasks)
        } catch (e: Exception) {
            android.util.Log.e("TaskRepository", "Error filtering tasks by status: $status", e)
            Result.failure(e)
        }
    }

    // READ - Get tasks for a specific date range
    suspend fun getTasksByDateRange(startDate: Date, endDate: Date): Result<List<Task>> {
        return try {
            val snapshot = getUserTasksCollection()
                .whereGreaterThanOrEqualTo("dueDate", Timestamp(startDate))
                .whereLessThanOrEqualTo("dueDate", Timestamp(endDate))
                .orderBy("dueDate", Query.Direction.ASCENDING)
                .get()
                .await()

            val tasks = snapshot.toObjects(Task::class.java)
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // READ - Get single task by ID
    suspend fun getTaskById(taskId: String): Result<Task?> {
        return try {
            val snapshot = getUserTasksCollection()
                .document(taskId)
                .get()
                .await()

            val task = snapshot.toObject(Task::class.java)
            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // UPDATE - Update existing task
    suspend fun updateTask(taskId: String, request: UpdateTaskRequest): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any?>()

            request.name?.let { updates["name"] = it }
            request.details?.let { updates["details"] = it }
            request.dueDate?.let { updates["dueDate"] = Timestamp(it) }
            request.isCompleted?.let {
                updates["completed"] = it
                if (it) {
                    updates["completedDate"] = Timestamp.now()
                } else {
                    updates["completedDate"] = com.google.firebase.firestore.FieldValue.delete()
                }
            }

            if (updates.isNotEmpty()) {
                getUserTasksCollection()
                    .document(taskId)
                    .update(updates)
                    .await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // UPDATE - Toggle task completion status
    suspend fun toggleTaskCompletion(taskId: String): Result<Unit> {
        return try {
            val taskResult = getTaskById(taskId)
            if (taskResult.isFailure) {
                return Result.failure(taskResult.exceptionOrNull() ?: Exception("Failed to get task"))
            }

            val task = taskResult.getOrNull()
                ?: return Result.failure(Exception("Task not found"))

            val updates = mapOf<String, Any?>(
                "completed" to !task.isCompleted,
                "completedDate" to if (!task.isCompleted) Timestamp.now() else com.google.firebase.firestore.FieldValue.delete()
            )

            getUserTasksCollection()
                .document(taskId)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // DELETE - Remove task
    suspend fun deleteTask(taskId: String): Result<Unit> {
        return try {
            getUserTasksCollection()
                .document(taskId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // REAL-TIME - Get tasks as Flow for real-time updates
    fun getTasksFlow(): Flow<Result<List<Task>>> = flow {
        try {
            getUserTasksCollection()
                .orderBy("due_date", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Handle error in flow
                        return@addSnapshotListener
                    }

                    snapshot?.let {
                        val tasks = it.toObjects(Task::class.java)
                        // Emit would go here, but we need to handle this differently
                        // This is a simplified version - in practice, you'd use callbackFlow
                    }
                }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // UTILITY - Search tasks by name or details
    suspend fun searchTasks(query: String): Result<List<Task>> {
        return try {
            val allTasksResult = getAllTasks()
            if (allTasksResult.isFailure) {
                return allTasksResult
            }

            val allTasks = allTasksResult.getOrNull() ?: emptyList()
            val filteredTasks = allTasks.filter { task ->
                task.name.contains(query, ignoreCase = true) ||
                        task.details.contains(query, ignoreCase = true)
            }

            Result.success(filteredTasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ENHANCED - Get tasks with filtering and sorting
    suspend fun getTasksWithFilters(filters: TaskFilters): Result<List<Task>> {
        return try {
            android.util.Log.d("TaskRepository", "Getting tasks with filters: $filters")

            // First apply status filter if specified
            val baseQuery = getUserTasksCollection()
            val tasks = if (filters.status != null) {
                when (filters.status) {
                    TaskStatus.COMPLETED -> {
                        baseQuery.whereEqualTo("completed", true)
                            .get()
                            .await()
                            .toObjects(Task::class.java)
                    }
                    TaskStatus.PENDING -> {
                        baseQuery.whereEqualTo("completed", false)
                            .get()
                            .await()
                            .toObjects(Task::class.java)
                            .filter { !it.isOverdue() }
                    }
                    TaskStatus.OVERDUE -> {
                        baseQuery.whereEqualTo("completed", false)
                            .get()
                            .await()
                            .toObjects(Task::class.java)
                            .filter { it.isOverdue() }
                    }
                }
            } else {
                // Get all tasks
                baseQuery.get()
                    .await()
                    .toObjects(Task::class.java)
            }

            // Apply date filtering
            val dateFilteredTasks = applyDateFilter(tasks, filters)

            // Apply sorting
            val sortedTasks = applySorting(dateFilteredTasks, filters.sortOption)

            android.util.Log.d("TaskRepository", "Filtered and sorted tasks: ${sortedTasks.size}")
            Result.success(sortedTasks)

        } catch (e: Exception) {
            android.util.Log.e("TaskRepository", "Error getting filtered tasks", e)
            Result.failure(e)
        }
    }

    private fun applyDateFilter(tasks: List<Task>, filters: TaskFilters): List<Task> {
        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        return when (filters.dateFilter) {
            DateFilter.ALL -> tasks

            DateFilter.TODAY -> {
                val endOfToday = today.clone() as java.util.Calendar
                endOfToday.add(java.util.Calendar.DAY_OF_MONTH, 1)
                tasks.filter { task ->
                    val dueDate = task.getDueDateAsDate()
                    dueDate.after(today.time) && dueDate.before(endOfToday.time)
                }
            }

            DateFilter.THIS_WEEK -> {
                val startOfWeek = today.clone() as java.util.Calendar
                startOfWeek.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
                val endOfWeek = startOfWeek.clone() as java.util.Calendar
                endOfWeek.add(java.util.Calendar.WEEK_OF_YEAR, 1)

                tasks.filter { task ->
                    val dueDate = task.getDueDateAsDate()
                    dueDate.after(startOfWeek.time) && dueDate.before(endOfWeek.time)
                }
            }

            DateFilter.THIS_MONTH -> {
                val startOfMonth = today.clone() as java.util.Calendar
                startOfMonth.set(java.util.Calendar.DAY_OF_MONTH, 1)
                val endOfMonth = startOfMonth.clone() as java.util.Calendar
                endOfMonth.add(java.util.Calendar.MONTH, 1)

                tasks.filter { task ->
                    val dueDate = task.getDueDateAsDate()
                    dueDate.after(startOfMonth.time) && dueDate.before(endOfMonth.time)
                }
            }

            DateFilter.OVERDUE -> {
                tasks.filter { task ->
                    !task.isCompleted && task.getDueDateAsDate().before(today.time)
                }
            }

            DateFilter.CUSTOM_RANGE -> {
                if (filters.customStartDate != null && filters.customEndDate != null) {
                    tasks.filter { task ->
                        val dueDate = task.getDueDateAsDate()
                        dueDate.after(filters.customStartDate) && dueDate.before(filters.customEndDate)
                    }
                } else {
                    tasks
                }
            }
        }
    }

    private fun applySorting(tasks: List<Task>, sortOption: SortOption): List<Task> {
        return when (sortOption) {
            SortOption.DUE_DATE_ASC -> tasks.sortedBy { it.getDueDateAsDate() }
            SortOption.DUE_DATE_DESC -> tasks.sortedByDescending { it.getDueDateAsDate() }
            SortOption.NAME_ASC -> tasks.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> tasks.sortedByDescending { it.name.lowercase() }
            SortOption.CREATED_DATE_ASC -> tasks.sortedBy { it.getCreatedDateAsDate() }
            SortOption.CREATED_DATE_DESC -> tasks.sortedByDescending { it.getCreatedDateAsDate() }
        }
    }
    suspend fun getTaskStats(): Result<TaskStats> {
        return try {
            val allTasksResult = getAllTasks()
            if (allTasksResult.isFailure) {
                return Result.failure(allTasksResult.exceptionOrNull() ?: Exception("Failed to get tasks"))
            }

            val tasks = allTasksResult.getOrNull() ?: emptyList()
            val stats = TaskStats(
                totalTasks = tasks.size,
                completedTasks = tasks.count { it.isCompleted },
                pendingTasks = tasks.count { !it.isCompleted && !it.isOverdue() },
                overdueTasks = tasks.count { it.isOverdue() }
            )

            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Data class for task statistics
data class TaskStats(
    val totalTasks: Int,
    val completedTasks: Int,
    val pendingTasks: Int,
    val overdueTasks: Int
) {
    val completionPercentage: Float
        get() = if (totalTasks > 0) (completedTasks.toFloat() / totalTasks) * 100 else 0f
}