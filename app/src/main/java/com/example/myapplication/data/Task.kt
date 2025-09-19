package com.example.myapplication.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class Task(
    @DocumentId
    val id: String = "",
    
    @PropertyName("name")
    val name: String = "",
    
    @PropertyName("details")
    val details: String = "",
    
    @PropertyName("due_date")
    val dueDate: Timestamp = Timestamp.now(),
    
    @PropertyName("is_completed")
    val isCompleted: Boolean = false,
    
    @PropertyName("completed_date")
    val completedDate: Timestamp? = null,
    
    @PropertyName("user_id")
    val userId: String = "",
    
    @PropertyName("created_date")
    val createdDate: Timestamp = Timestamp.now()
) {
    // No-argument constructor required for Firestore
    constructor() : this("", "", "", Timestamp.now(), false, null, "", Timestamp.now())
    
    // Helper methods for easier date handling
    fun getDueDateAsDate(): Date = dueDate.toDate()
    fun getCreatedDateAsDate(): Date = createdDate.toDate()
    fun getCompletedDateAsDate(): Date? = completedDate?.toDate()
    
    // Check if task is overdue
    fun isOverdue(): Boolean {
        return !isCompleted && dueDate.toDate().before(Date())
    }
    
    // Get task status as enum
    fun getStatus(): TaskStatus {
        return when {
            isCompleted -> TaskStatus.COMPLETED
            isOverdue() -> TaskStatus.OVERDUE
            else -> TaskStatus.PENDING
        }
    }
    
    // Create a copy with completion status toggled
    fun toggleCompletion(): Task {
        return copy(
            isCompleted = !isCompleted,
            completedDate = if (!isCompleted) Timestamp.now() else null
        )
    }
}

enum class TaskStatus {
    PENDING,
    OVERDUE, 
    COMPLETED
}

// Data class for creating new tasks (without auto-generated fields)
data class CreateTaskRequest(
    val name: String,
    val details: String,
    val dueDate: Date,
    val userId: String
) {
    fun toTask(): Task {
        return Task(
            name = name,
            details = details,
            dueDate = Timestamp(dueDate),
            userId = userId,
            createdDate = Timestamp.now()
        )
    }
}

// Data class for updating existing tasks
data class UpdateTaskRequest(
    val name: String? = null,
    val details: String? = null,
    val dueDate: Date? = null,
    val isCompleted: Boolean? = null
)