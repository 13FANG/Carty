package com.shah.carty

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemProductBinding

class ProductAdapter(
    private val onItemClicked: (Product) -> Unit,
    private val onItemLongClicked: (Product) -> Unit,
    private val getDepartmentName: (Long?) -> String
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(ProductDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = getItem(position)
        holder.bind(product, getDepartmentName, onItemClicked, onItemLongClicked)
    }

    class ProductViewHolder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            product: Product,
            getDepartmentName: (Long?) -> String,
            onItemClickedFromAdapter: (Product) -> Unit,
            onDeleteClickedFromAdapter: (Product) -> Unit
        ) {
            binding.productNameTV.text = product.productName
            binding.departmentNameTV.text = getDepartmentName(product.departmentId)

            val unitDisplayName = when (product.defaultUnit) {
                ProductUnit.PIECE -> "Штука"
                ProductUnit.KILOGRAM -> "Килограмм"
                ProductUnit.GRAM -> "Грамм"
                ProductUnit.LITER -> "Литр"
                ProductUnit.MILLILITER -> "Миллилитр"
                ProductUnit.PACKAGE -> "Упаковка"
            }
            binding.unitProductItemTV.text = unitDisplayName
            binding.priceTV.text = product.defaultPrice?.toString() ?: "?"

            itemView.setOnClickListener {
                onItemClickedFromAdapter(product)
            }
            binding.delItemProdictImageButton.setOnClickListener {
                onDeleteClickedFromAdapter(product)
            }
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