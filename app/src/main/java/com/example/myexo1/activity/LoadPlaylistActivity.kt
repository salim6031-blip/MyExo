package com.example.myexo1.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.myexo1.databinding.ActivityLoadplaylistBinding
import com.example.myexo1.utils.PlaylistHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter

@Suppress("DEPRECATION")
class LoadPlaylistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoadplaylistBinding

    //private lateinit var list: String
    private lateinit var urlStr: String
    private lateinit var text: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        val startForResult =  registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    val intent = result.data
                    val myFile = getContentResolver().openInputStream(intent?.data!!)
                    if (myFile != null) {
                        text = myFile.bufferedReader().readText()
                        writeFiles(text)
                    }
                }
            }
        binding = ActivityLoadplaylistBinding.inflate(layoutInflater)

        binding.loadButton.setOnClickListener {
            urlStr = binding.utlEditText.text.toString()
            load(urlStr)
        }

        binding.readLocalButton.setOnClickListener {
            clearStorageInBackground(this)

            val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT)
            // запускаем контракт
            startForResult.launch(intent)
        }

        binding.backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        setContentView(binding.root)
    }

    private fun load(urlStr: String) { // читаем плейлист по URL
        // Создаем экземпляр PlaylistHandler
        val playlistHandler = PlaylistHandler(this)
        // URL плейлиста
        val playlistUrl = urlStr // "http://rafail1982.uz/salim/800.m3u"
        // Загружаем плейлист
        val isSuccess = playlistHandler.downloadPlaylist(playlistUrl)
        if (isSuccess) {
            // Извлекаем группы и сохраняем их в файл "group.txt"
            playlistHandler.extractGroupsFromPlaylist("playlist.m3u")
            // Создаем файл "isFavorite.txt"
            val count = playlistHandler.createIsFavoriteFile("playlist.m3u")
            Toast.makeText(
                this,
                "Плейлист создан, всего каналов: $count. Перезапустите приложение.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun writeFiles( txt: String) {  // сохраняем скаченный плейлист во внутренней памяти
        try {
            val bw = BufferedWriter(OutputStreamWriter(openFileOutput("playlist.m3u", MODE_PRIVATE)))
            bw.write(txt)
            bw.close()

            val playlistHandler = PlaylistHandler(this)
            // Извлекаем группы и сохраняем их в файл "group.txt"
            playlistHandler.extractGroupsFromPlaylist("playlist.m3u")
            // Создаем файл "isFavorite.txt"
            val count = playlistHandler.createIsFavoriteFile("playlist.m3u")
            Toast.makeText(
                this,
                "Плейлист создан, всего каналов: $count. Перезапустите приложение.",
                Toast.LENGTH_SHORT
            ).show()


            //Toast.makeText(this, "Список каналов обновлен", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }


    fun clearStorageInBackground(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            clearAppStorage(context)

            // Вернуться в главный поток для UI обновлений
            withContext(Dispatchers.Main) {
                // Обновить UI
            }
        }
    }

    fun clearAppStorage(context: Context) {
        try {
            // Очистка кэша
            val cacheDir = context.cacheDir
            if (cacheDir.exists() && cacheDir.isDirectory) {
                deleteDirectoryContents(cacheDir)
            }

            // Очистка files
            val filesDir = context.filesDir
            if (filesDir.exists() && filesDir.isDirectory) {
                deleteDirectoryContents(filesDir)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Обработка ошибок
        }
    }

    private fun deleteDirectoryContents(directory: File) {
        val files = directory.listFiles()
        files?.forEach { file ->
            when {
                file.isDirectory -> deleteDirectoryContents(file)
                else -> {
                    try {
                        file.delete()
                    } catch (e: SecurityException) {
                        // Обработка ошибок безопасности
                        e.printStackTrace()
                    }
                }
            }
        }
    }

//    private fun setupBackPressHandler() {
//        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
//            override fun handleOnBackPressed() {
//                // Ваша логика перезапуска
//                restartAppFully()
//                isEnabled = false
//                finish()
//            }
//        })
//    }
//
//    private fun restartAppFully() {
//        val intent = Intent(this, MainActivity::class.java).apply {
//            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
//        }
//        startActivity(intent)
//    }
}