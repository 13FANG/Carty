package com.shah.carty

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemShoppingListBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShoppingListAdapter(
    private val onItemClicked: (ShoppingList) -> Unit
) : ListAdapter<ShoppingList, ShoppingListAdapter.ShoppingListViewHolder>(ShoppingListDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShoppingListViewHolder {
        val binding = ItemShoppingListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShoppingListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShoppingListViewHolder, position: Int) {
        val shoppingList = getItem(position)
        holder.bind(shoppingList)
        holder.itemView.setOnClickListener {
            onItemClicked(shoppingList)
        }
    }

    class ShoppingListViewHolder(private val binding: ItemShoppingListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        fun bind(shoppingList: ShoppingList) {
            binding.itemShoppingListNameTV.text = shoppingList.shoppingListName
            val dateString = dateFormat.format(Date(shoppingList.updatedAt))
            binding.infoProductItemTV.text = "Обновлен: $dateString"

            if (shoppingList.isFavorite) {
                binding.favIconIV.setImageResource(R.drawable.favoriteicon)
            } else {
                binding.favIconIV.setImageResource(R.drawable.notfavoriteicon)
            }
        }
    }
}

class ShoppingListDiffCallback : DiffUtil.ItemCallback<ShoppingList>() {
    override fun areItemsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean {
        return oldItem.shoppingListId == newItem.shoppingListId
    }

    override fun areContentsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean {
        return oldItem == newItem
    }
}