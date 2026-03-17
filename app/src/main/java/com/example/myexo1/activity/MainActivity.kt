package com.example.myexo1.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager

import android.net.Uri
import android.os.Build
import android.os.Bundle
//import android.util.Log

import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.data.EpgProgram
import com.example.data.GroupData
import com.example.data.MyData
import com.example.myexo1.R
import com.example.myexo1.adapter.GroupAdapter
import com.example.myexo1.adapter.MyAdapter
import com.example.myexo1.databinding.ActivityMainBinding
import com.example.myexo1.utils.PlaylistHandler
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class MainActivity : AppCompatActivity(), MyAdapter.OnChannelClickListener,
    GroupAdapter.OnGroupClickListener {
    private lateinit var binding: ActivityMainBinding
    private var player: ExoPlayer? = null
    private var channelList = ArrayList<MyData>()


    private val chNumber = ArrayList<Int>()
    private val titleCh = ArrayList<String>()
    private val urlCh = ArrayList<String>()
    private val tvgLogo = ArrayList<String>()
    private val tvgId = ArrayList<String>()
    private val groupTitle = ArrayList<String>()

    private var groupList = ArrayList<GroupData>()
    private val groupsArr = ArrayList<String>()

    // private val tvgArr = ArrayList<String>()
    private val favArray = ArrayList<Boolean>()
    private val epgChannelMap = HashMap<String, String>()   // displayName.lowercase -> channelId
    private val epgProgramMap = HashMap<String, MutableList<EpgProgram>>() // channelId -> programs
    private val localChannelListFileName: String = "playlist.m3u"
    private val groupListFileName: String = "groups.txt"
    private val favListFileName: String = "isFavorite.txt"
    private var currentChNum = 0
    private var currentGrNum = 0
    private var urlCount = 0
    private var showInfo = false
    private var showGroup = false
    private var showList = false
    private var isFav = false
    private var showStatusBar = false
    private var showButtons = false
    private var timer = Timer()
    private var timer2 = Timer()  //таймер для кнопок
    private var epgTimer: Timer? = null  // таймер проверки смены суток для EPG
    private var urlString = "https://igi-hls.cdnvideo.ru/igi/igi_tcode/playlist.m3u8"
    private lateinit var pref: SharedPreferences
    private lateinit var adapter: MyAdapter
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var audioManager: AudioManager
    private var zoomMode = 0
    private var lastButton = 0
    private var oldChannel = 0
    private var epgDate = "25.03.2025"
    private var epgProgress = 0
    private var epgMax = 100
    private lateinit var openPlaylistLauncher: ActivityResultLauncher<Intent>
    private var volumeSwipeActive = false
    private var lastVolumeY = 0f
    private val volumeSwipeRegionRatio = 0.8f
    private var volumeHideTimer: Timer? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        pref = getSharedPreferences("MyPref", MODE_PRIVATE)

        binding = ActivityMainBinding.inflate(layoutInflater)

        binding.leftButton.setOnClickListener {
            lastButton = 0
            binding.rvChannelListView.visibility = View.INVISIBLE
            prevUrlStart()
            timerButtonsHide()
        }

        binding.rightButton.setOnClickListener {
            lastButton = 1
            binding.rvChannelListView.visibility = View.INVISIBLE
            nextUrlStart()
            timerButtonsHide()
        }

        binding.listButton.setOnClickListener {
            lastButton = 2
            showList = !showList
            channelListShow(showList)
        }

        binding.groupButton.setOnClickListener {
            lastButton = 3
            binding.rvChannelListView.visibility = View.INVISIBLE // прячем лист
            showGroup = !showGroup
            groupListShow(showGroup)
            fillGroupListFromLocalFile()
        }

        binding.favButton.setOnClickListener {
            lastButton = 4
            isFav = !isFav
            setFavIcon(isFav)
            showList = true
            channelListShow(showList)
        }

//        binding.infoButton.setOnClickListener {
//            lastButton = 5
//            showStatusBar = true
//            infoShow(15000.0)
//            timerButtonsHide(15000.0)
//        }

        binding.zoomButton.setOnClickListener {
            lastButton = 6
            zoomMode++
            if (zoomMode > 2) zoomMode = 0
            setZoomMode(zoomMode)
        }

        binding.stopButton.setOnClickListener { stopShow() }

        binding.infoStarIcon.setOnClickListener {
            toggleFavorite(currentChNum) // Обработка нажатия на "Избранное"
            showInfo = false
            infoShow(10000.0)
        }

        openPlaylistLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val uri = result.data?.data
                    if (uri != null) {
                        contentResolver.openInputStream(uri)?.use { input ->
                            val text = input.bufferedReader().readText()
                            writePlaylistFromText(text)
                        }
                    }
                }
            }

        binding.infoChannelImage.setOnClickListener { // вызов окна загрузки плейлиста
            currentChNum = 0
            currentGrNum = 0
            saveSettings(currentChNum, currentGrNum, zoomMode, epgDate, isFav)
            openPlaylistFromDownloads()
        }

        binding.rvChannelListView.layoutManager =
            LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.rvGroupListView.layoutManager =
            LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        setContentView(binding.root)
        val swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                binding.emptyCanvas.performClick()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                binding.emptyCanvas.performLongClick()
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val startEvent = e1 ?: return false
                val diffX = e2.x - startEvent.x
                val diffY = e2.y - startEvent.y
                val swipeDistance = 120
                val swipeVelocity = 120
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) &&
                    kotlin.math.abs(diffX) > swipeDistance &&
                    kotlin.math.abs(velocityX) > swipeVelocity
                ) {
                    if (diffX > 0) {
                        prevUrlStart()
                    } else {
                        nextUrlStart()
                    }
                    timerButtonsHide()
                    return true
                }
                return false
            }
        })
        binding.emptyCanvas.setOnClickListener {
            showButtons = !showButtons
            canvasClickEvent(showButtons)
            setActiveButton()
            if (!showButtons) {
                showInfo = true
                infoShow(10.0)
            }
        }
        binding.emptyCanvas.setOnLongClickListener {
            showButtons = !showButtons
            showStatusBar = true
            canvasClickEvent(showButtons)
            if (!showButtons) {
                showInfo = true
                infoShow(10.0)
            }
            return@setOnLongClickListener true
        }
        binding.emptyCanvas.setOnTouchListener { view, event ->
            if (!handleVolumeSwipe(view, event)) {
                swipeDetector.onTouchEvent(event)
            }
            true
        }


        getSettings()  // читаем сохраненный настройки

        // Восстановление состояния при повороте
        if (savedInstanceState != null) {
            currentChNum = savedInstanceState.getInt("currentChNum", 0)
            currentGrNum = savedInstanceState.getInt("currentGrNum", 0)
            zoomMode = savedInstanceState.getInt("zoomMode", 0)
            isFav = savedInstanceState.getBoolean("isFav", false)
            //Log.d("mylog", "Create = $currentChNum  $currentGrNum  $zoomMode")
        }

        checkFirstRun()     // проверка на первый запуск
        updateEpg()
        scheduleEpgMidnightUpdate()

        //Log.d("mylog", "urlCh.size = ${urlCh.size}")

        // Инициализация AudioManager
        audioManager = getSystemService(AudioManager::class.java)

