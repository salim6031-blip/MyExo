package com.example.myexo1.adapter

import android.annotation.SuppressLint
import android.graphics.Color
//import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.data.GroupData
import com.example.myexo1.R

class GroupAdapter(
    private val groupList: ArrayList<GroupData>,
    private var selectedGroupPosition : Int,
    private var listener: OnGroupClickListener? = null
) : RecyclerView.Adapter<GroupAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.group_item, parent, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return groupList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindItems(groupData: GroupData, isSelected: Boolean) {
            itemView.isSelected = isSelected

            val groupTv = itemView.findViewById(R.id.group_data) as TextView
            groupTv.text = groupData.groupData
            groupTv.setTextColor(Color.parseColor(groupData.color))
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    // Элемент в фокусе
                    view.setBackgroundResource(R.color.blue00cc)
                } else {
                    // Элемент не в фокусе
                    view.setBackgroundResource(R.color.raf_default)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindItems(groupList[position], position == selectedGroupPosition)
        holder.itemView.setOnClickListener {
            selectedGroupPosition = holder.adapterPosition
            listener?.onGroupClick(position)
            notifyDataSetChanged()
        }
    }

    fun setOnKotlinItemClickListener(itemClickListener: OnGroupClickListener) {
        this.listener = itemClickListener
    }

    interface OnGroupClickListener {
        fun onGroupClick(position: Int)
    }

//    // Метод для обновления дополнительного аргумента
//    @SuppressLint("NotifyDataSetChanged")
//    fun updateArgument(newArgument: Int) {
//        selectedGroupPosition = newArgument
//        notifyDataSetChanged() // Уведомляем адаптер об изменении аргумента
//    }
}