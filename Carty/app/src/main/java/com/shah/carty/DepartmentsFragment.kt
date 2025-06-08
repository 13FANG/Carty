package com.shah.carty

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shah.carty.databinding.DialogConfirmDeleteBinding
import com.shah.carty.databinding.FragmentDepartmentsBinding
import kotlinx.coroutines.launch

class DepartmentsFragment : Fragment() {

    private var _binding: FragmentDepartmentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DepartmentsViewModel by viewModels {
        AppViewModelProvider(
            (requireActivity().application as CartyApplication).repository,
            requireActivity().application)
    }

    private lateinit var departmentAdapter: DepartmentAdapter
    private var itemTouchHelper: ItemTouchHelper? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDepartmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeViewModelState() // Переименовал

        binding.addDepartmentFAB.setOnClickListener {
            val action = DepartmentsFragmentDirections.actionDepartmentsFragmentToEditDepartmentFragment(0L)
            findNavController().navigate(action)
        }
    }

    private fun setupRecyclerView() {
        departmentAdapter = DepartmentAdapter(
            onItemClicked = { department ->
                val action = DepartmentsFragmentDirections.actionDepartmentsFragmentToEditDepartmentFragment(department.departmentId)
                findNavController().navigate(action)
            },
            // onItemLongClicked больше нет
            onDeleteClicked = { department ->
                showDeleteConfirmationDialog(department)
            },
            onOrderChanged = { updatedDepartments ->
                viewModel.updateDepartmentsOrder(updatedDepartments)
            }
        )
        binding.departmentsRV.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = departmentAdapter
            itemAnimator = null
        }
        val callback = SimpleItemTouchHelperCallback(departmentAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(binding.departmentsRV)
    }

    private fun observeViewModelState() { // Убрал override и переименовал
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    departmentAdapter.submitList(uiState.departmentList)
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(department: Department) {
        val dialogBinding = DialogConfirmDeleteBinding.inflate(LayoutInflater.from(requireContext()))

        dialogBinding.delDialLableTV.text = getString(R.string.confirm_delete_title)
        dialogBinding.delDialInfoTV.text = getString(R.string.confirm_delete_department_message, department.departmentName)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        dialogBinding.delYesButton.setOnClickListener {
            viewModel.deleteDepartment(department)
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
        _binding?.departmentsRV?.adapter = null
        _binding = null
    }
}