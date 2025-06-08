package com.shah.carty

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class SimpleItemTouchHelperCallback(
    private val adapter: ItemTouchHelperAdapter
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean {
        return true
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return false
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION || !adapter.isItemDraggable(position)) {
            return makeMovementFlags(0, 0)
        }

        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = 0
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition

        if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
            return false
        }
        if (adapter.isItemDraggable(fromPosition) && !adapter.isItemDraggable(toPosition)) {
            val movingAdapter = recyclerView.adapter
            if (movingAdapter is ShoppingListItemAdapter) {
                if (movingAdapter.getItemViewType(toPosition) == ShoppingListItemAdapter.VIEW_TYPE_HEADER) {
                    return false
                }
            }
        }

        return adapter.onItemMove(fromPosition, toPosition)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder is ItemTouchHelperViewHolder) {
                viewHolder.onItemSelected()
            }
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder is ItemTouchHelperViewHolder) {
            viewHolder.onItemClear()
        }
        val position = viewHolder.adapterPosition
        if (position != RecyclerView.NO_POSITION && adapter.isItemDraggable(position)) {
            adapter.onDragFinished()
        } else if (position == RecyclerView.NO_POSITION && viewHolder is ItemTouchHelperViewHolder) {
            adapter.onDragFinished()
        }
    }

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val currentPosition = current.adapterPosition
        val targetPosition = target.adapterPosition
        if (currentPosition == RecyclerView.NO_POSITION || targetPosition == RecyclerView.NO_POSITION) {
            return false
        }
        if (!adapter.isItemDraggable(targetPosition)) {
            return false
        }
        return currentPosition != targetPosition
    }
}