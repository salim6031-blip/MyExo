package com.example.myexo1.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.myexo1.databinding.ActivityLoadplaylistBinding
import com.example.myexo1.utils.PlaylistHandler
import java.io.BufferedWriter
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
                if (result.resultCode == Activity.RESULT_OK) {
                    val intent = result.data
                    val myFile = getContentResolver().openInputStream(intent?.data!!)
                    if (myFile != null) {
                        text = myFile.bufferedReader().readText()
                        writeFiles("playlist.m3u", text)
                    }
                }
            }
        binding = ActivityLoadplaylistBinding.inflate(layoutInflater)

        binding.loadButton.setOnClickListener {
            urlStr = binding.utlEditText.text.toString()
            load(urlStr)
        }

        binding.readLocalButton.setOnClickListener {
            val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT)
            // запускаем контракт
            startForResult.launch(intent)
        }

        binding.backButton.setOnClickListener {
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

    private fun writeFiles( filename: String,txt: String) {  // сохраняем скаченный плейлист во внутренней памяти
        try {
            val bw = BufferedWriter(OutputStreamWriter(openFileOutput(filename, MODE_PRIVATE)))
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
}