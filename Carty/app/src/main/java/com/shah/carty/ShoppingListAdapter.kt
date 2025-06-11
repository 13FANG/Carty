package com.shah.carty

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemShoppingListBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayList
import java.util.Collections

class ShoppingListAdapter(
    private val onItemClicked: (ShoppingList) -> Unit,
    private val onOrderChanged: (List<ShoppingList>) -> Unit
) : ListAdapter<ShoppingList, ShoppingListAdapter.ShoppingListViewHolder>(ShoppingListDiffCallback()),
    ItemTouchHelperAdapter {

    private val internalList: MutableList<ShoppingList> = ArrayList()

    override fun submitList(list: List<ShoppingList>?) {
        super.submitList(list) {
            internalList.clear()
            if (list != null) {
                internalList.addAll(list)
            }
        }
    }

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

    override fun getItemCount(): Int {
        return currentList.size
    }

    override fun isItemDraggable(position: Int): Boolean {
        return position < currentList.size
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
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
        onOrderChanged(ArrayList(internalList))
    }

    override fun onItemDismiss(position: Int) {}


    class ShoppingListViewHolder(private val binding: ItemShoppingListBinding) :
        RecyclerView.ViewHolder(binding.root), ItemTouchHelperViewHolder {

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

class ShoppingListDiffCallback : DiffUtil.ItemCallback<ShoppingList>() {
    override fun areItemsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean {
        return oldItem.shoppingListId == newItem.shoppingListId
    }

    override fun areContentsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean {
        return oldItem == newItem
    }
}