package com.eventverse.app.domain.i18n

sealed interface TextKey {
    val key: String

    data class General(override val key: String) : TextKey
    data class Navigation(override val key: String) : TextKey
    data class Action(override val key: String) : TextKey
    data class Status(override val key: String) : TextKey
    data class Entity(override val key: String) : TextKey
    data class Field(override val key: String) : TextKey
    data class Message(override val key: String) : TextKey
    data class Error(override val key: String) : TextKey
    data class Validation(override val key: String) : TextKey
}

fun String.toTextKey(category: String = "general"): TextKey {
    return TextKey.General("$category.$this")
}

object SystemTextKeys {
    // General
    val APP_NAME = TextKey.General("app.name")
    val LOADING = TextKey.General("loading")
    val SAVE = TextKey.General("save")
    val CANCEL = TextKey.General("cancel")
    val DELETE = TextKey.General("delete")
    val EDIT = TextKey.General("edit")
    val CREATE = TextKey.General("create")
    val SEARCH = TextKey.General("search")
    val FILTER = TextKey.General("filter")
    val SORT = TextKey.General("sort")
    val REFRESH = TextKey.General("refresh")
    val BACK = TextKey.General("back")
    val NEXT = TextKey.General("next")
    val PREVIOUS = TextKey.General("previous")
    val CLOSE = TextKey.General("close")
    val CONFIRM = TextKey.General("confirm")
    val YES = TextKey.General("yes")
    val NO = TextKey.General("no")
    val OK = TextKey.General("ok")
    val APPLY = TextKey.General("apply")
    val RESET = TextKey.General("reset")
    val UPLOAD = TextKey.General("upload")
    val DOWNLOAD = TextKey.General("download")
    val VIEW = TextKey.General("view")
    val DETAIL = TextKey.General("detail")
    val ADD = TextKey.General("add")
    val REMOVE = TextKey.General("remove")
    val SELECT = TextKey.General("select")
    val SELECT_ALL = TextKey.General("select_all")
    val DESELECT_ALL = TextKey.General("deselect_all")
    val NO_DATA = TextKey.General("no_data")
    val NO_RESULTS = TextKey.General("no_results")
    val REQUIRED = TextKey.General("required")
    val OPTIONAL = TextKey.General("optional")

    // Navigation
    val HOME = TextKey.Navigation("home")
    val DASHBOARD = TextKey.Navigation("dashboard")
    val SETTINGS = TextKey.Navigation("settings")
    val PROFILE = TextKey.Navigation("profile")
    val LOGOUT = TextKey.Navigation("logout")
    val LOGIN = TextKey.Navigation("login")

    // Status
    val ACTIVE = TextKey.Status("active")
    val INACTIVE = TextKey.Status("inactive")
    val PENDING = TextKey.Status("pending")
    val APPROVED = TextKey.Status("approved")
    val REJECTED = TextKey.Status("rejected")
    val DRAFT = TextKey.Status("draft")
    val PUBLISHED = TextKey.Status("published")
    val ARCHIVED = TextKey.Status("archived")
    val CANCELLED = TextKey.Status("cancelled")
    val COMPLETED = TextKey.Status("completed")
    val IN_PROGRESS = TextKey.Status("in_progress")
    val ON_HOLD = TextKey.Status("on_hold")

    // Messages
    val SUCCESS_SAVED = TextKey.Message("success_saved")
    val SUCCESS_DELETED = TextKey.Message("success_deleted")
    val SUCCESS_UPDATED = TextKey.Message("success_updated")
    val ERROR_GENERIC = TextKey.Message("error_generic")
    val ERROR_NETWORK = TextKey.Message("error_network")
    val ERROR_NOT_FOUND = TextKey.Message("error_not_found")
    val ERROR_UNAUTHORIZED = TextKey.Message("error_unauthorized")
    val ERROR_FORBIDDEN = TextKey.Message("error_forbidden")

    // Validation
    val VALIDATION_REQUIRED = TextKey.Validation("required")
    val VALIDATION_MIN_LENGTH = TextKey.Validation("min_length")
    val VALIDATION_MAX_LENGTH = TextKey.Validation("max_length")
    val VALIDATION_INVALID_FORMAT = TextKey.Validation("invalid_format")
    val VALIDATION_EMAIL = TextKey.Validation("email")
    val VALIDATION_NUMBER = TextKey.Validation("number")
    val VALIDATION_DATE = TextKey.Validation("date")
}
