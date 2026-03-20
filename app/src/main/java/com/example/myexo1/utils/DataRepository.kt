package com.example.myexo1.utils

import android.content.Context
import com.example.data.EpgProgram
import com.example.data.MyData
import com.example.myexo1.adapter.EpgInfo
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Единый кеш данных приложения.
 * Загружает playlist, groups, favorites, EPG один раз и разделяет между Activity.
 */
object DataRepository {

    val chNumber = ArrayList<Int>()
    val titleCh = ArrayList<String>()
    val urlCh = ArrayList<String>()
    val tvgLogo = ArrayList<String>()
    val tvgId = ArrayList<String>()
    val groupTitle = ArrayList<String>()
    val groupsArr = ArrayList<String>()
    val favArray = ArrayList<Boolean>()
    val epgChannelMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    val epgProgramMap = java.util.concurrent.ConcurrentHashMap<String, MutableList<EpgProgram>>()
    // Кеш: нормализованное имя → channel ID (строится при загрузке EPG)
    private val epgNormalizedMap = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    var playlistLoaded = false
        private set

    @Volatile
    var epgLoaded = false
        private set

    val searchResults = ArrayList<Int>()  // индексы найденных каналов

    private const val PLAYLIST_FILE = "playlist.m3u"
    private const val GROUPS_FILE = "groups.txt"
    private const val FAV_FILE = "isFavorite.txt"
    private const val EPG_FILE = "epg.txt"
    private const val SEARCH_FILE = "search_results.txt"

    fun playlistExists(context: Context): Boolean {
        return File(context.filesDir, PLAYLIST_FILE).exists()
    }

    /** Загрузка playlist + groups + favorites. Вызывать один раз при старте. */
    fun loadAll(context: Context) {
        if (playlistLoaded) return
        loadChannelFile(context)
        loadGroupFile(context)
        normalizeGroupTitles()
        fillMissingLogos()
        loadFavoritesFile(context)
        loadSearchResults(context)
        playlistLoaded = true
    }

    /** Принудительная перезагрузка (после скачивания нового плейлиста) */
    fun reloadAll(context: Context) {
        playlistLoaded = false
        epgLoaded = false
        loadChannelFile(context)
        loadGroupFile(context)
        normalizeGroupTitles()
        fillMissingLogos()
        loadFavoritesFile(context)
        playlistLoaded = true
    }

    /** Перезагрузить только избранное */
    fun reloadFavorites(context: Context) {
        loadFavoritesFile(context)
    }

    /** Загрузка EPG (вызывать в фоне) */
    fun loadEpg(context: Context) {
        if (epgLoaded) return
        loadEpgFile(context)
        epgLoaded = true
    }

    /** Принудительная перезагрузка EPG */
    fun reloadEpg(context: Context) {
        epgLoaded = false
        loadEpgFile(context)
        epgLoaded = true
    }

    /** Формирование списка каналов для группы */
    fun buildChannelList(grNum: Int): ArrayList<MyData> {
        val list = ArrayList<MyData>()
        val filter = groupsArr.getOrElse(grNum) { "" }
        for (i in urlCh.indices) {
            if (grNum == 0 || groupTitle[i] == filter) {
                list.add(
                    MyData(chNumber[i], titleCh[i], urlCh[i], tvgLogo[i], groupTitle[i], tvgId[i], favArray[i])
                )
            }
        }
        return list
    }

    /** Формирование списка избранных каналов */
    fun buildFavList(): ArrayList<MyData> {
        val list = ArrayList<MyData>()
        for (i in favArray.indices) {
            if (favArray[i]) {
                list.add(
                    MyData(chNumber[i], titleCh[i], urlCh[i], tvgLogo[i], groupTitle[i], tvgId[i], favArray[i])
                )
            }
        }
        return list
    }

    /** Подсчёт каналов в каждой группе */
    fun countByGroup(): HashMap<String, Int> {
        val map = HashMap<String, Int>()
        for (gt in groupTitle) {
            map[gt] = (map[gt] ?: 0) + 1
        }
        return map
    }

    /** Поиск каналов по названию */
    fun searchChannels(query: String): ArrayList<MyData> {
        searchResults.clear()
        val q = query.lowercase()
        for (i in titleCh.indices) {
            if (titleCh[i].lowercase().contains(q)) {
                searchResults.add(i)
            }
        }
        return buildSearchList()
    }

    /** Формирование списка найденных каналов */
    fun buildSearchList(): ArrayList<MyData> {
        val list = ArrayList<MyData>()
        for (i in searchResults) {
            if (i in urlCh.indices) {
                list.add(
                    MyData(chNumber[i], titleCh[i], urlCh[i], tvgLogo[i], groupTitle[i], tvgId[i], favArray[i])
                )
            }
        }
        return list
    }

