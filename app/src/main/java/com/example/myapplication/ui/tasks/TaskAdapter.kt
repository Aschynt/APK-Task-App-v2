package com.example.myapplication.ui.tasks

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.Task
import com.example.myapplication.data.TaskStatus
import java.text.SimpleDateFormat
import java.util.*

class TaskAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onTaskToggle: (Task) -> Unit,
    private val onTaskLongClick: (Task) -> Boolean
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task)
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkboxCompleted: CheckBox = itemView.findViewById(R.id.checkbox_completed)
        private val textTaskName: TextView = itemView.findViewById(R.id.text_task_name)
        private val textTaskDetails: TextView = itemView.findViewById(R.id.text_task_details)
        private val textDueDate: TextView = itemView.findViewById(R.id.text_due_date)
        private val statusIndicator: View = itemView.findViewById(R.id.status_indicator)

        fun bind(task: Task) {
            // Set task name
            textTaskName.text = task.name
            
            // Set task details (hide if empty)
            if (task.details.isNotEmpty()) {
                textTaskDetails.text = task.details
                textTaskDetails.visibility = View.VISIBLE
            } else {
                textTaskDetails.visibility = View.GONE
            }
            
            // Set due date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            textDueDate.text = dateFormat.format(task.getDueDateAsDate())
            
            // Set completion status
            checkboxCompleted.setOnCheckedChangeListener(null) // Remove listener temporarily
            checkboxCompleted.isChecked = task.isCompleted
            checkboxCompleted.setOnCheckedChangeListener { _, _ ->
                onTaskToggle(task)
            }
            
            // Style based on task status
            styleTaskByStatus(task)
            
            // Set click listeners
            itemView.setOnClickListener { onTaskClick(task) }
            itemView.setOnLongClickListener { onTaskLongClick(task) }
        }

        private fun styleTaskByStatus(task: Task) {
            val context = itemView.context
            
            when (task.getStatus()) {
                TaskStatus.COMPLETED -> {
                    // Completed styling
                    textTaskName.paintFlags = textTaskName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    textTaskName.setTextColor(ContextCompat.getColor(context, R.color.text_completed))
                    textTaskDetails.setTextColor(ContextCompat.getColor(context, R.color.text_completed))
                    textDueDate.setTextColor(ContextCompat.getColor(context, R.color.text_completed))
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.status_completed))
                    itemView.alpha = 0.7f
                }
                TaskStatus.OVERDUE -> {
                    // Overdue styling
                    textTaskName.paintFlags = textTaskName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    textTaskName.setTextColor(ContextCompat.getColor(context, R.color.text_overdue))
                    textTaskDetails.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    textDueDate.setTextColor(ContextCompat.getColor(context, R.color.text_overdue))
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.status_overdue))
                    itemView.alpha = 1.0f
                }
                TaskStatus.PENDING -> {
                    // Pending styling
                    textTaskName.paintFlags = textTaskName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    textTaskName.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textTaskDetails.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    textDueDate.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.status_pending))
                    itemView.alpha = 1.0f
                }
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem == newItem
        }
    }
}