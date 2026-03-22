package com.example.myexo1.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.example.myexo1.R

data class GroupGridItem(
    val name: String,
    val count: Int,
    val isSpecial: Boolean = false, // для "Избранное"
    val isSearch: Boolean = false,  // для "Найденные"
    val groupIndex: Int = 0        // индекс в groupsArr
)

class GroupGridAdapter(
    private val items: List<GroupGridItem>,
    private val selectedPosition: Int = -1,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<GroupGridAdapter.ViewHolder>() {

    private val selectedBgColor = "#2c3741".toColorInt()
    private val defaultBgColor = "#1c2731".toColorInt()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_group_name)
        val tvCount: TextView = itemView.findViewById(R.id.tv_group_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_group_grid, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        if (item.isSpecial) {
            holder.tvName.setTextColor("#FAFD9A".toColorInt())
            holder.tvCount.text = item.count.toString()
        } else if (item.isSearch) {
            holder.tvName.setTextColor("#9AFACC".toColorInt())
            holder.tvCount.text = item.count.toString()
        } else {
            holder.tvName.setTextColor(Color.WHITE)
            holder.tvCount.text = item.count.toString()
        }
        val isSelected = position == selectedPosition
        holder.itemView.setBackgroundColor(if (isSelected) selectedBgColor else defaultBgColor)
        holder.itemView.setOnClickListener { onClick(position) }
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus || position == selectedPosition) {
                view.setBackgroundColor(selectedBgColor)
            } else {
                view.setBackgroundColor(defaultBgColor)
            }
        }
    }
}
