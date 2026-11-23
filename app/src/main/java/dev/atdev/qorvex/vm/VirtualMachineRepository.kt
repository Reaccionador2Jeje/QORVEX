package dev.atdev.qorvex.vm

import android.content.Context
import org.json.JSONArray

class VirtualMachineRepository(context: Context) {
    private val preferences = context.getSharedPreferences("qorvex_vm_profiles", Context.MODE_PRIVATE)

    fun getAll(): List<VirtualMachine> {
        val raw = preferences.getString(KEY_PROFILES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    runCatching { add(VirtualMachine.fromJson(array.getJSONObject(index))) }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(machine: VirtualMachine) {
        val machines = getAll().filterNot { it.id == machine.id } + machine
        val array = JSONArray()
        machines.forEach { array.put(it.toJson()) }
        check(preferences.edit().putString(KEY_PROFILES, array.toString()).commit()) {
            "Could not save the virtual machine profile."
        }
    }

    fun delete(id: String) {
        val array = JSONArray()
        getAll().filterNot { it.id == id }.forEach { array.put(it.toJson()) }
        check(preferences.edit().putString(KEY_PROFILES, array.toString()).commit()) {
            "Could not update virtual machine profiles."
        }
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
    }
}
