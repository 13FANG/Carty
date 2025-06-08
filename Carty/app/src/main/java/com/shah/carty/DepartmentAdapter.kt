package com.shah.carty

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemDepartmentBinding
import java.util.ArrayList
import java.util.Collections

class DepartmentAdapter(
    private val onItemClicked: (Department) -> Unit,
    private val onDeleteClicked: (Department) -> Unit,
    private val onOrderChanged: (List<Department>) -> Unit
) : ListAdapter<Department, DepartmentAdapter.DepartmentViewHolder>(DepartmentDiffCallback()),
    ItemTouchHelperAdapter {

    private var internalList: MutableList<Department> = mutableListOf()
    private var dragInProgress = false
    private var listSnapshotBeforeDrag: List<Department>? = null


    override fun getItemCount(): Int {
        return if (dragInProgress && internalList.isNotEmpty()) internalList.size else super.getItemCount()
    }

    override fun isItemDraggable(position: Int): Boolean {
        return position < itemCount
    }

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
        val listForBind = if (dragInProgress && internalList.isNotEmpty()) internalList else currentList
        if (position < 0 || position >= listForBind.size) return
        val department = listForBind[position]

        holder.bind(department, onDeleteClicked)
        holder.itemView.setOnClickListener {
            onItemClicked(department)
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!dragInProgress) {
            listSnapshotBeforeDrag = ArrayList(currentList)
            internalList.clear()
            internalList.addAll(listSnapshotBeforeDrag!!)
            dragInProgress = true
        }
        if (fromPosition < 0 || fromPosition >= internalList.size ||
            toPosition < 0 || toPosition >= internalList.size) {
            return false
        }
        if (fromPosition == toPosition) return true

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(internalList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(internalList, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onDragFinished() {
        if (dragInProgress) {
            onOrderChanged(ArrayList(internalList))
        }
        dragInProgress = false
        listSnapshotBeforeDrag = null
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