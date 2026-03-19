package com.example.myexo1.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<GroupGridAdapter.ViewHolder>() {

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
            holder.tvName.setTextColor(Color.parseColor("#FAFD9A"))
            holder.tvCount.text = ""
        } else if (item.isSearch) {
            holder.tvName.setTextColor(Color.parseColor("#9AFACC"))
            holder.tvCount.text = item.count.toString()
        } else {
            holder.tvName.setTextColor(Color.WHITE)
            holder.tvCount.text = item.count.toString()
        }
        holder.itemView.setOnClickListener { onClick(position) }
        holder.itemView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.setBackgroundColor(Color.parseColor("#2c3741"))
            } else {
                view.setBackgroundColor(Color.parseColor("#1c2731"))
            }
        }
    }
}
