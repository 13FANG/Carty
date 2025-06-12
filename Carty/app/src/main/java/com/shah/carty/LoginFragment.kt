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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shah.carty.databinding.DialogPasswordRecoveryBinding
import com.shah.carty.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels {
        AppViewModelProvider(
            (requireActivity().application as CartyApplication).repository,
            requireActivity().application
        )
    }
    private val cartyApp: CartyApplication
        get() = requireActivity().application as CartyApplication

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            if (cartyApp.repository.isUserLoggedIn()) {
                navigateToMainApp()
                return@launch
            }
        }


        binding.authEmailET.doOnTextChanged { text, _, _, _ ->
            viewModel.updateEmail(text.toString())
        }
        binding.authPasswordET.doOnTextChanged { text, _, _, _ ->
            viewModel.updatePassword(text.toString())
        }

        binding.logInButton.setOnClickListener {
            viewModel.loginUser()
        }

        binding.linkToRegistrationTV.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.passwordRecoveryLinkTV.setOnClickListener {
            showPasswordRecoveryDialog()
        }

        binding.guestModeLinkTV?.setOnClickListener {
            navigateToMainApp()
        }


        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    binding.loginProgressBar?.isVisible = uiState.isLoading
                    binding.logInButton.isEnabled = !uiState.isLoading
                    binding.authEmailET.isEnabled = !uiState.isLoading
                    binding.authPasswordET.isEnabled = !uiState.isLoading
                    binding.linkToRegistrationTV.isEnabled = !uiState.isLoading
                    binding.passwordRecoveryLinkTV.isEnabled = !uiState.isLoading
                    binding.guestModeLinkTV?.isEnabled = !uiState.isLoading


                    uiState.errorMessage?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        viewModel.consumeErrorMessage()
                    }

                    if (uiState.loginSuccess) {
                        handleLoginSuccess()
                        viewModel.consumeLoginSuccessEvent()
                    }
                }
            }
        }
    }

    private fun handleLoginSuccess() {
        lifecycleScope.launch {
            binding.loginProgressBar?.isVisible = true

            (cartyApp.repository as OfflineCartyRepository).clearLocalGuestData()
            cartyApp.repository.fetchAndOverwriteLocalData()

            binding.loginProgressBar?.isVisible = false
            navigateToMainApp()
        }
    }

    private fun navigateToMainApp() {
        val action = LoginFragmentDirections.actionLoginFragmentToShoppingListsFragment()
        findNavController().navigate(action)
    }

    private fun showPasswordRecoveryDialog() {
        val dialogBinding = DialogPasswordRecoveryBinding.inflate(LayoutInflater.from(requireContext()))
        val dialogView = dialogBinding.root

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogBinding.recoveryButton.setOnClickListener {
            val email = dialogBinding.recoveryEmailET.text.toString()
            viewModel.resetPassword(email,
                onSuccess = {
                    Toast.makeText(requireContext(), getString(R.string.password_recovery_success_message), Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                },
                onFailure = { errorMsg ->
                    dialogBinding.recoveryEmailET.error = errorMsg
                }
            )
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}