package com.shah.carty

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.shah.carty.databinding.FragmentShoppingListsBinding
import kotlinx.coroutines.launch

class ShoppingListsFragment : Fragment() {

    private var _binding: FragmentShoppingListsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShoppingListsViewModel by viewModels {
        AppViewModelProvider(
            (requireActivity().application as CartyApplication).repository,
            requireActivity().application
        )
    }
    private val cartyApp: CartyApplication
        get() = requireActivity().application as CartyApplication

    private lateinit var shoppingListAdapter: ShoppingListAdapter
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShoppingListsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModel()

        binding.addShoppingListFAB.setOnClickListener {
            viewModel.createNewShoppingListAndNavigate { newListId ->
                val action = ShoppingListsFragmentDirections.actionShoppingListsFragmentToViewShoppingListFragment(newListId)
                findNavController().navigate(action)
            }
        }

        binding.overflowMenuButton.setOnClickListener { anchorView ->
            showPopupMenu(anchorView)
        }
    }

    private fun setupRecyclerView() {
        shoppingListAdapter = ShoppingListAdapter(
            onItemClicked = { shoppingList ->
                val action = ShoppingListsFragmentDirections.actionShoppingListsFragmentToViewShoppingListFragment(shoppingList.shoppingListId)
                findNavController().navigate(action)
            },
            onOrderChanged = { updatedList ->
                viewModel.updateShoppingListsOrder(updatedList)
            }
        )
        binding.shoppingListsRV.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = shoppingListAdapter
            itemAnimator = null
        }

        val callback = SimpleItemTouchHelperCallback(shoppingListAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(binding.shoppingListsRV)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    shoppingListAdapter.submitList(uiState.activeLists)
                }
            }
        }
    }

    private fun showPopupMenu(anchor: View) {
        val wrapper = ContextThemeWrapper(requireContext(), R.style.MyPopupMenu_White)
        val popup = PopupMenu(wrapper, anchor)
        popup.menuInflater.inflate(R.menu.shopping_lists_menu, popup.menu)

        val currentUser = FirebaseAuth.getInstance().currentUser
        popup.menu.findItem(R.id.action_logout)?.isVisible = (currentUser != null)
        popup.menu.findItem(R.id.action_login)?.isVisible = (currentUser == null)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_show_products -> {
                    findNavController().navigate(ShoppingListsFragmentDirections.actionShoppingListsFragmentToProductsFragment(-1L, false))
                    true
                }
                R.id.action_show_departments -> {
                    findNavController().navigate(ShoppingListsFragmentDirections.actionShoppingListsFragmentToDepartmentsFragment())
                    true
                }
                R.id.action_logout -> {
                    lifecycleScope.launch {
                        (cartyApp.repository as OfflineCartyRepository).clearLocalUserDataOnSignOut()
                        FirebaseAuth.getInstance().signOut()
                        val navOptions = androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                        findNavController().navigate(R.id.loginFragment, null, navOptions)
                    }
                    true
                }
                R.id.action_login -> {
                    val navOptions = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.nav_graph, true)
                        .build()
                    findNavController().navigate(R.id.loginFragment, null, navOptions)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        itemTouchHelper?.attachToRecyclerView(null)
        _binding?.shoppingListsRV?.adapter = null
        _binding = null
    }
}