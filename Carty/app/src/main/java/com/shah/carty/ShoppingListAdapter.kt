package com.shah.carty

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemShoppingListBinding
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.ArrayList


class ShoppingListAdapter(
    private val onItemClicked: (ShoppingList) -> Unit,
    private val onOrderChanged: (List<ShoppingList>) -> Unit
) : ListAdapter<ShoppingList, ShoppingListAdapter.ShoppingListViewHolder>(ShoppingListDiffCallback()),
    ItemTouchHelperAdapter {

    private var currentlyDraggedList: MutableList<ShoppingList>? = null
    private var listChangedDuringDrag = false

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

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (currentList.isEmpty() || fromPosition < 0 || fromPosition >= currentList.size || toPosition < 0 || toPosition >= currentList.size) {
            return false
        }

        if (currentlyDraggedList == null) {
            currentlyDraggedList = ArrayList(currentList)
            listChangedDuringDrag = false
        }

        val listToModify = currentlyDraggedList!!

        if (fromPosition == toPosition) {
            return !listChangedDuringDrag
        }

        val item = listToModify.removeAt(fromPosition)
        listToModify.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
        listChangedDuringDrag = true
        return true
    }

    override fun onDragFinished() {
        currentlyDraggedList?.let { draggedList ->
            if (listChangedDuringDrag) {
                val finalList = ArrayList(draggedList)
                onOrderChanged(finalList)

                submitList(finalList) {
                    notifyDataSetChanged()
                }
            }
        }
        currentlyDraggedList = null
        listChangedDuringDrag = false
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