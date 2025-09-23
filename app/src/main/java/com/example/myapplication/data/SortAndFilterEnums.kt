package com.example.myapplication.data

enum class SortOption(val displayName: String) {
    DUE_DATE_ASC("Due Date (Earliest First)"),
    DUE_DATE_DESC("Due Date (Latest First)"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    CREATED_DATE_DESC("Newest First"),
    CREATED_DATE_ASC("Oldest First")
}

enum class DateFilter(val displayName: String) {
    ALL("All"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    OVERDUE("Overdue"),
    CUSTOM_RANGE("Custom Range")
}

data class TaskFilters(
    val status: TaskStatus? = null,
    val dateFilter: DateFilter = DateFilter.ALL,
    val customStartDate: java.util.Date? = null,
    val customEndDate: java.util.Date? = null,
    val sortOption: SortOption = SortOption.DUE_DATE_ASC
)