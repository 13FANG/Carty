package com.shah.carty

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class SimpleItemTouchHelperCallback(
    private val adapter: ItemTouchHelperAdapter,
    private val getAdapterViewType: (Int) -> Int = { -1 } // Функция для получения типа view из адаптера
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled(): Boolean {
        return true
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        // Свайп не используется для удаления в этом контексте, отключаем
        return false
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val itemViewType = getAdapterViewType(viewHolder.adapterPosition)
        var dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN

        if (itemViewType == ShoppingListItemAdapter.VIEW_TYPE_ITEM) {
            // Для товаров разрешаем только вертикальное перемещение
            // Ограничения на перемещение между отделами будут в onItemMove адаптера
        } else if (itemViewType == ShoppingListItemAdapter.VIEW_TYPE_HEADER) {
            // Для заголовков отделов также разрешаем только вертикальное перемещение
        } else {
            // Если тип неизвестен, не разрешаем перетаскивание
            dragFlags = 0
        }

        val swipeFlags = 0 // Свайп отключен
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // Вызываем onItemMove адаптера. Адаптер сам решит, возможно ли перемещение.
        return adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Не используется
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
        adapter.onDragFinished() // Важно вызывать это для фиксации изменений
    }

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return true
    }
}