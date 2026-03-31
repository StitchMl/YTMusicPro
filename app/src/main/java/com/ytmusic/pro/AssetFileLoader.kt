package com.ytmusic.pro

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

@Suppress("CharsetObjectCanBeUsed")
object AssetFileLoader {

    @JvmStatic
    fun loadText(context: Context, assetName: String): String {
        return try {
            context.assets.open(assetName).use { inputStream ->
                ByteArrayOutputStream().use { outputStream ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read == -1) {
                            break
                        }
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.toString(StandardCharsets.UTF_8.name())
                }
            }
        } catch (_: IOException) {
            ""
        }
    }

    @JvmStatic
    fun loadJoinedText(context: Context, vararg assetNames: String): String {
        val builder = StringBuilder()
        for (assetName in assetNames) {
            val content = loadText(context, assetName)
            if (content.isEmpty()) {
                return ""
            }
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            builder.append(content)
        }
        return builder.toString()
    }
}
