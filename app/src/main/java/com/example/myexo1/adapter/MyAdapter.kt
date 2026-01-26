package com.example.myexo1.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.data.MyData
import com.example.myexo1.R

class MyAdapter(
    private val userList: ArrayList<MyData>,
    private var selectedChannelPosition : Int,
    private var listener: OnChannelClickListener? = null,
    private val onFavoriteClick: (Int) -> Unit // Колбэк для обработки нажатия
) : RecyclerView.Adapter<MyAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return userList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val favoriteIcon: ImageView = itemView.findViewById(R.id.favoriteIcon)
        fun bindItems(myData: MyData, isSelected: Boolean) {

            itemView.isSelected = isSelected
            itemView.setBackgroundResource(R.drawable.gradient_default)
            val channelImage: ImageView = itemView.findViewById(R.id.channelImage)
            var media = myData.tvgLogoData
            if (media.take(2) == "//") media = "https:$media"
            if (media.trim() !== "") {
                Glide.with(channelImage)
                    .load(media)
                    .placeholder(R.drawable.smart_tv48)
                    .into(channelImage)
            } else {
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
        holder.bindItems(userList[position], position == selectedChannelPosition)
        holder.itemView.setOnClickListener {
            selectedChannelPosition = holder.adapterPosition
            listener?.onUrlClick(position)
            notifyDataSetChanged()
        }
         //Обработка нажатия на иконку "Избранное"
        holder.favoriteIcon.setOnClickListener {
            onFavoriteClick(position) // Вызов колбэка
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