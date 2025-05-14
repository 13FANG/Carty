package com.shah.carty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemDepartmentBinding

class DepartmentAdapter(
    private val onItemClicked: (Department) -> Unit,
    private val onDeleteButtonClicked: (Department) -> Unit
) : ListAdapter<Department, DepartmentAdapter.DepartmentViewHolder>(DepartmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DepartmentViewHolder {
        val binding = ItemDepartmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DepartmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DepartmentViewHolder, position: Int) {
        val department = getItem(position)
        holder.bind(department, onItemClicked, onDeleteButtonClicked)
    }

    class DepartmentViewHolder(private val binding: ItemDepartmentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            department: Department,
            onItemClickedCallback: (Department) -> Unit,
            onDeleteButtonClickedCallback: (Department) -> Unit
        ) {
            binding.departmentNameTV.text = department.departmentName

            itemView.setOnClickListener {
                onItemClickedCallback(department)
            }

            binding.delItemProdictImageButton.setOnClickListener {
                onDeleteButtonClickedCallback(department)
            }
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