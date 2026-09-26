package com.eventverse.app.domain.crm

import kotlin.jvm.JvmInline

@JvmInline
value class ContactId(val value: String) {
    init {
        require(value.isNotBlank()) { "ContactId cannot be blank" }
        require(value.length <= 64) { "ContactId must be at most 64 characters" }
    }
}
