package de.baumann.browser.browser

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class MyHashMapManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
    private val hashMapKey = "my_hash_map_key"
    private val hashMap: HashMap<String, Boolean> = loadHashMapFromPreferences()

    fun getHashMap(): HashMap<String, Boolean> {
        return loadHashMapFromPreferences()
    }

    fun updateHashMap(key: String, value: Boolean) {
        hashMap[key] = value
        saveHashMapToPreferences()
    }

    fun hasHashMapChanged(): Boolean {
        val savedHashMap = loadHashMapFromPreferences()
        return savedHashMap != hashMap
    }

    fun saveHashMapToPreferences() {
        val editor = sharedPreferences.edit()
        val jsonString = hashMapToJsonString(hashMap)
        editor.putString(hashMapKey, jsonString)
        editor.apply()
    }

    private fun loadHashMapFromPreferences(): HashMap<String, Boolean> {
        val jsonString = sharedPreferences.getString(hashMapKey, null)
        return jsonString?.let { jsonStringToHashMap(it) } ?: HashMap()
    }

    private fun hashMapToJsonString(hashMap: HashMap<String, Boolean>): String {
        // You can use a JSON library or a custom serialization method here
        // For simplicity, we'll use a basic approach:
        val jsonArray = hashMap.entries.map { "{\"key\":\"${it.key}\",\"value\":${it.value}}" }
        return "[${jsonArray.joinToString(",")}]"
    }

    private fun jsonStringToHashMap(jsonString: String): HashMap<String, Boolean> {
        // You can use a JSON library or a custom deserialization method here
        // For simplicity, we'll use a basic approach:
        val map = HashMap<String, Boolean>()
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val key = jsonObject.getString("key")
            val value = jsonObject.getBoolean("value")
            map[key] = value
        }
        return map
    }
}
