package com.shah.carty

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.shah.carty.databinding.FragmentEditProductBinding
import kotlinx.coroutines.launch

class EditProductFragment : Fragment() {

    private var _binding: FragmentEditProductBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditProductViewModel by viewModels {
        AppViewModelProvider(
            (requireActivity().application as CartyApplication).repository,
            requireActivity().application
        )
    }

    private lateinit var departmentArrayAdapter: ArrayAdapter<String>
    private lateinit var unitDisplayArrayAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        setupInputListeners()

        binding.saveProductButton.setOnClickListener {
            viewModel.saveProduct {
                if (isAdded && view != null) {
                    findNavController().popBackStack()
                }
            }
        }

        binding.toNewDepartmentLinkTV.setOnClickListener {
            val action = EditProductFragmentDirections.actionEditProductFragmentToEditDepartmentFragment(0L)
            findNavController().navigate(action)
        }

        setFragmentResultListener(EditDepartmentFragment.REQUEST_KEY_DEPARTMENT_SAVED) { _, bundle ->
            val departmentWasSaved = bundle.getBoolean(EditDepartmentFragment.BUNDLE_KEY_SAVED_DEPARTMENT_FLAG, false)
            if (departmentWasSaved) {
                viewModel.refreshDepartments()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    if (uiState.productNotFound) {
                        Toast.makeText(requireContext(), "Товар не найден", Toast.LENGTH_LONG).show()
                        if (isAdded && getView() != null) {
                            findNavController().popBackStack()
                        }
                        return@collect
                    }

                    if (!uiState.isLoading) {
                        if (binding.productNameET.text.toString() != uiState.productName && !binding.productNameET.hasFocus()) {
                            binding.productNameET.setText(uiState.productName)
                        }
                        if (binding.priceET.text.toString() != uiState.defaultPrice && !binding.priceET.hasFocus()) {
                            binding.priceET.setText(uiState.defaultPrice)
                        }

                        val departmentNames = mutableListOf(getString(R.string.no_department_selected))
                        departmentNames.addAll(uiState.allDepartments.map { it.departmentName })
                        departmentArrayAdapter.clear()
                        departmentArrayAdapter.addAll(departmentNames)
                        departmentArrayAdapter.notifyDataSetChanged()

                        val currentSelectedDepartmentPosition = uiState.allDepartments.indexOfFirst { it.departmentId == uiState.selectedDepartmentId }
                        if (binding.departmentSpinner.selectedItemPosition != (currentSelectedDepartmentPosition + 1)) {
                            binding.departmentSpinner.setSelection(if (currentSelectedDepartmentPosition != -1) currentSelectedDepartmentPosition + 1 else 0, false)
                        }


                        val unitDisplayNames = uiState.allUnitsDisplay.map { it.displayName }
                        unitDisplayArrayAdapter.clear()
                        unitDisplayArrayAdapter.addAll(unitDisplayNames)
                        unitDisplayArrayAdapter.notifyDataSetChanged()

                        val currentSelectedUnitPosition = uiState.allUnitsDisplay.indexOfFirst { it.unit == uiState.selectedUnit }
                        if (binding.unitSpinner.selectedItemPosition != currentSelectedUnitPosition && currentSelectedUnitPosition != -1) {
                            binding.unitSpinner.setSelection(currentSelectedUnitPosition, false)
                        }
                    }

                    binding.addProductLable.text = if (uiState.isEditing) getString(R.string.edit_product_title) else getString(R.string.add_product_title)
                    binding.saveProductButton.isEnabled = uiState.saveButtonEnabled

                    if (uiState.isEditing && uiState.productName.isNotEmpty() && !uiState.isLoading && !binding.productNameET.hasFocus()) {
                        binding.productNameET.setSelection(binding.productNameET.text.length)
                    }
                }
            }
        }
    }

    private fun setupInputListeners() {
        binding.productNameET.doOnTextChanged { text, _, _, _ ->
            viewModel.updateProductName(text.toString())
        }
        binding.priceET.doOnTextChanged { text, _, _, _ ->
            viewModel.updateDefaultPrice(text.toString())
        }
    }

    private fun setupSpinners() {
        departmentArrayAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_custom, mutableListOf(getString(R.string.no_department_selected)))
        departmentArrayAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_custom)
        binding.departmentSpinner.adapter = departmentArrayAdapter

        unitDisplayArrayAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_custom, mutableListOf<String>())
        unitDisplayArrayAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_custom)
        binding.unitSpinner.adapter = unitDisplayArrayAdapter


        binding.departmentSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    viewModel.updateSelectedDepartment(null)
                } else {
                    val selectedDept = viewModel.uiState.value.allDepartments.getOrNull(position - 1)
                    selectedDept?.let { viewModel.updateSelectedDepartment(it) }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.unitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedUnitDisplay = viewModel.uiState.value.allUnitsDisplay.getOrNull(position)
                selectedUnitDisplay?.let { viewModel.updateSelectedUnit(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}