//        // Вызов системного регулятора громкости
//        binding.volumeClick.setOnClickListener() {
//            showSystemVolumeControl()
//        }


    }

    @SuppressLint("SimpleDateFormat")
    private fun updateEpg() {  // если дата последнего скаченного епг не совпадает с текущей, то скачиваем новый епг
        if (epgDate != SimpleDateFormat("dd.MM.yyyy").format(Date())) {
            lifecycleScope.launch {
                val epgHandler = PlaylistHandler(this@MainActivity)
                val epgUrl = "http://epg.one/epg.xml"
                withContext(Dispatchers.IO) {
                    epgHandler.downloadEpg(epgUrl)
                }
                epgDate = SimpleDateFormat("dd.MM.yyyy").format(Date())
                loadEpgFile()
            }
        } else {
            loadEpgFile()
        }
    }

    private fun scheduleEpgMidnightUpdate() {
        epgTimer?.cancel()
        // вычисляем миллисекунды до полуночи + 1 минута запас
        val now = java.util.Calendar.getInstance()
        val midnight = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 1)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val delayMs = midnight.timeInMillis - now.timeInMillis
        epgTimer = Timer()
        epgTimer?.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    updateEpg()
                    scheduleEpgMidnightUpdate() // запланировать на следующие сутки
                }
            }
        }, delayMs)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if ((showList) || (showGroup)) {
            if (showList) {
                showList = false
                channelListShow(showList)
            }
            if (showGroup) {
                showGroup = false
                groupListShow(showGroup)
            }
        } else {
            saveSettings(currentChNum, currentGrNum, zoomMode, epgDate, isFav)
            super.onBackPressed()
        }
    }

    private fun setActiveButton() {   // после вызова кнопок сразу делаем послееднюю нажатую кнопку активной
        when (lastButton) {
            0 -> binding.leftButton.requestFocus()
            1 -> binding.rightButton.requestFocus()
            2 -> binding.listButton.requestFocus()
            3 -> binding.groupButton.requestFocus()
            4 -> binding.favButton.requestFocus()
            5 -> binding.zoomButton.requestFocus()
        }
    }

    @OptIn(UnstableApi::class)
    private fun setZoomMode(zoomMode: Int) {
        when (zoomMode) {
            0 -> binding.videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            1 -> binding.videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            2 -> binding.videoView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        }
    }

    private fun setFavIcon(isf: Boolean) {
        if (isf) {
            fillFavlListFromArrays()
            binding.favButton.setImageResource(R.drawable.baseline_star)
        } else {
            fillChannelListFromArrays(currentGrNum)
            binding.favButton.setImageResource(R.drawable.baseline_star_border)
        }
    }

    private fun canvasClickEvent(sc: Boolean) = // sc = showButtons
        with(binding) {
            if (sc) {
                zoomButton.visibility = View.VISIBLE
                listButton.visibility = View.VISIBLE
                groupButton.visibility = View.VISIBLE
                favButton.visibility = View.VISIBLE
                leftButton.visibility = View.VISIBLE
                rightButton.visibility = View.VISIBLE
                stopButton.visibility = View.VISIBLE
                ovalRectangle2.visibility = View.VISIBLE
                logoExo.visibility = View.VISIBLE
                timerButtonsHide()
                showInfo = false
                infoShow(15000.0)
            } else {
                zoomButton.visibility = View.GONE
                listButton.visibility = View.GONE
                groupButton.visibility = View.GONE
                favButton.visibility = View.GONE
                leftButton.visibility = View.GONE
                rightButton.visibility = View.GONE
                stopButton.visibility = View.GONE
                ovalRectangle2.visibility = View.GONE
                logoExo.visibility = View.GONE
                showStatusBar = false
            }
        }

