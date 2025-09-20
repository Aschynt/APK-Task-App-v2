package com.example.myapplication.ui.tasks

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.data.CreateTaskRequest
import com.example.myapplication.data.Task
import com.example.myapplication.data.TaskRepository
import com.example.myapplication.data.UpdateTaskRequest
import com.example.myapplication.databinding.ActivityAddEditTaskBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddEditTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditTaskBinding
    private val repository = TaskRepository()
    private val auth = FirebaseAuth.getInstance()
    
    private var selectedDate: Date? = null
    private var taskToEdit: Task? = null
    private var isEditMode = false
    
    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK = "task"
        
        fun newIntent(context: Context): Intent {
            return Intent(context, AddEditTaskActivity::class.java)
        }
        
        fun newEditIntent(context: Context, task: Task): Intent {
            return Intent(context, AddEditTaskActivity::class.java).apply {
                putExtra(EXTRA_TASK, task)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupUI()
        handleIntent()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    private fun setupUI() {
        // Set default date to tomorrow
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        selectedDate = calendar.time
        updateDateDisplay()
    }

    private fun handleIntent() {
        // Check if we're in edit mode
        taskToEdit = intent.getSerializableExtra(EXTRA_TASK) as? Task
        
        if (taskToEdit != null) {
            isEditMode = true
            setupEditMode(taskToEdit!!)
        } else {
            isEditMode = false
            setupAddMode()
        }
    }

    private fun setupAddMode() {
        binding.toolbar.title = "Add Task"
        binding.btnSave.text = "Save Task"
    }

    private fun setupEditMode(task: Task) {
        binding.toolbar.title = "Edit Task"
        binding.btnSave.text = "Update Task"
        
        // Pre-populate form with task data
        binding.etTaskName.setText(task.name)
        binding.etTaskDetails.setText(task.details)
        selectedDate = task.getDueDateAsDate()
        updateDateDisplay()
    }

    private fun setupClickListeners() {
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }
        
        binding.btnCancel.setOnClickListener {
            finish()
        }
        
        binding.btnSave.setOnClickListener {
            if (validateForm()) {
                saveTask()
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        selectedDate?.let { calendar.time = it }
        
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance()
                newCalendar.set(year, month, dayOfMonth)
                selectedDate = newCalendar.time
                updateDateDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        
        // Don't allow selecting dates in the past
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun updateDateDisplay() {
        selectedDate?.let { date ->
            val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
            binding.tvSelectedDate.text = dateFormat.format(date)
            binding.tvSelectedDate.visibility = View.VISIBLE
            
            // Update button text
            val shortFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.btnSelectDate.text = "Due: ${shortFormat.format(date)}"
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true
        
        // Clear previous errors
        binding.tilTaskName.error = null
        binding.tvErrorMessage.visibility = View.GONE
        
        // Validate task name
        val taskName = binding.etTaskName.text.toString().trim()
        if (taskName.isEmpty()) {
            binding.tilTaskName.error = "Task name is required"
            isValid = false
        } else if (taskName.length < 3) {
            binding.tilTaskName.error = "Task name must be at least 3 characters"
            isValid = false
        }
        
        // Validate due date
        if (selectedDate == null) {
            showError("Please select a due date")
            isValid = false
        } else {
            // Check if date is not in the past
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)
            
            if (selectedDate!!.before(today.time)) {
                showError("Due date cannot be in the past")
                isValid = false
            }
        }
        
        return isValid
    }

    private fun saveTask() {
        val taskName = binding.etTaskName.text.toString().trim()
        val taskDetails = binding.etTaskDetails.text.toString().trim()
        val userId = auth.currentUser?.uid ?: return
        
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val result = if (isEditMode && taskToEdit != null) {
                    // Update existing task
                    val updateRequest = UpdateTaskRequest(
                        name = taskName,
                        details = taskDetails,
                        dueDate = selectedDate
                    )
                    repository.updateTask(taskToEdit!!.id, updateRequest)
                } else {
                    // Create new task
                    val createRequest = CreateTaskRequest(
                        name = taskName,
                        details = taskDetails,
                        dueDate = selectedDate!!,
                        userId = userId
                    )
                    repository.createTask(createRequest)
                }
                
                showLoading(false)
                
                if (result.isSuccess) {
                    val message = if (isEditMode) "Task updated successfully" else "Task created successfully"
                    Toast.makeText(this@AddEditTaskActivity, message, Toast.LENGTH_SHORT).show()
                    
                    // Set result and finish
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error occurred"
                    showError(error)
                }
                
            } catch (e: Exception) {
                showLoading(false)
                showError("Error: ${e.message}")
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !show
        binding.btnCancel.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.tvErrorMessage.text = message
        binding.tvErrorMessage.visibility = View.VISIBLE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}