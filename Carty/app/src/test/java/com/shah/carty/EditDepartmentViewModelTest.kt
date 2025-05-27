package com.shah.carty

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class EditDepartmentViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: EditDepartmentViewModel
    private lateinit var mockRepository: CartyRepository
    private lateinit var savedStateHandle: SavedStateHandle

    @Test
    fun `saveDepartment should call repository addDepartment for new department and invoke callback`() = runTest(mainCoroutineRule.testDispatcher) {
        savedStateHandle = SavedStateHandle(mapOf("departmentId" to 0L))
        mockRepository = mockk()
        coEvery { mockRepository.getDepartmentById(0L) } returns flowOf(null)
        coJustRun { mockRepository.addDepartment(any()) }

        viewModel = EditDepartmentViewModel(mockRepository, savedStateHandle)

        val departmentName = "Тестовый отдел для сохранения"
        viewModel.updateDepartmentName(departmentName)
        advanceUntilIdle()

        assertTrue("Save button should be enabled", viewModel.uiState.value.saveButtonEnabled)
        assertEquals(departmentName, viewModel.uiState.value.departmentName)

        var callbackSuccess: Boolean? = null

        viewModel.saveDepartment { success ->
            callbackSuccess = success
        }

        advanceUntilIdle()

        assertTrue("Callback should have been called", callbackSuccess != null)
        assertTrue("Save operation should be successful via callback", callbackSuccess == true)

        coVerify(exactly = 1) {
            mockRepository.addDepartment(match {
                it.departmentName == departmentName &&
                        it.ownerId == "" &&
                        it.departmentId == 0L
            })
        }
    }

    @Test
    fun `load existing department should update uiState with department data`() = runTest(mainCoroutineRule.testDispatcher) {
        // Arrange
        val departmentId = 1L
        val departmentName = "Существующий Отдел"
        val ownerId = "user1"
        val firestoreId = "fs1"
        val existingDepartment = Department(departmentId = departmentId, departmentName = departmentName, ownerId = ownerId, firestoreId = firestoreId)

        savedStateHandle = SavedStateHandle(mapOf("departmentId" to departmentId))
        mockRepository = mockk()
        coEvery { mockRepository.getDepartmentById(departmentId) } returns flowOf(existingDepartment)

        viewModel = EditDepartmentViewModel(mockRepository, savedStateHandle)

        viewModel.uiState.test {
            var loadedState: EditDepartmentUiState
            do {
                loadedState = awaitItem()
            } while (loadedState.isLoading || !loadedState.isEditing)

            assertEquals(departmentName, loadedState.departmentName)
            assertTrue(loadedState.isEditing)
            assertFalse(loadedState.isLoading)
            assertEquals(departmentId, loadedState.departmentId)
            assertTrue(loadedState.saveButtonEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `load non_existing department should set departmentNotFound to true`() = runTest(mainCoroutineRule.testDispatcher) {
        val departmentId = 99L
        savedStateHandle = SavedStateHandle(mapOf("departmentId" to departmentId))
        mockRepository = mockk()
        coEvery { mockRepository.getDepartmentById(departmentId) } returns flowOf(null)

        viewModel = EditDepartmentViewModel(mockRepository, savedStateHandle)

        viewModel.uiState.test {
            var finalState: EditDepartmentUiState
            do {
                finalState = awaitItem()
            } while (finalState.isLoading)

            assertTrue(finalState.departmentNotFound)
            assertFalse(finalState.isLoading)
            assertTrue(finalState.isEditing)
            assertEquals(departmentId, finalState.departmentId)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `updateDepartmentName should update name and saveButtonEnabled in uiState`() = runTest(mainCoroutineRule.testDispatcher) {
        savedStateHandle = SavedStateHandle(mapOf("departmentId" to 0L))
        mockRepository = mockk(relaxed = true)
        coEvery { mockRepository.getDepartmentById(0L) } returns flowOf(null)
        viewModel = EditDepartmentViewModel(mockRepository, savedStateHandle)

        val newName = "Новое Имя Отдела"
        viewModel.updateDepartmentName(newName)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value
        assertEquals(newName, updatedState.departmentName)
        assertTrue(updatedState.saveButtonEnabled)

        viewModel.updateDepartmentName("")
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertEquals("", finalState.departmentName)
        assertFalse(finalState.saveButtonEnabled)
    }
}