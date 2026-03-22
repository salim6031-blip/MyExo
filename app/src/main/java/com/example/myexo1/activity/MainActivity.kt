package com.example.myexo1.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.SeekBar
import androidx.core.net.toUri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.data.MyData
import com.example.myexo1.R
import com.example.myexo1.adapter.EpgInfo
import com.example.myexo1.adapter.MyAdapter
import com.example.myexo1.databinding.ActivityMainBinding
import com.example.myexo1.utils.DataRepository
import com.example.myexo1.utils.PlaylistHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

@Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var player: ExoPlayer? = null
    private var channelList = ArrayList<MyData>()
    private val repo = DataRepository
    private var currentChNum = 0
    private var currentGrNum = 0
    private var showInfo = false
    private var showList = false
    private var isFav = false
    private var isSearch = false
    private var showStatusBar = false
    private var showButtons = false
    private var timer = Timer()
    private var timer2 = Timer()  //таймер для кнопок
    private var epgTimer: Timer? = null  // таймер проверки смены суток для EPG
    private var urlString = "https://igi-hls.cdnvideo.ru/igi/igi_tcode/playlist.m3u8"
    private lateinit var pref: SharedPreferences
    private lateinit var adapter: MyAdapter
    private lateinit var audioManager: AudioManager
    private var zoomMode = 0
    private var lastButton = 0
    private var oldChannel = 0
    private var currentIsMovie = false
    private var seekBarTracking = false
    private var epgDate = "21.03.2026"
    private var epgProgress = 0
    private var epgMax = 100
    private lateinit var openPlaylistLauncher: ActivityResultLauncher<Intent>
    private lateinit var channelListLauncher: ActivityResultLauncher<Intent>
    private var volumeSwipeActive = false
    private var volumeSwipeMoved = false
    private var lastVolumeSwipeY = 0f
    private val volumeSwipeRegionRatio = 0.9f
    private val volumeSwipeSensitivity = 300f  // dp полного свайпа от 0% до 100%
    private var playerVolume = 1f
    private var volumeHideTimer: Timer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

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
            launchChannelListActivity()
        }

        binding.groupButton.setOnClickListener {
            lastButton = 3
            showButtons = false
            canvasClickEvent(showButtons)
            val intent = Intent(this, GroupListActivity::class.java)
            intent.putExtra("fromPlayer", true)
            startActivity(intent)
        }

        binding.favButton.setOnClickListener {
            lastButton = 4
            isSearch = false
            isFav = !isFav
            setFavIcon(isFav)
            launchChannelListActivity()
        }

        binding.zoomButton.setOnClickListener {
            lastButton = 6
            zoomMode++
            if (zoomMode > 2) zoomMode = 0
            setZoomMode(zoomMode)
        }

        binding.stopButton.setOnClickListener { stopShow() }

        binding.infoStarIcon.setOnClickListener {
            toggleFavorite(currentChNum)
            showInfo = false
            infoShow(10000.0)
        }

        binding.epgProgressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && currentIsMovie) {
                    val posMs = progress * 1000L
                    binding.epgText.text = getString(R.string.playback_position, formatDuration(posMs), formatDuration(player?.duration ?: 0L))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                seekBarTracking = true
                timer.cancel() // не прячем info_card во время перемотки
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                seekBarTracking = false
                if (currentIsMovie) {
                    player?.seekTo(seekBar.progress * 1000L)
                }
                timerInfoHide(10000.0)
            }
        })

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

        channelListLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val data = result.data!!
                    val selectedChNum = data.getIntExtra("selectedChNum", -1)
                    currentGrNum = data.getIntExtra("currentGrNum", currentGrNum)
                    isFav = data.getBooleanExtra("isFav", isFav)
                    isSearch = data.getBooleanExtra("isSearch", false)
                    val newPosition = data.getIntExtra("currentChNum", currentChNum)
                    setFavIcon(isFav)
                    repo.reloadFavorites(this@MainActivity)
                    fillCurrentList()
                    if (selectedChNum >= 0 && selectedChNum < repo.urlCh.size) {
                        currentChNum = newPosition
                        oldChannel = selectedChNum + 1
                        adapter.updateArgument(currentChNum)
                        setupVideoView(selectedChNum)
                    }
                } else {
                    repo.reloadFavorites(this@MainActivity)
                    fillCurrentList()
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
//        binding.emptyCanvas.setOnLongClickListener {
//            showButtons = !showButtons
//            showStatusBar = true
//            canvasClickEvent(showButtons)
//            if (!showButtons) {
//                showInfo = true
//                infoShow(10.0)
//            }
//            return@setOnLongClickListener true
//        }
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
            isSearch = savedInstanceState.getBoolean("isSearch", false)
        }

        // Если запущен из GroupListActivity/ChannelListActivity с конкретным каналом
        if (intent.hasExtra("selectedChNum")) {
            currentGrNum = intent.getIntExtra("currentGrNum", currentGrNum)
            currentChNum = intent.getIntExtra("currentChNum", currentChNum)
            isFav = intent.getBooleanExtra("isFav", isFav)
            isSearch = intent.getBooleanExtra("isSearch", false)
        }

        checkFirstRun()
        updateEpg()
        scheduleEpgMidnightUpdate()

        // Инициализация AudioManager
        audioManager = getSystemService(AudioManager::class.java)

    }

    @SuppressLint("SimpleDateFormat")
    private fun updateEpg() {
        if (epgDate != SimpleDateFormat("dd.MM.yyyy").format(Date())) {
            lifecycleScope.launch {
                val epgHandler = PlaylistHandler(this@MainActivity)
                val epgUrls = listOf(
                    "https://epg.one/epg.xml",
                    "https://epg.one/epg.xml",
                    "https://epg.iptvx.one/EPG.xml.gz"
                )
                withContext(Dispatchers.IO) {
                    var downloaded = false
                    for (url in epgUrls) {
                        if (epgHandler.downloadEpg(url)) {
                            downloaded = true
                            break
                        }
                    }
                    if (downloaded) {
                        repo.reloadEpg(this@MainActivity)
                    } else {
                        android.util.Log.w("MainActivity", "EPG: не удалось загрузить ни из одного источника")
                        // Пробуем загрузить кеш, если он есть
                        repo.loadEpg(this@MainActivity)
                    }
                }
                epgDate = SimpleDateFormat("dd.MM.yyyy").format(Date())
            }
        } else {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repo.loadEpg(this@MainActivity) }
            }
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
        saveSettings(currentChNum, currentGrNum, zoomMode, epgDate, isFav)
        super.onBackPressed()
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
            binding.favButton.setImageResource(R.drawable.baseline_star)
        } else {
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

    private fun fillCurrentList() {
        if (isSearch) {
            fillSearchListFromArrays()
        } else if (isFav) {
            fillFavListFromArrays()
        } else {
            fillChannelListFromArrays(currentGrNum)
        }
    }

    private fun checkFirstRun() {
        try {
            if (repo.playlistLoaded) {
                // Данные уже загружены в DataRepository
                fillCurrentList()
                setFavIcon(isFav)
                if (channelList.isEmpty()) return
                if (currentChNum >= channelList.size) currentChNum = 0
                val selectedFromIntent = intent.getIntExtra("selectedChNum", -1)
                if (selectedFromIntent >= 0 && selectedFromIntent < repo.urlCh.size) {
                    setupVideoView(selectedFromIntent)
                } else {
                    setupVideoView(channelList[currentChNum].numData - 1)
                }
            } else if (DataRepository.playlistExists(this)) {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repo.loadAll(this@MainActivity) }
                    fillCurrentList()
                    setFavIcon(isFav)
                    if (channelList.isEmpty()) return@launch
                    if (currentChNum >= channelList.size) currentChNum = 0
                    val selectedFromIntent = intent.getIntExtra("selectedChNum", -1)
                    if (selectedFromIntent >= 0 && selectedFromIntent < repo.urlCh.size) {
                        setupVideoView(selectedFromIntent)
                    } else {
                        setupVideoView(channelList[currentChNum].numData - 1)
                    }
                    // EPG в фоне
                    withContext(Dispatchers.IO) { repo.loadEpg(this@MainActivity) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        if (player != null) return
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,   // minBufferMs
                5000,   // maxBufferMs
                500,    // bufferForPlaybackMs
                1000    // bufferForPlaybackAfterRebufferMs
            )
            .build()
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .build().apply {
                playWhenReady = true
            }
        binding.videoView.player = player
        binding.videoView.keepScreenOn = true
        player?.volume = playerVolume
        applyLoudnessNorm()
    }

    @SuppressLint("NewApi")
    @OptIn(UnstableApi::class)
    private fun applyLoudnessNorm() {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        dynamicsProcessing?.release()
        dynamicsProcessing = null
        val enabled = pref.getBoolean("loudnessNorm", true)
        if (!enabled) return
        val sessionId = player?.audioSessionId ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    1, false, 0, false, 0, false, 0, true
                ).build()
                dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply {
                    setInputGainAllChannelsTo(10f) // +10 dB усиление тихих каналов
                    val limiter = DynamicsProcessing.Limiter(
                        true, true, 0,
                        1f,     // attackTime мс
                        100f,   // releaseTime мс
                        10f,    // ratio
                        -2f,    // threshold дБ
                        0f      // postGain дБ
                    )
                    setLimiterAllChannelsTo(limiter)
                    this.enabled = true
                }
                return
            } catch (_: Exception) { }
        }
        // Fallback для API < 28: только усиление
        loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
            setTargetGain(1000)
            this.enabled = true
        }
    }

    private fun setupVideoView(un: Int, infoTimeMs: Double = 10000.0) {
        setZoomMode(zoomMode)
        urlString = repo.urlCh[un]
        initPlayer()
        player?.setMediaItem(MediaItem.fromUri(urlString))
        player?.prepare()
        showInfo = false
        infoShow(infoTimeMs)
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
        if (channelList.isEmpty()) return
        currentChNum++
        if (currentChNum > channelList.size - 1) currentChNum = 0
        adapter.updateArgument(currentChNum)  //  меняем текущую позицию в списке каналов
        binding.rvChannelListView.postDelayed({
            binding.rvChannelListView.smoothScrollToPosition(currentChNum)  // сдвигаем курсор на текущий канал из списка
        }, 100)
        showStatusBar = false
        setupVideoView(channelList[currentChNum].numData - 1, 3000.0)
    }

    private fun prevUrlStart() {
        if (channelList.isEmpty()) return
        if (currentChNum > 0) {
            currentChNum--
            adapter.updateArgument(currentChNum)  //  меняем текущую позицию в списке каналов
            binding.rvChannelListView.postDelayed({
                binding.rvChannelListView.smoothScrollToPosition(currentChNum)
            }, 100)
            showStatusBar = false
            setupVideoView(channelList[currentChNum].numData - 1, 3000.0)
        }
    }

    private fun launchChannelListActivity() {
        showButtons = false
        canvasClickEvent(showButtons)
        val intent = Intent(this, ChannelListActivity::class.java).apply {
            putExtra("currentChNum", currentChNum)
            putExtra("currentGrNum", currentGrNum)
            putExtra("isFav", isFav)
        }
        channelListLauncher.launch(intent)
    }

    private fun infoShow(timeMs: Double) {
        showInfo = !showInfo
        with(binding) {
            if (showInfo) {
                if (channelList.isEmpty() || currentChNum >= channelList.size) {
                    showInfo = false
                    return
                }
                infoNumData.text = (channelList[currentChNum].numData).toString()
                infoTitleData.text = channelList[currentChNum].titleData
                infoGroupData.text = getString(R.string.group_title_format, channelList[currentChNum].groupTitle)
                channelCountTv.text = getString(R.string.total_channels, repo.urlCh.size)
                groupCountTv.text = getString(R.string.group_count, channelList.size, currentChNum + 1)
                var media = repo.tvgLogo[channelList[currentChNum].numData - 1]
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

                val url = channelList[currentChNum].urlData
                currentIsMovie = isMovieUrl(url)
                if (currentIsMovie) {
                    // Для фильмов: интерактивный SeekBar с thumb
                    binding.epgProgressBar.thumb = ResourcesCompat.getDrawable(
                        resources, R.drawable.seekbar_thumb, theme
                    )
                    binding.epgProgressBar.splitTrack = false
                    val duration = player?.duration ?: 0L
                    val position = player?.currentPosition ?: 0L
                    if (duration > 0) {
                        binding.epgProgressBar.max = (duration / 1000).toInt()
                        binding.epgProgressBar.progress = (position / 1000).toInt()
                        binding.epgText.text = getString(R.string.playback_position, formatDuration(position), formatDuration(duration))
                    } else {
                        binding.epgProgressBar.max = 0
                        binding.epgProgressBar.progress = 0
                        binding.epgText.text = ""
                    }
                } else {
                    // Для каналов: только индикатор EPG, без thumb
                    binding.epgProgressBar.thumb = null
                    // EPG: сначала tvg-id, потом fallback по имени
                    var epgId = channelList[currentChNum].tvgId
                    if (epgId.isEmpty() || !repo.epgProgramMap.containsKey(epgId)) {
                        epgId = getChannelId(channelList[currentChNum].titleData)
                    }
                    if (epgId.isNotEmpty()) {
                        binding.epgText.text = getIdEpg(epgId)
                        binding.epgProgressBar.max = epgMax
                        binding.epgProgressBar.progress = epgProgress
                    } else {
                        binding.epgText.text = ""
                        binding.epgProgressBar.max = 0
                        binding.epgProgressBar.progress = 0
                    }
                }


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
        return repo.findChannelIdPublic(chTitle) ?: ""
    }

    private fun getIdEpg(id: String): String {
        epgMax = 0
        epgProgress = 0
        val programs = repo.epgProgramMap[id] ?: return ""
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

    private fun getEpgInfoForChannel(channelTitle: String): EpgInfo? {
        return repo.getEpgInfoForChannel(channelTitle)
    }

    private fun isMovieUrl(url: String): Boolean {
        val lower = url.lowercase()
        // Убираем query-параметры для проверки расширения
        val path = lower.substringBefore('?').substringBefore('#')
        return path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") ||
                path.endsWith(".mov") || path.endsWith(".wmv") || path.endsWith(".flv") ||
                path.endsWith(".webm") || path.endsWith(".ts") || path.endsWith(".vob")
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    private fun stopShow() {
        saveSettings(currentChNum, currentGrNum, zoomMode, epgDate, isFav)
        finishAffinity()
    }

    private fun handleVolumeSwipe(view: View, event: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                volumeSwipeActive = event.x >= view.width * volumeSwipeRegionRatio
                volumeSwipeMoved = false
                lastVolumeSwipeY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!volumeSwipeActive) return false
                val dyPx = lastVolumeSwipeY - event.y
                lastVolumeSwipeY = event.y
                val dyDp = dyPx / density
                val delta = dyDp / volumeSwipeSensitivity  // доля от полного диапазона
                if (kotlin.math.abs(delta) > 0.001f) {
                    volumeSwipeMoved = true
                    val current = player?.volume ?: 1f
                    player?.volume = (current + delta).coerceIn(0f, 1f)
                    showVolumeBar()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasMoved = volumeSwipeMoved
                volumeSwipeActive = false
                volumeSwipeMoved = false
                if (wasMoved) {
                    scheduleVolumeBarHide()
                    return true
                }
                return false
            }
        }
        return false
    }

    private fun showVolumeBar() {
        val volume = player?.volume ?: 1f
        val percent = (volume * 100).toInt()
        binding.volumeProgressBar.max = 100
        binding.volumeProgressBar.progress = percent
        binding.volumePercentText.text = getString(R.string.volume_percent, percent)
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
        saveSettings(currentChNum, currentGrNum, zoomMode, epgDate, isFav)
        epgTimer?.cancel()
        epgTimer = null
        player?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
        applyLoudnessNorm()
        scheduleEpgMidnightUpdate()
    }

    override fun onDestroy() {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        dynamicsProcessing?.release()
        dynamicsProcessing = null
        player?.release()
        player = null
        super.onDestroy()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fillListFromData(data: ArrayList<MyData>) {
        channelList = data
        adapter = MyAdapter(channelList, currentChNum, null, { title ->
            getEpgInfoForChannel(title)
        }, { position ->
            toggleFavorite(position)
        })
        adapter.notifyDataSetChanged()
        binding.rvChannelListView.adapter = adapter
        adapter.setOnKotlinItemClickListener(object : MyAdapter.OnChannelClickListener {
            override fun onUrlClick(position: Int) {
                if (oldChannel != channelList[position].numData) {
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

    private fun fillChannelListFromArrays(grNum: Int) {
        fillListFromData(repo.buildChannelList(grNum))
    }

    private fun fillFavListFromArrays() {
        fillListFromData(repo.buildFavList())
    }

    private fun fillSearchListFromArrays() {
        fillListFromData(repo.buildSearchList())
    }

    private fun toggleFavorite(position: Int) {
        channelList[position].isFav = !channelList[position].isFav
        repo.favArray[channelList[position].numData - 1] = !repo.favArray[channelList[position].numData - 1]
        adapter.notifyItemChanged(position)
        repo.saveFavList(this)
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
        isSearch = pref.getBoolean("isSearch", false)
        playerVolume = pref.getFloat("playerVolume", 1f)
    }

    private fun saveSettings(un: Int, gn: Int, zm: Int, epgDate: String, isFav: Boolean) {
        pref.edit {
            putInt("currentGrNum", gn)
            putInt("currentChNum", un)
            putString("zoomMode", zm.toString())
            putString("epgDate", epgDate)
            putString("isFav", isFav.toString())
            putBoolean("isSearch", isSearch)
            putFloat("playerVolume", player?.volume ?: playerVolume)
        }
    }

    private fun openPlaylistFromDownloads() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    "content://com.android.externalstorage.documents/document/primary:Download".toUri()
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
            playlistHandler.createIsFavoriteFile("playlist.m3u")
            repo.reloadAll(this)
            currentGrNum = 0
            currentChNum = 0
            isFav = false
            setFavIcon(isFav)
            fillChannelListFromArrays(currentGrNum)
            if (channelList.isNotEmpty()) {
                setupVideoView(channelList[currentChNum].numData - 1)
            } else if (repo.urlCh.isNotEmpty()) {
                setupVideoView(0)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentChNum", currentChNum)
        outState.putInt("currentGrNum", currentGrNum)
        outState.putInt("zoomMode", zoomMode)
        outState.putBoolean("isFav", isFav)
        outState.putBoolean("isSearch", isSearch)
    }

}
