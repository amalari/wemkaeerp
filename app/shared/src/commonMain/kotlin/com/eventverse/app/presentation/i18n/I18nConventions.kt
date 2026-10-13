package com.eventverse.app.presentation.i18n

object I18nConventions {
    /**
     * Struktur kunci: {category}.{feature}.{object}.{action}
     *
     * Contoh:
     * - nav.home
     * - action.save
     * - fulfillment.carton.create
     * - fulfillment.carton.status_packed
     * - error.not_found
     *
     * Category: nav, action, status, entity, field, message, error, validation
     * Feature: fulfillment, sampling, costing, rbac, orgchart, crm, deal
     * Object: carton, shipment, sample, cost, user, role, lead, deal
     * Action: create, edit, delete, view, list, update, approve, reject
     */

    const val CATEGORY_NAV = "nav"
    const val CATEGORY_ACTION = "action"
    const val CATEGORY_STATUS = "status"
    const val CATEGORY_ENTITY = "entity"
    const val CATEGORY_FIELD = "field"
    const val CATEGORY_MESSAGE = "message"
    const val CATEGORY_ERROR = "error"
    const val CATEGORY_VALIDATION = "validation"

    const val FEATURE_FULFILLMENT = "fulfillment"
    const val FEATURE_SAMPLING = "sampling"
    const val FEATURE_COSTING = "costing"
    const val FEATURE_RBAC = "rbac"
    const val FEATURE_ORGCHART = "orgchart"
    const val FEATURE_CRM = "crm"
    const val FEATURE_DEAL = "deal"

    const val ACTION_CREATE = "create"
    const val ACTION_EDIT = "edit"
    const val ACTION_DELETE = "delete"
    const val ACTION_VIEW = "view"
    const val ACTION_LIST = "list"
    const val ACTION_UPDATE = "update"
    const val ACTION_APPROVE = "approve"
    const val ACTION_REJECT = "reject"

    fun key(category: String, feature: String? = null, obj: String? = null, action: String? = null): String {
        return listOf(category, feature, obj, action)
            .filterNotNull()
            .joinToString(".")
    }
}

object ServerErrorCodes {
    const val UNKNOWN = "UNKNOWN"
    const val NOT_FOUND = "NOT_FOUND"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val DUPLICATE_ENTRY = "DUPLICATE_ENTRY"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val NETWORK_ERROR = "NETWORK_ERROR"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val CONFLICT = "CONFLICT"
}
