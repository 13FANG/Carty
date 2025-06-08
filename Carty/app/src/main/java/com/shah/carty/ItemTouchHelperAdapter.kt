package com.shah.carty

interface ItemTouchHelperAdapter {
    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean
    fun onItemDismiss(position: Int)
    fun onDragFinished()
    fun getItemCount(): Int
    fun isItemDraggable(position: Int): Boolean
}