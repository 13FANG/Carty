package com.shah.carty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemDepartmentBinding
import java.util.ArrayList

class DepartmentAdapter(
    private val onItemClicked: (Department) -> Unit,
    private val onDeleteClicked: (Department) -> Unit,
    private val onOrderChanged: (List<Department>) -> Unit
) : ListAdapter<Department, DepartmentAdapter.DepartmentViewHolder>(DepartmentDiffCallback()),
    ItemTouchHelperAdapter {

    private var internalList: MutableList<Department> = mutableListOf()
    private var dragInProgress = false

    override fun submitList(list: List<Department>?) {
        val listToSubmit = list ?: emptyList()
        super.submitList(listToSubmit)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
        }
    }

    override fun submitList(list: List<Department>?, commitCallback: Runnable?) {
        val listToSubmit = list ?: emptyList()
        super.submitList(listToSubmit, commitCallback)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DepartmentViewHolder {
        val binding = ItemDepartmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DepartmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DepartmentViewHolder, position: Int) {
        val department: Department = if (dragInProgress && position < internalList.size) {
            internalList[position]
        } else if (position < super.getItemCount()){
            getItem(position)
        } else {
            if (internalList.isNotEmpty()) internalList[0] else Department(departmentName = "Error")
        }

        holder.bind(department, onDeleteClicked)
        holder.itemView.setOnClickListener {
            onItemClicked(department)
        }
    }

    override fun getItemCount(): Int {
        return if (dragInProgress) internalList.size else super.getItemCount()
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!dragInProgress) {
            internalList.clear()
            internalList.addAll(currentList)
            dragInProgress = true
        }
        if (fromPosition < 0 || fromPosition >= internalList.size || toPosition < 0 || toPosition >= internalList.size) {
            return false
        }
        if (fromPosition == toPosition) return true

        val item = internalList.removeAt(fromPosition)
        internalList.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onDragFinished() {
        if (dragInProgress) {
            onOrderChanged(ArrayList(internalList))
        }
        dragInProgress = false
    }

    override fun onItemDismiss(position: Int) {}

    class DepartmentViewHolder(private val binding: ItemDepartmentBinding) : RecyclerView.ViewHolder(binding.root),
        ItemTouchHelperViewHolder {
        fun bind(department: Department, onDeleteClicked: (Department) -> Unit) {
            binding.departmentNameTV.text = department.departmentName
            binding.delItemDepartmentImageButton.setOnClickListener {
                onDeleteClicked(department)
            }
        }

        override fun onItemSelected() {
            itemView.alpha = 0.7f
            itemView.elevation = 8f
        }

        override fun onItemClear() {
            itemView.alpha = 1.0f
            itemView.elevation = 0f
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