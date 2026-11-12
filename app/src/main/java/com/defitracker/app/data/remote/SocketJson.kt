package com.defitracker.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject

// los frames de los sockets se leen con Gson, no con org.json: este ultimo es un stub
// en los tests JVM y un campo mal escrito pasaria sin verificacion
internal val socketGson: Gson = Gson()

internal fun parseSocketObject(text: String): JsonObject? =
    try {
        socketGson.fromJson(text, JsonObject::class.java)
    } catch (_: Exception) {
        null
    }

internal fun JsonObject.optString(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString

internal fun JsonObject.optObj(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject
