package com.shah.carty

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemProductBinding
import java.util.Collections

class ProductAdapter(
    private val onItemClicked: (Product) -> Unit,
    private val onDeleteClicked: (Product) -> Unit,
    private val getDepartmentName: (Long?) -> String,
    private val onOrderChanged: (List<Product>) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(ProductDiffCallback()),
    ItemTouchHelperAdapter {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = getItem(position)
        holder.bind(product, getDepartmentName, onDeleteClicked)
        holder.itemView.setOnClickListener {
            onItemClicked(product)
        }
    }

    override fun isItemDraggable(position: Int): Boolean {
        return position < itemCount
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        val list = currentList.toMutableList()
        Collections.swap(list, fromPosition, toPosition)
        submitList(list)
        return true
    }

    override fun onDragFinished() {
        onOrderChanged(currentList)
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