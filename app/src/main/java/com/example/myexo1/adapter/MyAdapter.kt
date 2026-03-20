package com.example.myexo1.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.data.MyData
import com.example.myexo1.R

data class EpgInfo(val title: String, val progress: Int, val max: Int)

class MyAdapter(
    private val userList: ArrayList<MyData>,
    private var selectedChannelPosition : Int,
    private var listener: OnChannelClickListener? = null,
    private val epgProvider: ((String) -> EpgInfo?)? = null,
    private val onFavoriteClick: (Int) -> Unit, // Колбэк для обработки нажатия
    private val layoutResId: Int = R.layout.item
) : RecyclerView.Adapter<MyAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return userList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val favoriteIcon: ImageView = itemView.findViewById(R.id.favoriteIcon)
        private val epgText: TextView = itemView.findViewById(R.id.epg_program_text)
        private val epgProgressBar: ProgressBar = itemView.findViewById(R.id.epg_progress_bar)

        fun bindItems(myData: MyData, isSelected: Boolean, epgInfo: EpgInfo?) {

            itemView.isSelected = isSelected
            itemView.setBackgroundResource(R.drawable.gradient_default)
            val channelImage: ImageView = itemView.findViewById(R.id.channelImage)
            var media = myData.tvgLogoData
            if (media.startsWith("//")) media = "https:$media"
            if (media.trim() != "") {
                Glide.with(channelImage)
                    .load(media)
                    .placeholder(R.drawable.smart_tv48)
                    .error(R.drawable.smart_tv48)
                    .into(channelImage)
            } else {
                Glide.with(channelImage).clear(channelImage)
                channelImage.setImageResource(R.drawable.smart_tv48)
            }
            val num = itemView.findViewById(R.id.num_data) as TextView
            num.text = myData.numData.toString()
            val title = itemView.findViewById(R.id.title_data) as TextView
            title.text = myData.titleData
            val group = itemView.findViewById(R.id.group_data) as TextView
            group.text = myData.groupTitle
            val url = itemView.findViewById(R.id.url_data) as TextView
            url.text = myData.urlData
            var favIcon = itemView.findViewById(R.id.favoriteIcon) as ImageView
            if (myData.isFav){   // значок фаворитных каналов
                favIcon.setImageResource(R.drawable.baseline_star)
            } else {
                favIcon.setImageResource(R.drawable.baseline_star_border)
            }

            // EPG данные
            if (epgInfo != null && epgInfo.title.isNotEmpty()) {
                epgText.text = epgInfo.title
                epgText.visibility = View.VISIBLE
                epgProgressBar.max = epgInfo.max
                epgProgressBar.progress = epgInfo.progress
                epgProgressBar.visibility = View.VISIBLE
            } else {
                epgText.text = "Программа отсутствует"
                epgText.visibility = View.VISIBLE
                epgProgressBar.visibility = View.GONE
            }

            // Обработка изменения фокуса
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    // Элемент в фокусе
                    view.setBackgroundResource(R.drawable.gradient_move_cursor)
                } else {
                    // Элемент не в фокусе
                    view.setBackgroundResource(R.drawable.gradient_default)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val epgInfo = epgProvider?.invoke(userList[position].titleData)
        holder.bindItems(userList[position], position == selectedChannelPosition, epgInfo)
        holder.itemView.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                selectedChannelPosition = pos
                listener?.onUrlClick(pos)
                notifyDataSetChanged()
            }
        }
         //Обработка нажатия на иконку "Избранное"
        holder.favoriteIcon.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onFavoriteClick(pos)
            }
        }
    }

    fun setOnKotlinItemClickListener(itemClickListener: OnChannelClickListener) {
        this.listener = itemClickListener
    }

    interface OnChannelClickListener {
        fun onUrlClick(position: Int)
    }
    // Метод для обновления дополнительного аргумента
    @SuppressLint("NotifyDataSetChanged")
    fun updateArgument(newArgument: Int) {
        selectedChannelPosition = newArgument
        notifyDataSetChanged() // Уведомляем адаптер об изменении аргумента
    }
}