    /** Сохранить результаты поиска в файл */
    fun saveSearchResults(context: Context) {
        try {
            val bw = BufferedWriter(OutputStreamWriter(context.openFileOutput(SEARCH_FILE, Context.MODE_PRIVATE)))
            for (idx in searchResults) {
                bw.write("$idx\n")
            }
            bw.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /** Загрузить результаты поиска из файла */
    fun loadSearchResults(context: Context) {
        searchResults.clear()
        val file = File(context.filesDir, SEARCH_FILE)
        if (!file.exists()) return
        try {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val idx = line.trim().toIntOrNull()
                    if (idx != null) searchResults.add(idx)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun hasSearchResults(context: Context): Boolean {
        val file = File(context.filesDir, SEARCH_FILE)
        return file.exists() && file.length() > 0
    }

    /** Публичный доступ к поиску channel ID */
    fun findChannelIdPublic(channelTitle: String): String? = findChannelId(channelTitle)

    /** Найти channel ID: сначала точное совпадение, потом нормализованное (O(1)) */
    private fun findChannelId(channelTitle: String): String? {
        // Точное совпадение
        val exact = epgChannelMap[channelTitle.lowercase()]
        if (exact != null) return exact
        // Нормализованное совпадение через предварительно построенный кеш
        return epgNormalizedMap[normalizeChannelName(channelTitle)]
    }

    /** Получить EPG для канала */
    fun getEpgInfoForChannel(channelTitle: String): EpgInfo? {
        val channelId = findChannelId(channelTitle) ?: return null
        val programs = epgProgramMap[channelId] ?: return null
        val displayTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val nowMillis = System.currentTimeMillis()
        for (prog in programs) {
            if (nowMillis in prog.startMillis until prog.endMillis) {
                val shortEpg = prog.title.replace("\\[[0-9]+\\+]".toRegex(), "")
                val start = displayTimeFormat.format(Date(prog.startMillis))
                val end = displayTimeFormat.format(Date(prog.endMillis))
                val max = ((prog.endMillis - prog.startMillis) / 1000).toInt()
                val progress = ((nowMillis - prog.startMillis) / 1000).toInt()
                return EpgInfo("$shortEpg ($start - $end)", progress, max)
            }
        }
        return null
    }

    /** Сохранить избранное в файл */
    fun saveFavList(context: Context) {
        try {
            val bw = BufferedWriter(OutputStreamWriter(context.openFileOutput(FAV_FILE, Context.MODE_PRIVATE)))
            for (i in favArray.indices) {
                bw.write("${favArray[i]}\n")
            }
            bw.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Заполнение пустых лого из каналов с похожими названиями.
     * "Россия 1 HD", "Россия 1 (+2)", "Россия 1" → берём лого у того, у кого оно есть.
     * Нормализация: lowercase, убираем HD/SD/FHD/UHD/+/цифры в скобках и т.п.
     */
    private fun fillMissingLogos() {
        // Строим маппинг: нормализованное имя -> первый найденный непустой URL лого
        val logoMap = HashMap<String, String>()
        for (i in titleCh.indices) {
            val key = normalizeChannelName(titleCh[i])
            if (key.isEmpty()) continue
            val logo = tvgLogo[i].trim()
            if (logo.isNotEmpty() && !logoMap.containsKey(key)) {
                logoMap[key] = logo
            }
        }
        // Заполняем пустые лого
        for (i in tvgLogo.indices) {
            if (tvgLogo[i].trim().isEmpty()) {
                val key = normalizeChannelName(titleCh[i])
                val found = logoMap[key]
                if (found != null) {
                    tvgLogo[i] = found
                }
            }
        }
    }

    private fun normalizeChannelName(name: String): String {
        return name
            .substringBefore("/")                             // "Россия 1 / ГТРК Кузбасс" → "Россия 1 "
            .substringBefore("(")                             // "Первый канал (Москва)" → "Первый канал "
            .lowercase()
            .replace(Regex("\\s*(hd|sd|fhd|uhd|4k|hevc|h\\.?265)\\s*"), " ")
            .replace(Regex("\\s*\\+\\s*$"), "")              // "НТВ +"
            .replace(Regex("[^\\p{L}\\p{N}]"), " ")          // оставляем буквы и цифры
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Нормализация groupTitle каналов: приводим к каноническому написанию из groupsArr.
     * Например, "эфирные" и "Эфирные" → "Эфирные" (первое встреченное в groups.txt).
     */
    private fun normalizeGroupTitles() {
        // lowercase -> каноническое имя из groupsArr
        val canonMap = HashMap<String, String>()
        for (g in groupsArr) {
            canonMap.putIfAbsent(g.lowercase(), g)
        }
        for (i in groupTitle.indices) {
            val canon = canonMap[groupTitle[i].lowercase()]
            if (canon != null && canon != groupTitle[i]) {
                groupTitle[i] = canon
            }
        }
    }

    // ─── private loading ───

    private fun loadChannelFile(context: Context) {
        val file = File(context.filesDir, PLAYLIST_FILE)
        if (!file.exists()) return
        var chCount = 0
        var groupTitleBool: Boolean
        var extinfBool = false
        var groupTitleTemp = ""

        chNumber.clear()
        titleCh.clear()
        urlCh.clear()
        tvgLogo.clear()
        tvgId.clear()
        groupTitle.clear()
        try {
            file.bufferedReader().useLines { lines ->
                for (i in lines) {
                    groupTitleBool = true
                    if (!extinfBool) {
                        if (i.startsWith("#EXTINF:")) {
                            extinfBool = true
                            chCount++
                            chNumber.add(chCount)
                            if ("group-title=" in i) {
                                groupTitleTemp = parseUrlString(i, "group-title=\"")
                                groupTitleBool = false
                            }
                            tvgLogo.add(parseUrlString(i, "tvg-logo=\""))
                            tvgId.add(parseUrlString(i, "tvg-id=\""))
                            titleCh.add(i.substringAfterLast(','))
                        }
                    }
                    if (groupTitleBool) {
                        if (i.startsWith("#EXTGRP:")) {
                            groupTitleTemp = i.substringAfter("#EXTGRP:")
                        }
                        if (groupTitleTemp.isEmpty()) groupTitleTemp = "Общий"
                    }
                    if (!i.startsWith("#") && extinfBool) {
                        urlCh.add(i)
                        groupTitle.add(groupTitleTemp)
                        groupTitleTemp = ""
                        extinfBool = false
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadGroupFile(context: Context) {
        groupsArr.clear()
        groupsArr.add("ВСЕ ГРУППЫ")
        val file = File(context.filesDir, GROUPS_FILE)
        try {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    groupsArr.add(line)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadFavoritesFile(context: Context) {
        favArray.clear()
        val file = File(context.filesDir, FAV_FILE)
        try {
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    favArray.add(line.toBoolean())
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadEpgFile(context: Context) {
        epgChannelMap.clear()
        epgProgramMap.clear()
        epgNormalizedMap.clear()
        val file = File(context.filesDir, EPG_FILE)
        if (!file.exists()) return

        val epgTimeFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

        // Нормализация строки даты до полного формата "yyyyMMddHHmmss Z"
        // "2026031816" → "20260318160000 +0000"
        // "20260318160000 +0300" → без изменений
        fun parseEpgDate(str: String): Long {
            try {
                val spaceIdx = str.indexOf(' ')
                val datePart = if (spaceIdx > 0) str.substring(0, spaceIdx) else str
                val tzPart = if (spaceIdx > 0) str.substring(spaceIdx) else " +0000"
                val padded = datePart.padEnd(14, '0') + tzPart
                return epgTimeFormat.parse(padded)?.time ?: 0L
            } catch (_: Exception) {
                return 0L
            }
        }

        try {
            file.bufferedReader().useLines { lines ->
                var currentChannelId = ""
                var inProgramme = false
                var progChannelId = ""
                var progStartMillis = 0L
                var progEndMillis = 0L

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("<channel id=\"")) {
                        currentChannelId = trimmed.substringAfter("<channel id=\"").substringBefore("\"")
                    } else if (trimmed.startsWith("<display-name") && currentChannelId.isNotEmpty()) {
                        val displayName = trimmed.substringAfter(">").substringBefore("<")
                        if (displayName.isNotEmpty()) {
                            epgChannelMap[displayName.lowercase()] = currentChannelId
                        }
                    } else if (trimmed == "</channel>") {
                        currentChannelId = ""
                    } else if (trimmed.startsWith("<programme start=\"")) {
                        val startStr = trimmed.substringAfter("start=\"").substringBefore("\"")
                        val endStr = trimmed.substringAfter("stop=\"").substringBefore("\"")
                        progChannelId = trimmed.substringAfter("channel=\"").substringBefore("\"")
                        progStartMillis = parseEpgDate(startStr)
                        progEndMillis = parseEpgDate(endStr)
                        if (progStartMillis > 0L && progEndMillis > 0L) {
                            inProgramme = true
                        }
                    } else if (inProgramme && trimmed.startsWith("<title")) {
                        val title = trimmed.substringAfter(">").substringBefore("</title>")
                        val program = EpgProgram(progStartMillis, progEndMillis, title)
                        epgProgramMap.getOrPut(progChannelId) { mutableListOf() }.add(program)
                        inProgramme = false
                    } else if (trimmed == "</programme>") {
                        inProgramme = false
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // Строим кеш нормализованных имён для быстрого O(1) поиска
        for ((displayName, channelId) in epgChannelMap) {
            val normalized = normalizeChannelName(displayName)
            if (normalized.isNotEmpty()) {
                epgNormalizedMap.putIfAbsent(normalized, channelId)
            }
        }
    }

    private fun parseUrlString(infStr: String, subStr: String): String {
        if (subStr in infStr) {
            return infStr.substringAfter(subStr).substringBefore('"')
        }
        return ""
    }
}
