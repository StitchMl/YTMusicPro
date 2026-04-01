package com.ytmusic.pro.web.webapp

import org.json.JSONArray
import org.json.JSONObject

data class QueueLayoutSnapshot(
    val viewportWidth: Float,
    val viewportHeight: Float,
    val items: List<QueueLayoutItem>,
) {
    companion object {
        fun fromJson(rawJson: String?): QueueLayoutSnapshot {
            val json = JSONObject(rawJson ?: "{}")
            val viewport = json.optJSONObject("viewport")
            val itemsJson = json.optJSONArray("items") ?: JSONArray()
            val items =
                buildList {
                    for (index in 0 until itemsJson.length()) {
                        val itemJson = itemsJson.optJSONObject(index) ?: continue
                        val itemRect = itemJson.optJSONObject("itemRect")?.toQueueRect() ?: continue
                        add(
                            QueueLayoutItem(
                                index = itemJson.optInt("index", size),
                                fingerprint = itemJson.optString("fingerprint").ifBlank { null },
                                itemRect = itemRect,
                                dragRect = itemJson.optJSONObject("dragRect")?.toQueueRect(),
                            ),
                        )
                    }
                }

            return QueueLayoutSnapshot(
                viewportWidth = viewport?.optDouble("width", 0.0)?.toFloat()?.coerceAtLeast(1f) ?: 1f,
                viewportHeight = viewport?.optDouble("height", 0.0)?.toFloat()?.coerceAtLeast(1f) ?: 1f,
                items = items,
            )
        }

        private fun JSONObject.toQueueRect(): QueueRect {
            return QueueRect(
                left = optDouble("left", 0.0).toFloat(),
                top = optDouble("top", 0.0).toFloat(),
                width = optDouble("width", 0.0).toFloat(),
                height = optDouble("height", 0.0).toFloat(),
            )
        }
    }
}

data class QueueLayoutItem(
    val index: Int,
    val fingerprint: String?,
    val itemRect: QueueRect,
    val dragRect: QueueRect?,
)

data class QueueRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)
