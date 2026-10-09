package com.dante.anchoralarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Item(val text: String, val done: Boolean = false)

/** One checklist together with its own anchor + ring pattern. */
data class Plan(
    val id: Int = 0,
    val name: String = "Checklist",
    val items: List<Item> = emptyList(),
    val hour: Int = 7,
    val minute: Int = 0,
    val after: Boolean = false,
    val intervalMin: Int = 5,
    val count: Int = 4,
)

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("anchor", Context.MODE_PRIVATE)

    private fun toJson(p: Plan): JSONObject {
        val arr = JSONArray()
        p.items.forEach { arr.put(JSONObject().put("t", it.text).put("d", it.done)) }
        return JSONObject()
            .put("id", p.id)
            .put("name", p.name)
            .put("items", arr)
            .put("h", p.hour)
            .put("m", p.minute)
            .put("a", p.after)
            .put("i", p.intervalMin)
            .put("n", p.count)
    }

    private fun fromJson(o: JSONObject, fallbackId: Int = 0): Plan {
        val arr = o.getJSONArray("items")
        return Plan(
            id = o.optInt("id", fallbackId),
            name = o.optString("name", "Checklist"),
            items = List(arr.length()) { i ->
                val x = arr.getJSONObject(i)
                Item(x.getString("t"), x.optBoolean("d", false))
            },
            hour = o.optInt("h", 7),
            minute = o.optInt("m", 0),
            after = o.optBoolean("a", false),
            intervalMin = o.optInt("i", 5),
            count = o.optInt("n", 4),
        )
    }

    /** Converts the old single-plan storage (v1.x) into the first checklist. */
    private fun migrateLegacy(c: Context) {
        val old = sp(c).getString("plan", null)
        if (old == null) {
            sp(c).edit().putString("plans", "[]").apply()
            return
        }
        try {
            val p = fromJson(JSONObject(old), 1).copy(id = 1, name = "Checklist 1")
            val now = System.currentTimeMillis()
            val future = (sp(c).getString("rings", "") ?: "")
                .split(",").mapNotNull { it.toLongOrNull() }.filter { it > now }
            sp(c).edit()
                .putString("plans", JSONArray().put(toJson(p)).toString())
                .putInt("nextId", 2)
                .remove("plan")
                .remove("rings")
                .apply()
            Scheduler.cancelLegacy(c)
            if (future.isNotEmpty()) {
                saveRings(c, 1, future)
                Scheduler.schedule(c, 1, future)
            }
        } catch (e: Exception) {
            sp(c).edit().putString("plans", "[]").apply()
        }
    }

    @Synchronized
    fun loadAll(c: Context): List<Plan> {
        if (sp(c).getString("plans", null) == null) migrateLegacy(c)
        val s = sp(c).getString("plans", null) ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            List(arr.length()) { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveAll(c: Context, plans: List<Plan>) {
        val arr = JSONArray()
        plans.forEach { arr.put(toJson(it)) }
        sp(c).edit().putString("plans", arr.toString()).apply()
    }

    fun load(c: Context, id: Int): Plan? = loadAll(c).firstOrNull { it.id == id }

    /** Insert or replace by id. */
    @Synchronized
    fun save(c: Context, p: Plan) {
        val all = loadAll(c)
        val next = if (all.any { it.id == p.id }) all.map { if (it.id == p.id) p else it } else all + p
        saveAll(c, next)
    }

    @Synchronized
    fun delete(c: Context, id: Int) {
        saveAll(c, loadAll(c).filter { it.id != id })
        sp(c).edit().remove("rings_$id").apply()
    }

    @Synchronized
    fun nextId(c: Context): Int {
        val n = sp(c).getInt("nextId", 1)
        sp(c).edit().putInt("nextId", n + 1).apply()
        return n
    }

    fun loadRings(c: Context, id: Int): List<Long> =
        (sp(c).getString("rings_$id", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }

    fun saveRings(c: Context, id: Int, times: List<Long>) {
        sp(c).edit().putString("rings_$id", times.joinToString(",")).apply()
    }
}
