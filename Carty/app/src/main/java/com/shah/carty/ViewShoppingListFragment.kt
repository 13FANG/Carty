package com.shah.carty

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shah.carty.databinding.DialogAddItemDetailsBinding
import com.shah.carty.databinding.DialogConfirmDeleteBinding
import com.shah.carty.databinding.FragmentViewShoppingListBinding
import kotlinx.coroutines.launch
import java.util.Locale

class ViewShoppingListFragment : Fragment() {

    private var _binding: FragmentViewShoppingListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ViewShoppingListViewModel by viewModels {
        AppViewModelProvider(
            (requireActivity().application as CartyApplication).repository,
            requireActivity().application)
    }

    private lateinit var shoppingListItemAdapter: ShoppingListItemAdapter
    private var itemTouchHelper: ItemTouchHelper? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewShoppingListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModelAndSetupUI()
        setupClickListeners()

        setFragmentResultListener(ProductsFragment.REQUEST_KEY_PRODUCT_SELECTION) { _, bundle ->
            val selectedProductId = bundle.getLong(ProductsFragment.BUNDLE_KEY_SELECTED_PRODUCT_ID, -1L)
            if (selectedProductId != -1L) {
                viewModel.loadProductToAdd(selectedProductId)
            }
        }
    }

    private fun setupRecyclerView() {
        shoppingListItemAdapter = ShoppingListItemAdapter(
            onItemCheckedChanged = { item, isChecked ->
                viewModel.updateShoppingListItemBoughtStatus(item, isChecked)
            },
            onDeleteItemClicked = { item ->
                showDeleteItemConfirmationDialog(item)
            },
            onItemClicked = { shoppingListItem ->
                showEditItemDetailsDialog(shoppingListItem)
            },
            onHeaderClicked = { headerItem ->},
            onOrderChanged = { updatedDisplayableItemsFromAdapter ->
                viewModel.processAndUpdateOrder(updatedDisplayableItemsFromAdapter)
            },
            getDepartmentName = { deptId ->
                viewModel.uiState.value.departmentMap[deptId] ?: getString(R.string.no_department_selected)
            }
        )
        binding.viewShoppingListRV.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = shoppingListItemAdapter
            itemAnimator = null
        }
        val callback = SimpleItemTouchHelperCallback(shoppingListItemAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(binding.viewShoppingListRV)
    }

    private fun observeViewModelAndSetupUI() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    if (uiState.isLoading && uiState.displayableItems.isEmpty() && uiState.currentList == null) {
                        return@collect
                    }

                    if (uiState.listNotFound) {
                        Toast.makeText(requireContext(), "Список не найден", Toast.LENGTH_LONG).show()
                        if (isAdded && view != null) {
                            findNavController().popBackStack()
                        }
                        return@collect
                    }

                    uiState.currentList?.let { list ->
                        if (binding.listNameET.text.toString() != list.shoppingListName && !binding.listNameET.hasFocus()) {
                            binding.listNameET.setText(list.shoppingListName)
                        }
                        binding.toOrFromFavoriteFAB.setImageResource(
                            if (list.isFavorite) R.drawable.favoriteicon else R.drawable.notfavoriteicon
                        )
                        binding.finishListFAB.isEnabled = !list.isCompleted
                    }

                    if (uiState.isGroupingEnabled) {
                        binding.groupProductsFAB.setImageResource(R.drawable.ic_ungroup)
                    } else {
                        binding.groupProductsFAB.setImageResource(R.drawable.ic_group)
                    }

                    binding.totalSumTV.text = String.format(Locale.getDefault(), "Суммарно - %.2f руб.", uiState.totalSum)

                    val previousGroupingState = shoppingListItemAdapter.isGroupingEnabled
                    shoppingListItemAdapter.isGroupingEnabled = uiState.isGroupingEnabled
                    shoppingListItemAdapter.submitList(uiState.displayableItems)

                    if (previousGroupingState != uiState.isGroupingEnabled) {
                        shoppingListItemAdapter.notifyDataSetChanged()
                    }

                    if (uiState.productToAdd != null && childFragmentManager.findFragmentByTag("addItemDetailsDialog") == null) {
                        showAddItemDetailsDialog(uiState.productToAdd)
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.listNameET.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.updateListName(binding.listNameET.text.toString())
            }
        }

        binding.groupProductsFAB.setOnClickListener {
            viewModel.toggleGrouping()
        }

        binding.toOrFromFavoriteFAB.setOnClickListener { viewModel.toggleFavoriteStatus() }
        binding.finishListFAB.setOnClickListener { viewModel.completeShoppingList() }
        binding.deleteListFAB.setOnClickListener { showDeleteListConfirmationDialog() }

        binding.addProductToListFAB.setOnClickListener {
            val currentListId = viewModel.uiState.value.currentList?.shoppingListId ?: return@setOnClickListener
            val action = ViewShoppingListFragmentDirections.actionViewShoppingListFragmentToProductsFragment(currentListId, true)
            findNavController().navigate(action)
        }
    }

    private fun showEditItemDetailsDialog(itemToEdit: ShoppingListItem) {
        val dialogBinding = DialogAddItemDetailsBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.addItemProductNameTV.text = itemToEdit.productName
        dialogBinding.addItemQuantityET.setText(itemToEdit.quantity.toString())
        dialogBinding.addItemPriceET.setText(itemToEdit.price?.toString() ?: "")
        dialogBinding.addItemDetalsButton.text = "Сохранить изменения"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.addItemDetalsButton.setOnClickListener {
            val quantity = dialogBinding.addItemQuantityET.text.toString().toDoubleOrNull() ?: itemToEdit.quantity
            val price = dialogBinding.addItemPriceET.text.toString().toDoubleOrNull()

            viewModel.updateShoppingListItemDetails(itemToEdit, quantity, price)
            dialog.dismiss()
        }
        dialog.show()
    }


    private fun showAddItemDetailsDialog(product: Product) {
        val dialogBinding = DialogAddItemDetailsBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.addItemProductNameTV.text = product.productName
        dialogBinding.addItemPriceET.setText(product.defaultPrice?.toString() ?: "")
        dialogBinding.addItemQuantityET.setText("1.0")
        dialogBinding.addItemDetalsButton.text = "Добавить в список"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialog.setOnDismissListener {
            viewModel.clearProductToAdd()
        }
        dialogBinding.addItemDetalsButton.setOnClickListener {
            val quantity = dialogBinding.addItemQuantityET.text.toString().toDoubleOrNull() ?: 1.0
            val price = dialogBinding.addItemPriceET.text.toString().toDoubleOrNull()
            viewModel.confirmAddProductToList(quantity, price, product.defaultUnit)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showDeleteItemConfirmationDialog(item: ShoppingListItem) {
        val dialogBinding = DialogConfirmDeleteBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.delDialLableTV.text = getString(R.string.confirm_delete_title)
        dialogBinding.delDialInfoTV.text = getString(R.string.confirm_delete_list_item_message, item.productName)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create().apply {
                dialogBinding.delYesButton.setOnClickListener {
                    viewModel.deleteShoppingListItem(item)
                    this.dismiss()
                }
                dialogBinding.delNoButton.setOnClickListener {
                    this.dismiss()
                }
            }.show()
    }

    private fun showDeleteListConfirmationDialog() {
        viewModel.uiState.value.currentList?.let { list ->
            val dialogBinding = DialogConfirmDeleteBinding.inflate(LayoutInflater.from(requireContext()))
            dialogBinding.delDialLableTV.text = getString(R.string.confirm_delete_title)
            dialogBinding.delDialInfoTV.text = getString(R.string.confirm_delete_shopping_list_message, list.shoppingListName)

            MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.root)
                .setCancelable(false)
                .create().apply {
                    dialogBinding.delYesButton.setOnClickListener {
                        viewModel.deleteFullShoppingList {
                            if (isAdded && getView() != null) {
                                findNavController().popBackStack()
                            }
                        }
                        this.dismiss()
                    }
                    dialogBinding.delNoButton.setOnClickListener {
                        this.dismiss()
                    }
                }.show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        itemTouchHelper?.attachToRecyclerView(null)
        _binding?.viewShoppingListRV?.adapter = null
        _binding = null
    }
}