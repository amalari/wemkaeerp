package com.eventverse.app.domain.crm

import com.eventverse.app.domain.tenant.TenantId

/**
 * Persists tenant customer contacts. The phone number is the natural find-or-create key:
 * qualification deduplicates by it so re-qualifying the same lead never forks the customer
 * master into two rows.
 */
interface ContactRepository {

    suspend fun findById(tenantId: TenantId, id: ContactId): Contact?

    /** Exact match on the normalised E.164 phone value; null when blank phone is passed. */
    suspend fun findByPhone(tenantId: TenantId, phone: WhatsappNumber): Contact?

    suspend fun findActive(tenantId: TenantId): List<Contact>

    suspend fun save(contact: Contact): Result<Contact>

    /**
     * Hard-deletes one contact row; true when a row was removed. Callers MUST prove the
     * contact is un-referenced first — the database cascades `deals.contact_id`, so a
     * careless delete silently destroys deal history along with it.
     */
    suspend fun delete(tenantId: TenantId, id: ContactId): Boolean
}
