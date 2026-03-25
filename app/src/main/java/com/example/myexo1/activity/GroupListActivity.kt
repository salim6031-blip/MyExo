package com.example.myexo1.activity

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.graphics.Color
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupWindow
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.example.myexo1.R
import com.example.myexo1.adapter.GroupGridAdapter
import com.example.myexo1.adapter.GroupGridItem
import com.example.myexo1.utils.DataRepository
import com.example.myexo1.utils.PlaylistHandler
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
class GroupListActivity : AppCompatActivity() {

    private lateinit var rvGroups: RecyclerView
    private lateinit var tvLoadingStatus: TextView
    private lateinit var pref: SharedPreferences
    private lateinit var channelListLauncher: ActivityResultLauncher<Intent>
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var settingsPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var legacyPermissionLauncher: ActivityResultLauncher<String>

    private val groupItems = ArrayList<GroupGridItem>()

    private var currentChNum = 0
    private var currentGrNum = 0
    private var isFav = false
    private var isSearch = false
    private var dataShown = false
    private var pendingStorageAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_group_list)

        pref = getSharedPreferences("MyPref", MODE_PRIVATE)
        rvGroups = findViewById(R.id.rv_groups)
        tvLoadingStatus = findViewById(R.id.tv_loading_status)
        rvGroups.layoutManager = GridLayoutManager(this, 3)

        channelListLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val data = result.data!!
                    currentGrNum = data.getIntExtra("currentGrNum", currentGrNum)
                    currentChNum = data.getIntExtra("currentChNum", 0)
                    isFav = data.getBooleanExtra("isFav", false)
                    val isSearch = data.getBooleanExtra("isSearch", false)
                    val selectedChNum = data.getIntExtra("selectedChNum", -1)
                    if (selectedChNum >= 0) {
                        launchPlayer(selectedChNum, currentChNum, currentGrNum, isFav, isSearch)
                    }
                }
                // Перезагрузить избранное (могло измениться)
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        DataRepository.reloadFavorites(this@GroupListActivity)
                    }
                    buildGroupItems()
                    showGroups()
                }
            }

        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data ?: return@registerForActivityResult
                    importPlaylistFromUri(uri)
                }
            }

        settingsPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data ?: return@registerForActivityResult
                    performLoadSettings(uri)
                }
            }

        storagePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (hasStoragePermission()) {
                    pendingStorageAction?.invoke()
                } else {
                    Toast.makeText(this, "Нет разрешения на доступ к хранилищу", Toast.LENGTH_SHORT).show()
                }
                pendingStorageAction = null
            }

        legacyPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    pendingStorageAction?.invoke()
                } else {
                    Toast.makeText(this, "Нет разрешения на доступ к хранилищу", Toast.LENGTH_SHORT).show()
                }
                pendingStorageAction = null
            }

        val fabMenu = findViewById<FloatingActionButton>(R.id.fab_menu)
        fabMenu.setOnClickListener { anchorView ->
            data class FabMenuItem(val id: Int, val title: String, val iconRes: Int)

            val loudnessNorm = pref.getBoolean("loudnessNorm", true)
            val items = listOf(
                FabMenuItem(1, "Поиск", R.drawable.search_24),
                FabMenuItem(2,
                    if (loudnessNorm) "Нормализация громкости  ON" else "Нормализация громкости  OFF",
                    R.drawable.ic_volume_normalize),
                FabMenuItem(4, "Загрузить плейлист", R.drawable.playlist_play),
                FabMenuItem(6, "Сохранить настройки", R.drawable.ic_save_settings),
                FabMenuItem(7, "Загрузить настройки", R.drawable.ic_load_settings),
                FabMenuItem(5, "Закрыть", R.drawable.exit_to_app)
            )

            val adapter = object : ArrayAdapter<FabMenuItem>(this, R.layout.item_fab_menu, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context)
                        .inflate(R.layout.item_fab_menu, parent, false)
                    val item = getItem(position)!!
                    view.findViewById<ImageView>(R.id.menu_icon).setImageResource(item.iconRes)
                    view.findViewById<TextView>(R.id.menu_title).text = item.title
                    return view
                }
            }

            val listView = ListView(this)
            listView.adapter = adapter
            listView.divider = null
            listView.setBackgroundColor(Color.parseColor("#424242"))

            // Измеряем содержимое для определения размеров
            var maxWidth = 0
            var totalHeight = 0
            for (i in 0 until adapter.count) {
                val itemView = adapter.getView(i, null, listView)
                itemView.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                maxWidth = maxOf(maxWidth, itemView.measuredWidth)
                totalHeight += itemView.measuredHeight
            }

            val popup = PopupWindow(listView, maxWidth, totalHeight, true)
            popup.elevation = 8f

            listView.setOnItemClickListener { _, _, position, _ ->
                popup.dismiss()
                when (items[position].id) {
                    1 -> showSearchDialog()
                    2 -> {
                        val newValue = !pref.getBoolean("loudnessNorm", true)
                        pref.edit { putBoolean("loudnessNorm", newValue) }
                        Toast.makeText(this,
                            if (newValue) "Нормализация громкости включена"
                            else "Нормализация громкости выключена",
                            Toast.LENGTH_SHORT).show()
                    }
                    4 -> openPlaylistFilePicker()
                    6 -> saveSettings()
                    7 -> loadSettings()
                    5 -> finishAffinity()
                }
            }

            // Показываем popup выше FAB
            popup.showAsDropDown(
                anchorView,
                0,
                -(totalHeight + anchorView.height)
            )
        }

        getSettings()
        val fromPlayer = intent.getBooleanExtra("fromPlayer", false)
        checkFirstRun(autoLaunch = !fromPlayer)
    }

    override fun onResume() {
        super.onResume()
        if (dataShown && DataRepository.playlistLoaded) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    DataRepository.reloadFavorites(this@GroupListActivity)
                }
                buildGroupItems()
                showGroups()
            }
        }
    }

    private fun getSettings() {
        currentChNum = pref.getInt("currentChNum", 0)
        currentGrNum = pref.getInt("currentGrNum", 0)
        val fav = pref.getString("isFav", "false")
        if (fav != null && fav.isNotEmpty()) isFav = fav.toBoolean()
        isSearch = pref.getBoolean("isSearch", false)
    }

    private fun checkFirstRun(autoLaunch: Boolean = true) {
        if (DataRepository.playlistExists(this)) {
            lifecycleScope.launch {
                if (!DataRepository.playlistLoaded) {
                    tvLoadingStatus.visibility = android.view.View.VISIBLE
                    tvLoadingStatus.setText(R.string.loading_playlist)
                }
                withContext(Dispatchers.IO) {
                    DataRepository.loadAll(this@GroupListActivity)
                }
                buildGroupItems()
                showGroups()
                dataShown = true

                // Сразу открываем последний канал (только при запуске из лаунчера)
                if (autoLaunch) {
                    val repo = DataRepository
                    if (repo.urlCh.isNotEmpty()) {
                        val chList = when {
                            isSearch -> repo.buildSearchList()
                            isFav -> repo.buildFavList()
                            else -> repo.buildChannelList(currentGrNum)
                        }
                        val safeChNum = if (currentChNum < chList.size) currentChNum else 0
                        if (chList.isNotEmpty()) {
                            launchPlayer(chList[safeChNum].numData - 1, safeChNum, currentGrNum, isFav, isSearch)
                        }
                    }
                }

                // EPG загружается в фоне, не блокируя запуск
                if (!DataRepository.epgLoaded) {
                    tvLoadingStatus.setText(R.string.updating_epg)
                }
                withContext(Dispatchers.IO) {
                    DataRepository.loadEpg(this@GroupListActivity)
                }
                tvLoadingStatus.visibility = android.view.View.GONE
            }
        } else {
            openPlaylistFilePicker()
        }
    }

    private fun buildGroupItems() {
        groupItems.clear()
        val repo = DataRepository
        val totalChannels = repo.groupTitle.size
        val countMap = repo.countByGroup()

        groupItems.add(GroupGridItem("Все каналы", totalChannels, groupIndex = 0))
        val favCount = repo.favArray.count { it }
        groupItems.add(GroupGridItem("Избранное", favCount, isSpecial = true))

        // «Найденные» — после «Избранное»
        if (repo.searchResults.isNotEmpty() || repo.hasSearchResults(this)) {
            if (repo.searchResults.isEmpty()) repo.loadSearchResults(this)
            if (repo.searchResults.isNotEmpty()) {
                groupItems.add(GroupGridItem("Найденные", repo.searchResults.size, isSearch = true))
            }
        }

        for (i in 1 until repo.groupsArr.size) {
            val name = repo.groupsArr[i]
            val count = countMap[name] ?: 0
            if (count > 0) {
                groupItems.add(GroupGridItem(name, count, groupIndex = i))
            }
        }
    }

    private fun showGroups() {
        val selectedPos = groupItems.indexOfFirst { item ->
            when {
                isSearch -> item.isSearch
                isFav -> item.isSpecial
                else -> !item.isSpecial && !item.isSearch && item.groupIndex == currentGrNum
            }
        }
        val adapter = GroupGridAdapter(groupItems, selectedPos) { position ->
            val item = groupItems[position]
            when {
                item.isSpecial -> openChannelList(0, isFav = true)
                item.isSearch -> openChannelList(0, isFav = false, isSearch = true)
                else -> openChannelList(item.groupIndex, isFav = false)
            }
        }
        rvGroups.adapter = adapter
        if (selectedPos >= 0) {
            rvGroups.scrollToPosition(selectedPos)
        }
    }

    private fun openChannelList(grNum: Int, isFav: Boolean, isSearch: Boolean = false) {
        val intent = Intent(this, ChannelListActivity::class.java).apply {
            putExtra("currentChNum", 0)
            putExtra("currentGrNum", grNum)
            putExtra("isFav", isFav)
            putExtra("isSearch", isSearch)
        }
        channelListLauncher.launch(intent)
    }

    private fun loadSearchHistory(): MutableList<String> {
        val raw = pref.getString("searchHistory", "") ?: ""
        if (raw.isEmpty()) return mutableListOf()
        return raw.split("\n").filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveSearchHistory(history: List<String>) {
        pref.edit { putString("searchHistory", history.joinToString("\n")) }
    }

    private fun addToSearchHistory(query: String) {
        val history = loadSearchHistory()
        history.remove(query)
        history.add(0, query)
        if (history.size > 5) history.subList(5, history.size).clear()
        saveSearchHistory(history)
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) return
        addToSearchHistory(query)
        val repo = DataRepository
        val results = repo.searchChannels(query)
        if (results.isNotEmpty()) {
            repo.saveSearchResults(this)
            buildGroupItems()
            showGroups()
            openChannelList(0, isFav = false, isSearch = true)
        } else {
            Toast.makeText(this, "Ничего не найдено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSearchDialog() {
        val history = loadSearchHistory()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val historyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        for (item in history) {
            val tv = TextView(this).apply {
                text = item
                textSize = 16f
                setTextColor("#FAFD9A".toColorInt())
                setPadding(16, 20, 16, 20)
            }
            historyLayout.addView(tv)
        }
        val historyScroll = android.widget.ScrollView(this).apply {
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (150 * resources.displayMetrics.density).toInt()
            )
            addView(historyLayout)
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val editText = EditText(this).apply {
            hint = "Название канала"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        inputRow.addView(editText)
        if (history.isNotEmpty()) {
            val historyBtn = android.widget.ImageButton(this).apply {
                setImageResource(R.drawable.arrow_drop_down_24)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(8, 8, 8, 8)
                setOnClickListener {
                    historyScroll.isVisible = !historyScroll.isVisible
                }
            }
            inputRow.addView(historyBtn)
        }

        layout.addView(inputRow)
        layout.addView(historyScroll)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Поиск каналов")
            .setView(layout)
            .setPositiveButton("Найти") { _, _ ->
                performSearch(editText.text.toString().trim())
            }
            .setNegativeButton("Отмена", null)
            .show()

        // Клик по элементу истории вставляет текст и скрывает список
        for (i in 0 until historyLayout.childCount) {
            historyLayout.getChildAt(i).setOnClickListener {
                val text = (it as TextView).text.toString()
                editText.setText(text)
                editText.setSelection(text.length)
                historyScroll.visibility = android.view.View.GONE
            }
        }

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(editText.text.toString().trim())
                dialog.dismiss()
                true
            } else false
        }
    }

    private fun openPlaylistFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/x-mpegurl",
                "audio/mpegurl",
                "application/x-mpegurl",
                "application/vnd.apple.mpegurl",
                "application/octet-stream"
            ))
            // Открываем папку Download по умолчанию
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val downloadsUri = Uri.Builder()
                    .scheme("content")
                    .authority("com.android.externalstorage.documents")
                    .appendPath("document")
                    .appendPath("primary:${Environment.DIRECTORY_DOWNLOADS}")
                    .build()
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri)
            }
        }
        filePickerLauncher.launch(intent)
    }

    private fun importPlaylistFromUri(uri: Uri) {
        lifecycleScope.launch {
            tvLoadingStatus.visibility = android.view.View.VISIBLE
            tvLoadingStatus.setText(R.string.loading_playlist)
            val success = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val file = java.io.File(filesDir, "playlist.m3u")
                        file.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    val playlistHandler = PlaylistHandler(this@GroupListActivity)
                    playlistHandler.extractGroupsFromPlaylist("playlist.m3u")
                    playlistHandler.createIsFavoriteFile("playlist.m3u")
                    DataRepository.reloadAll(this@GroupListActivity)
                    DataRepository.autoFavoriteChannels(
                        this@GroupListActivity,
                        listOf("РБК", "Известия")
                    )
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            tvLoadingStatus.visibility = android.view.View.GONE
            if (success) {
                buildGroupItems()
                showGroups()
                dataShown = true
                Toast.makeText(this@GroupListActivity, "Плейлист загружен", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@GroupListActivity, "Ошибка загрузки плейлиста", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmClearFavorites() {
        val favCount = DataRepository.favArray.count { it }
        if (favCount == 0) {
            Toast.makeText(this, "Список избранного пуст", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Очистить избранное")
            .setMessage("Удалить все каналы из избранного ($favCount)?")
            .setPositiveButton("Очистить") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        DataRepository.clearFavorites(this@GroupListActivity)
                    }
                    buildGroupItems()
                    showGroups()
                    Toast.makeText(this@GroupListActivity, "Избранное очищено", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun saveSettings() {
        if (!hasStoragePermission()) {
            pendingStorageAction = { saveSettings() }
            requestStoragePermission()
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val repo = DataRepository
                    val destDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "exo_settings"
                    )
                    destDir.mkdirs()
                    val channelCount = repo.urlCh.size
                    val zipFile = File(destDir, "settings_${channelCount}.bak")
                    if (zipFile.exists()) zipFile.delete()

                    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                        val filesDir = filesDir
                        val prefsDir = File(applicationInfo.dataDir, "shared_prefs")

                        fun addFolderToZip(folder: File, baseName: String) {
                            val files = folder.listFiles() ?: return
                            for (file in files) {
                                if (file.isDirectory) {
                                    addFolderToZip(file, "$baseName/${file.name}")
                                } else {
                                    zos.putNextEntry(ZipEntry("$baseName/${file.name}"))
                                    FileInputStream(file).use { it.copyTo(zos) }
                                    zos.closeEntry()
                                }
                            }
                        }

                        addFolderToZip(filesDir, "files")
                        if (prefsDir.exists()) {
                            addFolderToZip(prefsDir, "shared_prefs")
                        }
                    }
                    zipFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            if (result != null) {
                Toast.makeText(this@GroupListActivity, "Настройки сохранены: $result", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@GroupListActivity, "Ошибка сохранения настроек", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            storagePermissionLauncher.launch(intent)
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun loadSettings() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        settingsPickerLauncher.launch(intent)
    }

    private fun performLoadSettings(uri: android.net.Uri) {
        AlertDialog.Builder(this)
            .setTitle("Загрузить настройки")
            .setMessage("Текущие настройки будут заменены из архива. Продолжить?")
            .setPositiveButton("Загрузить") { _, _ ->
                lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        try {
                            val filesDir = filesDir
                            val prefsDir = File(applicationInfo.dataDir, "shared_prefs")
                            val dataDir = File(applicationInfo.dataDir)

                            // Очистить папки files и shared_prefs
                            filesDir.deleteRecursively()
                            filesDir.mkdirs()
                            prefsDir.deleteRecursively()
                            prefsDir.mkdirs()

                            // Распаковать архив
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                ZipInputStream(inputStream).use { zis ->
                                    var entry: ZipEntry? = zis.nextEntry
                                    while (entry != null) {
                                        if (!entry.isDirectory) {
                                            val outFile = File(dataDir, entry.name)
                                            outFile.parentFile?.mkdirs()
                                            FileOutputStream(outFile).use { fos ->
                                                zis.copyTo(fos)
                                            }
                                        }
                                        zis.closeEntry()
                                        entry = zis.nextEntry
                                    }
                                }
                            }
                            true
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                    }
                    if (success) {
                        Toast.makeText(this@GroupListActivity, "Настройки загружены. Перезапуск…", Toast.LENGTH_SHORT).show()
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finishAffinity()
                        Runtime.getRuntime().exit(0)
                    } else {
                        Toast.makeText(this@GroupListActivity, "Ошибка загрузки настроек", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun launchPlayer(selectedChNum: Int, currentChNum: Int, currentGrNum: Int, isFav: Boolean, isSearch: Boolean = false) {
        pref.edit {
            putInt("currentChNum", currentChNum)
            putInt("currentGrNum", currentGrNum)
            putString("isFav", isFav.toString())
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("selectedChNum", selectedChNum)
            putExtra("currentChNum", currentChNum)
            putExtra("currentGrNum", currentGrNum)
            putExtra("isFav", isFav)
            putExtra("isSearch", isSearch)
        }
        startActivity(intent)
    }
}
