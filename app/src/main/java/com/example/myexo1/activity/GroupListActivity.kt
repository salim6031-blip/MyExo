package com.example.myexo1.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.graphics.Color
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var pref: SharedPreferences
    private lateinit var channelListLauncher: ActivityResultLauncher<Intent>

    private val groupItems = ArrayList<GroupGridItem>()

    private var currentChNum = 0
    private var currentGrNum = 0
    private var isFav = false
    private var isSearch = false
    private var dataShown = false

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
                DataRepository.reloadFavorites(this)
                buildGroupItems()
                showGroups()
            }

        val fabMenu = findViewById<FloatingActionButton>(R.id.fab_menu)
        fabMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view, Gravity.TOP or Gravity.END)
            popup.menu.add(0, 1, 0, "Поиск")
            val normItem = popup.menu.add(0, 2, 1, "Нормализация громкости")
            normItem.isCheckable = true
            normItem.isChecked = pref.getBoolean("loudnessNorm", false)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        showSearchDialog()
                        true
                    }
                    2 -> {
                        val newValue = !menuItem.isChecked
                        menuItem.isChecked = newValue
                        pref.edit { putBoolean("loudnessNorm", newValue) }
                        Toast.makeText(this,
                            if (newValue) "Нормализация громкости включена"
                            else "Нормализация громкости выключена",
                            Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        getSettings()
        val fromPlayer = intent.getBooleanExtra("fromPlayer", false)
        checkFirstRun(autoLaunch = !fromPlayer)
    }

    override fun onResume() {
        super.onResume()
        if (dataShown && DataRepository.playlistLoaded) {
            DataRepository.reloadFavorites(this)
            buildGroupItems()
            showGroups()
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
                // EPG загружаем в фоне
                withContext(Dispatchers.IO) {
                    DataRepository.loadEpg(this@GroupListActivity)
                }
            }
        } else {
            lifecycleScope.launch {
                val playlistHandler = PlaylistHandler(this@GroupListActivity)
                val playlistUrl = "http://rafail1982.ru/test.m3u"
                val isSuccess = withContext(Dispatchers.IO) {
                    playlistHandler.downloadPlaylist(playlistUrl)
                }
                if (isSuccess) {
                    playlistHandler.extractGroupsFromPlaylist("playlist.m3u")
                    playlistHandler.createIsFavoriteFile("playlist.m3u")
                    DataRepository.reloadAll(this@GroupListActivity)
                    buildGroupItems()
                    showGroups()
                    dataShown = true
                }
            }
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
        val adapter = GroupGridAdapter(groupItems) { position ->
            val item = groupItems[position]
            when {
                item.isSpecial -> openChannelList(0, isFav = true)
                item.isSearch -> openChannelList(0, isFav = false, isSearch = true)
                else -> openChannelList(item.groupIndex, isFav = false)
            }
        }
        rvGroups.adapter = adapter
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
                setTextColor(Color.parseColor("#FAFD9A"))
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
                    historyScroll.visibility = if (historyScroll.visibility == android.view.View.VISIBLE)
                        android.view.View.GONE else android.view.View.VISIBLE
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
