package com.shah.carty

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.shah.carty.databinding.FragmentEditDepartmentBinding
import kotlinx.coroutines.launch

class EditDepartmentFragment : Fragment() {

    private var _binding: FragmentEditDepartmentBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val REQUEST_KEY_DEPARTMENT_SAVED = "departmentSavedRequestKey"
        const val BUNDLE_KEY_SAVED_DEPARTMENT_FLAG = "savedDepartmentFlag"
    }

    private val viewModel: EditDepartmentViewModel by viewModels {
        AppViewModelProvider(
            (requireActivity().application as CartyApplication).repository,
            requireActivity().application
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditDepartmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.departmentNameET.doOnTextChanged { text, _, _, _ ->
            viewModel.updateDepartmentName(text.toString())
        }

        binding.saveDepartmentButton.setOnClickListener {
            viewModel.saveDepartment { success ->
                if (success) {
                    setFragmentResult(REQUEST_KEY_DEPARTMENT_SAVED, bundleOf(BUNDLE_KEY_SAVED_DEPARTMENT_FLAG to true))
                    findNavController().popBackStack()
                } else {
                    Toast.makeText(requireContext(), "Не удалось сохранить отдел", Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    if (uiState.departmentNotFound) {
                        Toast.makeText(requireContext(), "Отдел не найден", Toast.LENGTH_LONG).show()
                        findNavController().popBackStack()
                        return@collect
                    }

                    if (uiState.isEditing && binding.departmentNameET.text.toString() != uiState.departmentName) {
                        if (!uiState.isLoading && (binding.departmentNameET.text.isEmpty() || !binding.departmentNameET.hasFocus())) {
                            binding.departmentNameET.setText(uiState.departmentName)
                            binding.departmentNameET.setSelection(uiState.departmentName.length)
                        }
                    }
                    binding.addDepartmentLable.text = if (uiState.isEditing) getString(R.string.edit_department_title) else getString(R.string.add_department_title)
                    binding.saveDepartmentButton.isEnabled = uiState.saveButtonEnabled
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}