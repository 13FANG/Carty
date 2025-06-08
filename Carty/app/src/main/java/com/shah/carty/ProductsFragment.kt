package com.shah.carty

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shah.carty.databinding.DialogConfirmDeleteBinding
import com.shah.carty.databinding.FragmentProductsBinding
import kotlinx.coroutines.launch

class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProductsViewModel by viewModels {
        AppViewModelProvider(
            (requireActivity().application as CartyApplication).repository,
            requireActivity().application)
    }
    private val navigationArgs: ProductsFragmentArgs by navArgs()

    private lateinit var productAdapter: ProductAdapter
    private var itemTouchHelper: ItemTouchHelper? = null


    companion object {
        const val REQUEST_KEY_PRODUCT_SELECTION = "productSelectionRequestKey"
        const val BUNDLE_KEY_SELECTED_PRODUCT_ID = "selectedProductId"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModelState()

        binding.addProductFAB.setOnClickListener {
            val action = ProductsFragmentDirections.actionProductsFragmentToEditProductFragment(0L)
            findNavController().navigate(action)
        }
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(
            onItemClicked = { product ->
                if (navigationArgs.isSelectionMode) {
                    setFragmentResult(REQUEST_KEY_PRODUCT_SELECTION, bundleOf(BUNDLE_KEY_SELECTED_PRODUCT_ID to product.productId))
                    findNavController().popBackStack()
                } else {
                    val action = ProductsFragmentDirections.actionProductsFragmentToEditProductFragment(product.productId)
                    findNavController().navigate(action)
                }
            },
            onDeleteClicked = { product ->
                if (!navigationArgs.isSelectionMode) {
                    showDeleteConfirmationDialog(product)
                }
            },
            getDepartmentName = { departmentId ->
                viewModel.getDepartmentNameById(departmentId)
            },
            onOrderChanged = { updatedProducts ->
                if (!navigationArgs.isSelectionMode) {
                    viewModel.updateProductsOrder(updatedProducts)
                }
            }
        )
        binding.allProductsRV.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = productAdapter
            itemAnimator = null
        }

        val callback = SimpleItemTouchHelperCallback(productAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        if (!navigationArgs.isSelectionMode) {
            itemTouchHelper?.attachToRecyclerView(binding.allProductsRV)
        } else {
            itemTouchHelper?.attachToRecyclerView(null)
        }
    }

    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    productAdapter.submitList(uiState.productList)
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(product: Product) {
        val dialogBinding = DialogConfirmDeleteBinding.inflate(LayoutInflater.from(requireContext()))
        dialogBinding.delDialLableTV.text = getString(R.string.confirm_delete_title)
        dialogBinding.delDialInfoTV.text = getString(R.string.confirm_delete_product_message, product.productName)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        dialogBinding.delYesButton.setOnClickListener {
            viewModel.deleteProduct(product)
            dialog.dismiss()
        }
        dialogBinding.delNoButton.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        itemTouchHelper?.attachToRecyclerView(null)
        _binding?.allProductsRV?.adapter = null
        _binding = null
    }
}