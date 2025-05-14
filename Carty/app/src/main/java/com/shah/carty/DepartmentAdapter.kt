package com.shah.carty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class DepartmentAdapter(
    private val onItemClicked: (Department) -> Unit,
    private val onItemLongClicked: (Department) -> Unit
) : ListAdapter<Department, DepartmentAdapter.DepartmentViewHolder>(DepartmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DepartmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_department, parent, false)
        return DepartmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DepartmentViewHolder, position: Int) {
        val department = getItem(position)
        holder.bind(department)
        holder.itemView.setOnClickListener {
            onItemClicked(department)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClicked(department)
            true
        }
    }

    class DepartmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val departmentNameTextView: TextView = itemView.findViewById(R.id.departmentNameTV)

        fun bind(department: Department) {
            departmentNameTextView.text = department.departmentName
        }
    }
}

class DepartmentDiffCallback : DiffUtil.ItemCallback<Department>() {
    override fun areItemsTheSame(oldItem: Department, newItem: Department): Boolean {
        return oldItem.departmentId == newItem.departmentId
    }

    override fun areContentsTheSame(oldItem: Department, newItem: Department): Boolean {
        return oldItem == newItem
    }
}