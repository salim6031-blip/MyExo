package com.example.myexo1.utils

import android.content.Context
import java.io.*
import java.net.URL

class PlaylistHandler(private val context: Context) {

    // Метод для загрузки плейлиста по URL
    fun downloadPlaylist(urlString: String): Boolean {
        return try {
            // Создаем URL-объект
            val url = URL(urlString)

            // Открываем поток для чтения данных из интернета
            val connection = url.openConnection()
            connection.connect()

            val inputStream = connection.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream))

            // Сохраняем плейлист в файл "playlist.m3u"
            savePlaylistToFile(reader, "playlist.m3u")

            // Закрываем потоки
            reader.close()
            inputStream.close()

            true // Успешное завершение
        } catch (e: Exception) {
            e.printStackTrace()
            false // Ошибка
        }
    }

    // Метод для загрузки плейлиста по URL
    fun downloadEpg(urlString: String): Boolean {
        return try {
            // Создаем URL-объект
            val url = URL(urlString)

            // Открываем поток для чтения данных из интернета
            val connection = url.openConnection()
            connection.connect()

            val inputStream = connection.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream))

            // Сохраняем плейлист в файл "playlist.m3u"
            savePlaylistToFile(reader, "epg.txt")

            // Закрываем потоки
            reader.close()
            inputStream.close()

            true // Успешное завершение
        } catch (e: Exception) {
            e.printStackTrace()
            false // Ошибка
        }
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
        val groups = HashSet<String>() // Используем HashSet для уникальных групп
        val playlistFile = File(context.filesDir, playlistFileName)
        if (playlistFile.exists()) {
            playlistFile.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.startsWith("#EXTGRP:") == true) {
                            val groupName = line!!.substringAfter("#EXTGRP:")
                            groups.add(groupName)
                    } else {
                        if (line?.startsWith("#EXTINF:") == true) {
                            if ("group-title" in line!!) {
                                val groupName =
                                    line!!.substringAfter("group-title=\"").substringBefore('"')
                                groups.add(groupName)
                            } else {
                                groups.add("Без названия")
                            }
                        }
                    }
                }
            }
        }

        // Сохраняем группы в файл "group.txt"
        val groupFile = File(context.filesDir, "groups.txt")
        FileOutputStream(groupFile).use { outputStream ->
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                groups.forEach { group ->
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
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.startsWith("http") == true) {
                        channelCount++ // Считаем количество каналов
                    }
                }
            }
        }

        // Создаем файл "isFavorite.txt" с false для каждого канала
        val isFavoriteFile = File(context.filesDir, "isFavorite.txt")
        FileOutputStream(isFavoriteFile).use { outputStream ->
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                for (i in 1..channelCount) {
                    writer.write("false")
                    writer.newLine()
                }
            }
        }
        return channelCount
    }
}