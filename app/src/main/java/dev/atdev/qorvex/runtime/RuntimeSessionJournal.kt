package dev.atdev.qorvex.runtime

import android.content.Context

/** Durable marker used to detect a VM process that did not shut down cleanly. */
internal class RuntimeSessionJournal(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    data class Session(val machineId: String, val diskCapacityBytes: Long, val startedAtEpochMs: Long)

    fun begin(machineId: String, diskCapacityBytes: Long) {
        check(preferences.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_MACHINE_ID, machineId)
            .putLong(KEY_DISK_CAPACITY, diskCapacityBytes)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .commit()) { "Could not persist the VM recovery marker." }
    }

    fun read(): Session? {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) return null
        val machineId = preferences.getString(KEY_MACHINE_ID, null).orEmpty()
        val capacity = preferences.getLong(KEY_DISK_CAPACITY, -1L)
        val startedAt = preferences.getLong(KEY_STARTED_AT, 0L)
        return Session(machineId, capacity, startedAt)
    }

    fun clear() {
        check(preferences.edit().clear().commit()) { "Could not clear the VM recovery marker." }
    }

    private companion object {
        const val PREFERENCES = "qorvex_runtime_session"
        const val KEY_ACTIVE = "active"
        const val KEY_MACHINE_ID = "machine_id"
        const val KEY_DISK_CAPACITY = "disk_capacity_bytes"
        const val KEY_STARTED_AT = "started_at_epoch_ms"
    }
}