//    private fun showSystemVolumeControl() {
//        // Отображаем системный регулятор громкости
//        audioManager.adjustStreamVolume(
//            AudioManager.STREAM_MUSIC,
//            AudioManager.ADJUST_SAME,
//            AudioManager.FLAG_SHOW_UI
//        )
//    }

    private fun checkFirstRun() {
        try {
            val filepath: File? = filesDir
            val file = File(filepath, localChannelListFileName)
            if (file.exists()) {
                loadChannelFile()    // читаем локальный плейлист
                loadGroupFile()      // читаем локальный плейлист
                loadFavoritesFile()  // читаем список фаоритных каналов
                if (isFav) {
                    fillFavlListFromArrays()
                } else {
                    fillChannelListFromArrays(currentGrNum) // при старте грузим список каналов сохраненной группы
                }
                setFavIcon(isFav)
                if (currentChNum >= urlCh.size) currentChNum = 0
                setupVideoView(channelList[currentChNum].numData - 1)
            } else {                // если первый запуск, то загружаем плейлист
                lifecycleScope.launch {
                    // Создаем экземпляр PlaylistHandler
                    val playlistHandler = PlaylistHandler(this@MainActivity)
                    // URL плейлиста
                    val playlistUrl = "http://rafail1982.ru/test.m3u"
                    //val playlistUrl = "http://rafail1982.ru/salim/cas.m3u"
                    // Загружаем плейлист
                    val isSuccess = withContext(Dispatchers.IO) {
                        playlistHandler.downloadPlaylist(playlistUrl)
                    }
                    if (isSuccess) {
                        // Извлекаем группы и сохраняем их в файл "group.txt"
                        playlistHandler.extractGroupsFromPlaylist("playlist.m3u")
                        // Создаем файл "isFavorite.txt"
                        playlistHandler.createIsFavoriteFile("playlist.m3u")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        if (player != null) return
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            )
            .build().apply {
                playWhenReady = true
            }
        binding.videoView.player = player
    }

    private fun setupVideoView(un: Int) {
        setZoomMode(zoomMode)
        urlString = urlCh[un]
        initPlayer()
        player?.setMediaItem(MediaItem.fromUri(urlString))
        player?.prepare()
        showInfo = false
        infoShow(10000.0)
    }

    private fun timerInfoHide(ms: Double) {
        timer.cancel()
        timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    showInfo = false
                    binding.infoCard.visibility = View.GONE
                    binding.logoExo.visibility = View.GONE
                    WindowInsetsControllerCompat(
                        window,
                        window.decorView
                    ).hide(WindowInsetsCompat.Type.statusBars())    // прячем системное меню
                }
            }
        }, ms.toLong(), 3000)
    }

    private fun timerButtonsHide() {
        timer2.cancel()
        timer2 = Timer()
        timer2.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    showButtons = false
                    canvasClickEvent(showButtons)
                    binding.leftButton.requestFocus() // возвращаем управление канвасу
                }
            }
        }, 15000, 3000)
    }

    private fun nextUrlStart() {
        currentChNum++
        if (currentChNum > channelList.size - 1) currentChNum = 0
        adapter.updateArgument(currentChNum)  //  меняем текущую позицию в списке каналов
        binding.rvChannelListView.postDelayed({
            binding.rvChannelListView.smoothScrollToPosition(currentChNum)  // сдвигаем курсор на текущий канал из списка
        }, 100)
        showStatusBar = false
        setupVideoView(channelList[currentChNum].numData - 1)
    }

    private fun prevUrlStart() {
        if (currentChNum > 0) {
            currentChNum--
            adapter.updateArgument(currentChNum)  //  меняем текущую позицию в списке каналов
            binding.rvChannelListView.postDelayed({
                binding.rvChannelListView.smoothScrollToPosition(currentChNum)
            }, 100)
            showStatusBar = false
            setupVideoView(channelList[currentChNum].numData - 1)
        }
    }

    private fun channelListShow(shl: Boolean) {
        binding.infoCard.visibility = View.GONE
        if (shl) {
            binding.rvChannelListView.viewTreeObserver.addOnGlobalLayoutListener(object :  // позиционируем текущий айтем в листе
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.rvChannelListView.scrollToPosition(currentChNum)
                    binding.rvChannelListView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
            binding.rvChannelListView.visibility = View.VISIBLE
            binding.rvChannelListView.requestFocus()
        } else {
            binding.rvChannelListView.visibility = View.INVISIBLE
            // binding.listButton.requestFocus()
        }
    }

    private fun groupListShow(sh_rgr: Boolean) {
        //Log.d("malog", "")
        if (sh_rgr) {
            binding.rvGroupListView.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {             // позиционируем текущий айтем в листе
                    binding.rvGroupListView.scrollToPosition(currentGrNum)
                    binding.rvGroupListView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
            binding.rvGroupListView.visibility = View.VISIBLE
            binding.rvGroupListView.requestFocus()
        } else {
            binding.rvGroupListView.visibility = View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun infoShow(timeMs: Double) {
        //Log.d("mylog", "${channelList.size}   channelList = $channelList")
        showInfo = !showInfo
        //Log.d("mylog", "currentChNum = $currentChNum   $currentGrNum")
        with(binding) {
            if (showInfo) {
                infoNumData.text = (channelList[currentChNum].numData).toString()
                infoTitleData.text = channelList[currentChNum].titleData
                infoGroupData.text = "(${channelList[currentChNum].groupTitle})"
                channelCountTv.text = "Всего каналов: ${urlCh.size}"
                groupCountTv.text = "В группе: ${channelList.size} (${currentChNum + 1})"
                var media = tvgLogo[channelList[currentChNum].numData - 1]
                if (media.startsWith("//")) media = "https:$media"
                if (media.trim() != "") {
                    Glide.with(infoChannelImage)
                        .load(media)
                        .placeholder(R.drawable.smart_tv48)
                        .error(R.drawable.smart_tv48)
                        .into(infoChannelImage)
                } else {
                    Glide.with(infoChannelImage).clear(infoChannelImage)
                    infoChannelImage.setImageResource(R.drawable.smart_tv48)
                }
                if (channelList[currentChNum].isFav) {
                    infoStarIcon.setImageResource(R.drawable.baseline_star)
                } else {
                    infoStarIcon.setImageResource(R.drawable.baseline_star_border)
                }
                infoCard.visibility = View.VISIBLE
                if (showStatusBar) {
                    WindowInsetsControllerCompat(
                        window,
                        window.decorView
                    ).show(WindowInsetsCompat.Type.statusBars())
                    binding.logoExo.visibility = View.VISIBLE
                }
                timerInfoHide(timeMs)

                // Сначала пробуем tvg-id из плейлиста (прямой матч), потом fallback по имени
                var epgId = channelList[currentChNum].tvgId
                if (epgId.isEmpty() || !epgProgramMap.containsKey(epgId)) {
                    epgId = getChannelId(channelList[currentChNum].titleData)
                }
                binding.epgText.text = ""
                if (epgId.isNotEmpty()) {
                    binding.epgText.text = getIdEpg(epgId)
                }
                binding.epgProgressBar.max = epgMax
                binding.epgProgressBar.progress = epgProgress


            } else {
                infoCard.visibility = View.GONE
                WindowInsetsControllerCompat(
                    window,
                    window.decorView
                ).hide(WindowInsetsCompat.Type.statusBars())
                timer.cancel()
            }
        }
    }

    private fun getChannelId(chTitle: String): String {
        return epgChannelMap[chTitle.lowercase()] ?: ""
    }

    private fun getIdEpg(id: String): String {
        epgMax = 0
        epgProgress = 0
        val programs = epgProgramMap[id] ?: return ""
        val displayTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val nowMillis = System.currentTimeMillis()

        for (prog in programs) {
            if (nowMillis in prog.startMillis until prog.endMillis) {
                val shortEpg = prog.title.replace(
                    "\\[[0-9]+\\+]".toRegex(), ""
                )  // вырезаем возрастной ценз
                val start = displayTimeFormat.format(Date(prog.startMillis))
                val end = displayTimeFormat.format(Date(prog.endMillis))
                epgMax = ((prog.endMillis - prog.startMillis) / 1000).toInt()
                epgProgress = ((nowMillis - prog.startMillis) / 1000).toInt()
                return "$shortEpg ($start - $end)"
            }
        }
        return ""
    }

    private fun stopShow() {
        saveSettings(currentChNum, currentGrNum, zoomMode, epgDate, isFav)
        finish()
    }

    @SuppressLint("SetTextI18n")
    private fun handleVolumeSwipe(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                volumeSwipeActive = event.x >= view.width * volumeSwipeRegionRatio
                lastVolumeY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!volumeSwipeActive) return false
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                // Полный свайп по высоте экрана = полный диапазон громкости (×3 для плавности)
                val stepPx = (view.height.toFloat() / maxVolume) * 3f
                val dy = lastVolumeY - event.y
                if (kotlin.math.abs(dy) >= stepPx) {
                    val direction =
                        if (dy > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        direction,
                        0  // без системного UI
                    )
                    lastVolumeY = event.y
                }
                // Показываем свой прогрессбар громкости
                showVolumeBar()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasActive = volumeSwipeActive
                volumeSwipeActive = false
                if (wasActive) scheduleVolumeBarHide()
                return wasActive
            }
        }
        return volumeSwipeActive
    }

    @SuppressLint("SetTextI18n")
    private fun showVolumeBar() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val percent = (currentVolume * 100) / maxVolume
        binding.volumeProgressBar.max = maxVolume
        binding.volumeProgressBar.progress = currentVolume
        binding.volumePercentText.text = "$percent%"
        binding.volumeBarLayout.visibility = View.VISIBLE
        // Сбрасываем таймер скрытия при каждом движении
        volumeHideTimer?.cancel()
    }

    private fun scheduleVolumeBarHide() {
        volumeHideTimer?.cancel()
        volumeHideTimer = Timer()
        volumeHideTimer?.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    binding.volumeBarLayout.visibility = View.GONE
                }
            }
        }, 1500)
    }

    override fun onPause() {
        epgTimer?.cancel()
        epgTimer = null
        player?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
        scheduleEpgMidnightUpdate()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun loadChannelFile() {    // при старте грузим полный список каналов в 4 списка - chNumber titleCh tvgLogo groupTitle
        val listFile: List<String>
        val filepath: File? = filesDir
        val file = File(filepath, localChannelListFileName)
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
            listFile = file.bufferedReader().useLines { it.toList() }
            for (i in listFile) {
                groupTitleBool = true
                if (!extinfBool) {
                    if (i.startsWith("#EXTINF:")) {
                        extinfBool = true  // защита от повтора #EXTINF:-строки
                        chCount++
                        chNumber.add(chCount)
                        // если имя группы в строке #EXTINF:, то группу берем здесь
                        if ("group-title=" in i) {
                            groupTitleTemp = parseUrlString(i, "group-title=\"")
                            groupTitleBool = false // защита от повтора #EXTGRP:-строки
                        }
                        tvgLogo.add(parseUrlString(i, "tvg-logo=\""))
                        tvgId.add(parseUrlString(i, "tvg-id=\""))
                        titleCh.add(i.substringAfterLast(','))
                    }
                }
                // а если имя группы в строке #EXTGRP:, то группа идет отсюда
                if (groupTitleBool) {
                    if (i.startsWith("#EXTGRP:")) {
                        groupTitleTemp = i.substringAfter("#EXTGRP:")
                    }
                    if (groupTitleTemp.length==0) groupTitleTemp="Общий"
                }
                if (!i.startsWith("#") && extinfBool) { // когда приходит урл, окончательная запись в массив
                    urlCh.add(i)
                    groupTitle.add(groupTitleTemp)
                    groupTitleTemp = ""
                    extinfBool = false // разрешаем чтение #EXTINF:-строки
                }
            }
            urlCount = titleCh.size
           // Log.d("mylog", "")
            Toast.makeText(this, "Каналов в плейлисте - $urlCount ", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadFavoritesFile() {    // при старте грузим список фаворитных каналов
        val listFile: List<String>
        val filepath: File? = filesDir
        val file = File(filepath, favListFileName)
        favArray.clear()
        try {
            listFile = file.bufferedReader().useLines { it.toList() }
            for (i in listFile) {
                favArray.add(i.toBoolean())
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadGroupFile() {       // при старте грузим в groupsArr все группы из файла
        groupsArr.clear()
        groupsArr.add("ВСЕ ГРУППЫ")
        val listFile: List<String>
        val filepath: File? = filesDir
        val file = File(filepath, groupListFileName)
        try {
            listFile = file.bufferedReader().useLines { it.toList() }
            for (i in listFile) {
                groupsArr.add(i)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadEpgFile() {       // парсим EPG файл в HashMap'ы для быстрого поиска
        epgChannelMap.clear()
        epgProgramMap.clear()
        val filepath: File? = filesDir
        val file = File(filepath, "epg.txt")
        if (!file.exists()) return

        val epgTimeFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.getDefault())

        try {
            file.bufferedReader().useLines { lines ->
                var currentChannelId = ""
                var inProgramme = false
                var progChannelId = ""
                var progStartMillis = 0L
                var progEndMillis = 0L

                for (line in lines) {
                    val trimmed = line.trim()

                    // Парсим <channel id="...">
                    if (trimmed.startsWith("<channel id=\"")) {
                        currentChannelId = trimmed.substringAfter("<channel id=\"").substringBefore("\"")
                    }
                    // Парсим <display-name ...>ИМЯ</display-name>
                    else if (trimmed.startsWith("<display-name") && currentChannelId.isNotEmpty()) {
                        val displayName = trimmed.substringAfter(">").substringBefore("<")
                        if (displayName.isNotEmpty()) {
                            epgChannelMap[displayName.lowercase()] = currentChannelId
                        }
                    }
                    // Закрытие </channel>
                    else if (trimmed == "</channel>") {
                        currentChannelId = ""
                    }
                    // Парсим <programme start="..." stop="..." channel="...">
                    else if (trimmed.startsWith("<programme start=\"")) {
                        val startStr = trimmed.substringAfter("start=\"").substringBefore("\"")
                        val endStr = trimmed.substringAfter("stop=\"").substringBefore("\"")
                        progChannelId = trimmed.substringAfter("channel=\"").substringBefore("\"")

                        // Парсим время с учётом часового пояса из самого EPG
                        val startParsed = epgTimeFormat.parse(startStr)
                        val endParsed = epgTimeFormat.parse(endStr)
                        if (startParsed != null && endParsed != null) {
                            progStartMillis = startParsed.time
                            progEndMillis = endParsed.time
                            inProgramme = true
                        }
                    }
                    // Парсим <title ...>НАЗВАНИЕ</title>
                    else if (inProgramme && trimmed.startsWith("<title")) {
                        val title = trimmed.substringAfter(">").substringBefore("</title>")
                        val program = EpgProgram(progStartMillis, progEndMillis, title)
                        epgProgramMap.getOrPut(progChannelId) { mutableListOf() }.add(program)
                        inProgramme = false
                    }
                    // Закрытие </programme> без title
                    else if (trimmed == "</programme>") {
                        inProgramme = false
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fillChannelListFromArrays(grNum: Int) {  // формируем список каналов текущей группы grNum (или все)
        val filter = groupsArr[grNum]
        channelList.clear()
        //Log.d("mylog", "urlCh.size = ${urlCh.size}")

        for (i in urlCh.indices) {
            if (grNum == 0) { // для ВСЕ ГРУППЫ
                channelList.add(
                    MyData(
                        chNumber[i],
                        titleCh[i],
                        urlCh[i],
                        tvgLogo[i],
                        groupTitle[i],
                        tvgId[i],
                        favArray[i]
                    )
                )
            } else {  // для выбранной группы
                if (groupTitle[i] == filter) {
                    channelList.add(
                        MyData(
                            chNumber[i],
                            titleCh[i],
                            urlCh[i],
                            tvgLogo[i],
                            groupTitle[i],
                            tvgId[i],
                            favArray[i]
                        )
                    )
                }
            }
        }
//             Log.d("mylog", "chNumber.size = ${chNumber.size}")
//             Log.d("mylog", "titleCh.size = ${titleCh.size}")
//             Log.d("mylog", "urlCh.size = ${urlCh.size}")
//             Log.d("mylog", "tvgLogo.size = ${tvgLogo.size}")
//             Log.d("mylog", "filter = $filter")
        // Log.d("mylog", "cc = $cc")

       // Log.d("mylog", "${channelList.size}   channelList = $channelList")
        // Log.d("mylog", "${channelList[1774]}")
        // Log.d("mylog", "${channelList[1775]}")
        adapter = MyAdapter(channelList, currentChNum, null) { position ->
            toggleFavorite(position) // Обработка нажатия на "Избранное"
        }
        adapter.notifyDataSetChanged()
        binding.rvChannelListView.adapter = adapter

        adapter.setOnKotlinItemClickListener(object : MyAdapter.OnChannelClickListener {
            override fun onUrlClick(position: Int) {
                //Log.d("mylog", " oldChannel = $oldChannel  ***  newNum = ${channelList[position].numData}")
                if (oldChannel != (channelList[position].numData)) {  // перезапускаем плеер только если другой канал
                    currentChNum = position
                    showInfo = false
                    infoShow(10000.0)
                    oldChannel = channelList[position].numData
                    setupVideoView(channelList[position].numData - 1)
                }
                showList = !showList
                binding.rvChannelListView.visibility = View.INVISIBLE
            }
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fillFavlListFromArrays() {  // формируем список фаворитных каналов
        channelList.clear()
        for (i in favArray.indices) {
            if (favArray[i]) { //
                channelList.add(
                    MyData(
                        chNumber[i],
                        titleCh[i],
                        urlCh[i],
                        tvgLogo[i],
                        groupTitle[i],
                        tvgId[i],
                        favArray[i]
                    )
                )
            }
        }
        adapter = MyAdapter(channelList, currentChNum, null) { position ->
            toggleFavorite(position) // Обработка нажатия на "Избранное"
        }
        adapter.notifyDataSetChanged()
        binding.rvChannelListView.adapter = adapter
        adapter.setOnKotlinItemClickListener(object : MyAdapter.OnChannelClickListener {
            override fun onUrlClick(position: Int) {
                if (currentChNum != position) {  // перезапускаем плеер только если другой канал
                    currentChNum = position
                    showInfo = false
                    infoShow(10000.0)
                    setupVideoView(channelList[position].numData - 1)
                }
                showList = !showList
                binding.rvChannelListView.visibility = View.INVISIBLE
            }
        })
    }

    // Переключение состояния "Избранное"
    private fun toggleFavorite(position: Int) {
        channelList[position].isFav = !channelList[position].isFav  // задаем значение фав каналу
        favArray[channelList[position].numData - 1] = !favArray[channelList[position].numData - 1]
        adapter.notifyItemChanged(position)     // обновление сердечек в списке
        saveFavList()                           // и сохранение списка Избранных в файл
    }

    private fun fillGroupListFromLocalFile() {
        //Log.d("myLog", "sdfsfdsff")
        groupList.clear()
        var groupColor: String
        for (i in groupsArr.indices) {
            groupColor = if (currentGrNum == i) "#F0F000" else "#FFFFFF"
            //Log.d("mylog", "groupTitle - ${groupsArr[i]}")
            groupList.add(GroupData(groupsArr[i], groupColor))
        }
        groupAdapter = GroupAdapter(groupList, currentGrNum, null)
        binding.rvGroupListView.adapter = groupAdapter
        groupAdapter.setOnKotlinItemClickListener(object :
            GroupAdapter.OnGroupClickListener { // слушаем клик в выборе группы
            override fun onGroupClick(position: Int) {
                binding.rvChannelListView.visibility =
                    View.INVISIBLE        // прячем список каналов
                currentGrNum = position                 // устанавливаем текущий номер группы
                showGroup = false
                // Log.d("myLog", " ${}")
                groupListShow(showGroup)
                fillChannelListFromArrays(currentGrNum)
                showList = true
                channelListShow(true)
                isFav = false       //  если выбрана группа, отключаем Избранное
                setFavIcon(isFav)
            }
        })
    }

    private fun parseUrlString(infStr: String, subStr: String): String {
        //  Log.d("mylog", "$infStr $subStr")
        var result = ""
        if (subStr in infStr) {
            val s = infStr.substringAfter(subStr)
            //Log.d("mylog", "s = $s"  )
            result = s.substringBefore('"')
        }
        return result
    }

    private fun getSettings() {
        val un = pref.getInt("currentChNum", 0)
        currentChNum = un
        val gn = pref.getInt("currentGrNum", 0)
        currentGrNum = gn
        val zm = pref.getString("zoomMode", "0")
        if (zm != null) {
            if (zm.isNotEmpty()) zoomMode = zm.toInt()
        }
        val ed = pref.getString("epgDate", "26.03.2025")
        if (ed != null) {
            if (ed.isNotEmpty()) epgDate = ed
        }
        val fav = pref.getString("isFav", "false")
        if (fav != null) {
            if (fav.isNotEmpty()) isFav = fav.toBoolean()
        }
    }

    private fun saveSettings(un: Int, gn: Int, zm: Int, epgDate: String, isFav: Boolean) {
        pref.edit {
            putInt("currentGrNum", gn)
            putInt("currentChNum", un)
            putString("zoomMode", zm.toString())
            putString("epgDate", epgDate)
            putString("isFav", isFav.toString())
        }
    }

    private fun saveFavList() {
        try {
            val bw =
                BufferedWriter(OutputStreamWriter(openFileOutput(favListFileName, MODE_PRIVATE)))
            for (i in favArray.indices) {
                bw.write("${favArray[i]}\n")
            }
            bw.close()
            //Toast.makeText(this, "Перезагрузите приложение.", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun openPlaylistFromDownloads() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.android.externalstorage.documents/document/primary:Download")
                )
            }
        }
        openPlaylistLauncher.launch(intent)
    }

    private fun writePlaylistFromText(txt: String) {  // сохраняем скаченный плейлист во внутренней памяти
        try {
            val bw = BufferedWriter(OutputStreamWriter(openFileOutput("playlist.m3u", MODE_PRIVATE)))
            bw.write(txt)
            bw.close()

            val playlistHandler = PlaylistHandler(this)
            // Извлекаем группы и сохраняем их в файл "group.txt"
            playlistHandler.extractGroupsFromPlaylist("playlist.m3u")
            // Создаем файл "isFavorite.txt"
            playlistHandler.createIsFavoriteFile("playlist.m3u")
            loadChannelFile()
            loadGroupFile()
            loadFavoritesFile()
            currentGrNum = 0
            currentChNum = 0
            isFav = false
            setFavIcon(isFav)
            fillChannelListFromArrays(currentGrNum)
            if (channelList.isNotEmpty()) {
                setupVideoView(channelList[currentChNum].numData - 1)
            } else if (urlCh.isNotEmpty()) {
                setupVideoView(0)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onUrlClick(position: Int) {
        Toast.makeText(applicationContext, urlCh[position], Toast.LENGTH_SHORT).show()
    }

    override fun onGroupClick(position: Int) {
        // TODO("Not yet implemented")
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Сохранение состояния при повороте
        outState.putInt("currentChNum", currentChNum)
        outState.putInt("currentGrNum", currentGrNum)
        outState.putInt("zoomMode", zoomMode)
        outState.putBoolean("isFav", isFav)
        //Log.d("mylog", "Save = $currentChNum  $currentGrNum  $zoomMode")
    }

}
