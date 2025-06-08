package com.shah.carty

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemProductBinding
import java.util.ArrayList
import java.util.Collections

class ProductAdapter(
    private val onItemClicked: (Product) -> Unit,
    private val onDeleteClicked: (Product) -> Unit,
    private val getDepartmentName: (Long?) -> String,
    private val onOrderChanged: (List<Product>) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(ProductDiffCallback()),
    ItemTouchHelperAdapter {

    private var internalList: MutableList<Product> = mutableListOf()
    private var dragInProgress = false
    private var listSnapshotBeforeDrag: List<Product>? = null


    override fun getItemCount(): Int {
        return if (dragInProgress && internalList.isNotEmpty()) internalList.size else super.getItemCount()
    }

    override fun isItemDraggable(position: Int): Boolean {
        return position < itemCount
    }

    override fun submitList(list: List<Product>?) {
        val listToSubmit = list ?: emptyList()
        super.submitList(listToSubmit)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
        }
    }

    override fun submitList(list: List<Product>?, commitCallback: Runnable?) {
        val listToSubmit = list ?: emptyList()
        super.submitList(listToSubmit, commitCallback)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val listForBind = if (dragInProgress && internalList.isNotEmpty()) internalList else currentList
        if (position < 0 || position >= listForBind.size) return
        val product = listForBind[position]


        holder.bind(product, getDepartmentName, onDeleteClicked)
        holder.itemView.setOnClickListener {
            onItemClicked(product)
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


    class ProductViewHolder(private val binding: ItemProductBinding) :
        RecyclerView.ViewHolder(binding.root), ItemTouchHelperViewHolder {
        fun bind(product: Product, getDepartmentName: (Long?) -> String, onDeleteClicked: (Product) -> Unit) {
            binding.productNameTV.text = product.productName
            binding.departmentNameTV.text = getDepartmentName(product.departmentId)
            binding.unitProductItemTV.text = product.defaultUnit.getDisplayName(itemView.context)
            binding.priceTV.text = product.defaultPrice?.toString() ?: "N/A"

            binding.delItemProductImageButton.setOnClickListener {
                onDeleteClicked(product)
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

class ProductDiffCallback : DiffUtil.ItemCallback<Product>() {
    override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
        return oldItem.productId == newItem.productId
    }

    override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
        return oldItem == newItem
    }
}