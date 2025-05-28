package com.shah.carty

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemProductBinding
import java.util.ArrayList

class ProductAdapter(
    private val onItemClicked: (Product) -> Unit,
    private val onDeleteClicked: (Product) -> Unit,
    private val getDepartmentName: (Long?) -> String,
    private val onOrderChanged: (List<Product>) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(ProductDiffCallback()),
    ItemTouchHelperAdapter {

    private var internalList: MutableList<Product> = mutableListOf()
    private var dragInProgress = false

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
        val product: Product = if (dragInProgress && position < internalList.size) {
            internalList[position]
        } else if (position < super.getItemCount()){
            getItem(position)
        } else {
            if (internalList.isNotEmpty()) internalList[0] else Product(productName = "Error")
        }

        holder.bind(product, getDepartmentName, onDeleteClicked)
        holder.itemView.setOnClickListener {
            onItemClicked(product)
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


    class ProductViewHolder(private val binding: ItemProductBinding) :
        RecyclerView.ViewHolder(binding.root), ItemTouchHelperViewHolder {
        fun bind(product: Product, getDepartmentName: (Long?) -> String, onDeleteClicked: (Product) -> Unit) {
            binding.productNameTV.text = product.productName
            binding.departmentNameTV.text = getDepartmentName(product.departmentId)
            binding.unitProductItemTV.text = product.defaultUnit.name
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