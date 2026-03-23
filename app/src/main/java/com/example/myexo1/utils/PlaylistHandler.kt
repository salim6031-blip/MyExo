package com.example.myexo1.utils

import android.content.Context
import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class PlaylistHandler(private val context: Context) {

    // Метод для загрузки EPG по URL с таймаутами
    fun downloadEpg(urlString: String): Boolean {
        return try {
            var currentUrl = urlString
            var connection: HttpURLConnection
            var redirectCount = 0
            do {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 120_000
                connection.instanceFollowRedirects = false
                connection.connect()
                val code = connection.responseCode
                if (code in 301..302 || code == 307 || code == 308) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location == null) return false
                    currentUrl = location
                    redirectCount++
                } else {
                    break
                }
            } while (redirectCount < 5)

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "EPG загрузка: HTTP $code от $currentUrl")
                connection.disconnect()
                return false
            }

            val rawStream = connection.getInputStream()
            val inputStream = if (currentUrl.endsWith(".gz") || urlString.endsWith(".gz")) GZIPInputStream(rawStream) else rawStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            savePlaylistToFile(reader, "epg.txt")
            reader.close()
            inputStream.close()
            connection.disconnect()

            Log.d(TAG, "EPG успешно загружен из $urlString")
            true
        } catch (e: Exception) {
            Log.e(TAG, "EPG ошибка загрузки из $urlString: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "PlaylistHandler"
    }

    // Метод для сохранения плейлиста в файл
    private fun savePlaylistToFile(reader: BufferedReader, fileName: String) {
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { outputStream ->
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    writer.write(line) // Записываем строку в файл
                    writer.newLine() // Добавляем символ новой строки
                }
            }
        }
    }

    // Метод для извлечения групп из плейлиста и сохранения их в файл "groups.txt"
    fun extractGroupsFromPlaylist(playlistFileName: String) {
        // LinkedHashMap: lowercase -> первое встреченное написание (сохраняем порядок)
        val groupMap = LinkedHashMap<String, String>()
        val playlistFile = File(context.filesDir, playlistFileName)
        if (playlistFile.exists()) {
            playlistFile.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("#EXTGRP:")) {
                        val groupName = line.substringAfter("#EXTGRP:")
                        groupMap.putIfAbsent(groupName.lowercase(), groupName)
                    } else if (line.startsWith("#EXTINF:")) {
                        if ("group-title" in line) {
                            val groupName =
                                line.substringAfter("group-title=\"").substringBefore('"')
                            groupMap.putIfAbsent(groupName.lowercase(), groupName)
                        } else {
                            groupMap.putIfAbsent("без названия", "Без названия")
                        }
                    }
                }
            }
        }

        val groupFile = File(context.filesDir, "groups.txt")
        FileOutputStream(groupFile).use { outputStream ->
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                groupMap.values.forEach { group ->
                    writer.write(group)
                    writer.newLine()
                }
            }
        }
    }

    // Метод для создания файла "isFavorite.txt" с false для каждого канала
    fun createIsFavoriteFile(playlistFileName: String): Int {
        val playlistFile = File(context.filesDir, playlistFileName)
        var channelCount = 0
        if (playlistFile.exists()) {
            playlistFile.bufferedReader().use { reader ->
                var afterExtinf = false
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.startsWith("#EXTINF:") == true) {
                        afterExtinf = true
                    } else if (afterExtinf && line?.startsWith("#") == false) {
                        channelCount++ // Считаем количество каналов
                        afterExtinf = false
                    }
                }
            }
        }

        // Создаем файл "isFavorite.txt" с false для каждого канала
        val isFavoriteFile = File(context.filesDir, "isFavorite.txt")
        FileOutputStream(isFavoriteFile).use { outputStream ->
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                repeat(channelCount) {
                    writer.write("false")
                    writer.newLine()
                }
            }
        }
        return channelCount
    }
}