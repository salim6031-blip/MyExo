package com.example.myexo1.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
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
        rvGroups.layoutManager = GridLayoutManager(this, 2)

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
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        showSearchDialog()
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

    private fun showSearchDialog() {
        val editText = EditText(this).apply {
            hint = "Название канала"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Поиск каналов")
            .setView(editText)
            .setPositiveButton("Найти") { _, _ ->
                val query = editText.text.toString().trim()
                if (query.isNotEmpty()) {
                    val repo = DataRepository
                    val results = repo.searchChannels(query)
                    if (results.isNotEmpty()) {
                        repo.saveSearchResults(this)
                        buildGroupItems()
                        showGroups()
                    } else {
                        Toast.makeText(this, "Ничего не найдено", Toast.LENGTH_SHORT).show()
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
