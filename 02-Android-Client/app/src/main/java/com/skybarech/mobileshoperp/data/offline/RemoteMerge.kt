package com.skybarech.mobileshoperp.data.offline

import org.json.JSONArray
import org.json.JSONObject

object RemoteMerge {
    private val collections = mapOf(
        "product" to "products", "sale" to "sales", "repair" to "repairs",
        "customer" to "customers", "supplier" to "suppliers", "installment" to "installments",
        "wallet" to "ewallets", "expense" to "expenses", "cash_session" to "cashClosings",
        "support_request" to "supportRequests"
    )

    fun merge(snapshotJson: String, records: JSONArray): String {
        val root = runCatching { JSONObject(snapshotJson) }.getOrElse { JSONObject() }
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            val payload = record.optJSONObject("payload") ?: JSONObject()
            if (record.optString("entity_type") == "device_snapshot") {
                mergeSnapshot(root, payload.optJSONObject("snapshot") ?: payload)
                continue
            }
            val collection = collections[record.optString("entity_type")] ?: continue
            mergeOne(root, collection, record.optString("entity_id"), payload, record.optBoolean("is_deleted"))
        }
        return root.toString()
    }

    private fun mergeSnapshot(root: JSONObject, source: JSONObject) {
        val aliases = mapOf(
            "products" to "products", "sales" to "sales", "invoices" to "sales",
            "repairs" to "repairs", "customers" to "customers", "suppliers" to "suppliers",
            "installments" to "installments", "ewallets" to "ewallets", "expenses" to "expenses",
            "cashClosings" to "cashClosings", "cashSessions" to "cashClosings",
            "supportRequests" to "supportRequests"
        )
        for ((sourceName, targetName) in aliases) {
            val items = source.optJSONArray(sourceName) ?: continue
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isNotBlank()) mergeOne(root, targetName, id, item, false)
            }
        }
    }

    private fun mergeOne(root: JSONObject, collection: String, id: String, raw: JSONObject, deleted: Boolean) {
        if (id.isBlank()) return
        val items = root.optJSONArray(collection) ?: JSONArray().also { root.put(collection, it) }
        var existingIndex = -1
        for (index in 0 until items.length()) {
            if (items.optJSONObject(index)?.optString("id") == id) { existingIndex = index; break }
        }
        if (deleted) {
            if (existingIndex >= 0) items.remove(existingIndex)
            return
        }
        val normalized = normalize(collection, JSONObject(raw.toString()).put("id", id))
        if (existingIndex >= 0) {
            val old = items.optJSONObject(existingIndex) ?: JSONObject()
            normalized.keys().forEach { key -> old.put(key, normalized.opt(key)) }
            items.put(existingIndex, old)
        } else items.put(normalized)
    }

    private fun normalize(collection: String, value: JSONObject): JSONObject {
        if (value.has("dateLabel") && !value.has("date")) value.put("date", value.optString("dateLabel"))
        if (collection == "sales" && value.has("timeLabel") && !value.has("time")) value.put("time", value.optString("timeLabel"))
        if (collection == "products") {
            if (value.has("price") && !value.has("salePrice")) value.put("salePrice", value.optInt("price"))
            if (value.has("cost") && !value.has("purchasePrice")) value.put("purchasePrice", value.optInt("cost"))
        }
        if (collection == "repairs" && value.has("cost") && !value.has("amount")) value.put("amount", value.optInt("cost"))
        if (collection == "sales") {
            if (!value.has("item")) {
                val first = value.optJSONArray("items")?.optJSONObject(0)?.optString("name").orEmpty()
                if (first.isNotBlank()) value.put("item", first)
            }
            if (!value.has("time")) value.put("time", value.optString("timeLabel").ifBlank { value.optString("date", "Now") })
        }
        if (collection == "ewallets") {
            if (value.has("customer") && !value.has("party")) value.put("party", value.optString("customer"))
            if (value.has("note") && !value.has("reference")) value.put("reference", value.optString("note"))
        }
        if (collection == "installments") {
            if (value.has("due") && !value.has("amount")) value.put("amount", value.optInt("due"))
            if (value.has("date") && !value.has("dueLabel")) value.put("dueLabel", value.optString("date"))
        }
        if (collection == "cashClosings") {
            if (value.has("opening") && !value.has("openingCash")) value.put("openingCash", value.optInt("opening"))
            if (value.has("actual") && !value.has("actualCash")) value.put("actualCash", value.optInt("actual"))
            if (value.has("closing") && !value.has("actualCash")) value.put("actualCash", value.optInt("closing"))
            if (value.has("sales") && !value.has("cashSales")) value.put("cashSales", value.optInt("sales"))
        }
        return value
    }
}
