package com.shah.carty

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.shah.carty.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels {
        AppViewModelProvider(
            (requireActivity().application as CartyApplication).repository,
            requireActivity().application)
    }
    private val cartyApp: CartyApplication
        get() = requireActivity().application as CartyApplication

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.regEmailET.doOnTextChanged { text, _, _, _ ->
            viewModel.updateEmail(text.toString())
        }
        binding.regPasswordET.doOnTextChanged { text, _, _, _ ->
            viewModel.updatePassword(text.toString())
        }
        binding.regPasswordConfirmET.doOnTextChanged { text, _, _, _ ->
            viewModel.updateConfirmPassword(text.toString())
        }

        binding.regButton.setOnClickListener {
            viewModel.registerUser()
        }

        binding.linkToAuthorizationTV.setOnClickListener {
            findNavController().popBackStack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    binding.registerProgressBar?.isVisible = uiState.isLoading
                    binding.regButton.isEnabled = !uiState.isLoading
                    binding.regEmailET.isEnabled = !uiState.isLoading
                    binding.regPasswordET.isEnabled = !uiState.isLoading
                    binding.regPasswordConfirmET.isEnabled = !uiState.isLoading
                    binding.linkToAuthorizationTV.isEnabled = !uiState.isLoading


                    uiState.errorMessage?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        viewModel.consumeErrorMessage()
                    }

                    if (uiState.registrationSuccess) {
                        handleRegistrationSuccess()
                        viewModel.consumeRegistrationSuccessEvent()
                    }
                }
            }
        }
    }

    private fun handleRegistrationSuccess() {
        lifecycleScope.launch {
            binding.registerProgressBar?.isVisible = true
            (cartyApp.repository as OfflineCartyRepository).clearLocalGuestData()
            binding.registerProgressBar?.isVisible = false
            Toast.makeText(context, "Регистрация успешна!", Toast.LENGTH_SHORT).show()
            val action = RegisterFragmentDirections.actionRegisterFragmentToShoppingListsFragment()
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}