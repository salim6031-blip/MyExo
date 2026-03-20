package com.example.myexo1.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.data.GroupData
import com.example.data.MyData
import com.example.myexo1.R
import com.example.myexo1.adapter.GroupAdapter
import com.example.myexo1.adapter.MyAdapter
import com.example.myexo1.utils.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class ChannelListActivity : AppCompatActivity(), GroupAdapter.OnGroupClickListener {

    private lateinit var rvChannelList: RecyclerView
    private lateinit var rvGroupList: RecyclerView
    private lateinit var btnBack: ImageView
    private lateinit var btnGroups: ImageView
    private lateinit var btnFav: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvCount: TextView
    private lateinit var adapter: MyAdapter

    private var channelList = ArrayList<MyData>()
    private var groupList = ArrayList<GroupData>()

    private var currentChNum = 0
    private var currentGrNum = 0
    private var isFav = false
    private var isSearch = false
    private var showGroup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_channel_list)

        rvChannelList = findViewById(R.id.rv_channel_list)
        rvGroupList = findViewById(R.id.rv_group_list)
        btnBack = findViewById(R.id.btn_back)
        btnGroups = findViewById(R.id.btn_groups)
        btnFav = findViewById(R.id.btn_fav)
        tvTitle = findViewById(R.id.tv_title)
        tvCount = findViewById(R.id.tv_count)

        rvChannelList.layoutManager = GridLayoutManager(this, 3)
        rvGroupList.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        currentChNum = intent.getIntExtra("currentChNum", 0)
        currentGrNum = intent.getIntExtra("currentGrNum", 0)
        isFav = intent.getBooleanExtra("isFav", false)
        isSearch = intent.getBooleanExtra("isSearch", false)

        // Данные уже загружены в DataRepository из GroupListActivity
        if (isSearch) {
            fillSearchList()
            btnFav.setImageResource(R.drawable.baseline_star_border)
        } else if (isFav) {
            fillFavList()
            btnFav.setImageResource(R.drawable.baseline_star)
        } else {
            fillChannelList(currentGrNum)
            btnFav.setImageResource(R.drawable.baseline_star_border)
        }
        updateTitle()

        rvChannelList.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (currentChNum < channelList.size) {
                    rvChannelList.scrollToPosition(currentChNum)
                }
                rvChannelList.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        btnBack.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        btnGroups.setOnClickListener {
            showGroup = !showGroup
            if (showGroup) {
                fillGroupList()
                rvGroupList.visibility = View.VISIBLE
                rvGroupList.requestFocus()
            } else {
                rvGroupList.visibility = View.GONE
            }
        }

        btnFav.setOnClickListener {
            isSearch = false
            isFav = !isFav
            if (isFav) {
                fillFavList()
                btnFav.setImageResource(R.drawable.baseline_star)
            } else {
                fillChannelList(currentGrNum)
                btnFav.setImageResource(R.drawable.baseline_star_border)
            }
            updateTitle()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (showGroup) {
            showGroup = false
            rvGroupList.visibility = View.GONE
        } else {
            setResult(RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    private fun updateTitle() {
        val repo = DataRepository
        if (isSearch) {
            tvTitle.text = "НАЙДЕННЫЕ"
        } else if (isFav) {
            tvTitle.text = "ИЗБРАННОЕ"
        } else {
            tvTitle.text = repo.groupsArr.getOrElse(currentGrNum) { "ВСЕ ГРУППЫ" }
        }
        tvCount.text = getString(R.string.channel_count, channelList.size)
    }

    private fun returnSelectedChannel(position: Int) {
        val resultIntent = Intent().apply {
            putExtra("selectedChNum", channelList[position].numData - 1)
            putExtra("currentChNum", position)
            putExtra("currentGrNum", currentGrNum)
            putExtra("isFav", isFav)
            putExtra("isSearch", isSearch)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fillList(data: ArrayList<MyData>) {
        channelList = data
        adapter = MyAdapter(channelList, currentChNum, null, { title ->
            DataRepository.getEpgInfoForChannel(title)
        }, { position ->
            toggleFavorite(position)
        }, R.layout.item_channel_grid)
        adapter.notifyDataSetChanged()
        rvChannelList.adapter = adapter
        adapter.setOnKotlinItemClickListener(object : MyAdapter.OnChannelClickListener {
            override fun onUrlClick(position: Int) {
                currentChNum = position
                returnSelectedChannel(position)
            }
        })
    }

    private fun fillChannelList(grNum: Int) {
        fillList(DataRepository.buildChannelList(grNum))
    }

    private fun fillFavList() {
        fillList(DataRepository.buildFavList())
    }

    private fun fillSearchList() {
        fillList(DataRepository.buildSearchList())
    }

    private fun toggleFavorite(position: Int) {
        val repo = DataRepository
        channelList[position].isFav = !channelList[position].isFav
        repo.favArray[channelList[position].numData - 1] = !repo.favArray[channelList[position].numData - 1]
        adapter.notifyItemChanged(position)
        lifecycleScope.launch(Dispatchers.IO) {
            repo.saveFavList(this@ChannelListActivity)
        }
    }

    private fun fillGroupList() {
        val repo = DataRepository
        groupList.clear()
        for (i in repo.groupsArr.indices) {
            val groupColor = if (currentGrNum == i) "#F0F000" else "#FFFFFF"
            groupList.add(GroupData(repo.groupsArr[i], groupColor))
        }
        val groupAdapter = GroupAdapter(groupList, currentGrNum, null)
        rvGroupList.adapter = groupAdapter
        groupAdapter.setOnKotlinItemClickListener(object : GroupAdapter.OnGroupClickListener {
            override fun onGroupClick(position: Int) {
                currentGrNum = position
                showGroup = false
                rvGroupList.visibility = View.GONE
                isFav = false
                btnFav.setImageResource(R.drawable.baseline_star_border)
                fillChannelList(currentGrNum)
                currentChNum = 0
                updateTitle()
            }
        })
    }

    override fun onGroupClick(position: Int) {
        // handled in fillGroupList
    }
}
