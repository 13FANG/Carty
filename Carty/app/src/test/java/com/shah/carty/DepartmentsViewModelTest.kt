package com.shah.carty

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class DepartmentsViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: DepartmentsViewModel
    private lateinit var mockRepository: CartyRepository

    @Before
    fun setUp() {
        mockRepository = mockk()
    }

    @Test
    fun `uiState should reflect departments from repository`() = runTest {
        val testDepartments = listOf(
            Department(departmentId = 1, departmentName = "Молочка", ownerId = "user1"),
            Department(departmentId = 2, departmentName = "Хлеб", ownerId = "user1")
        )
        coEvery { mockRepository.getAllDepartmentsList() } returns flowOf(testDepartments)

        viewModel = DepartmentsViewModel(mockRepository)

        viewModel.uiState.test {
            val первыйЭмишн = awaitItem()
            assertEquals(emptyList<Department>(), первыйЭмишн.departmentList)

            val второйЭмишн = awaitItem()
            assertEquals(testDepartments, второйЭмишн.departmentList)
            assertEquals(testDepartments.size, второйЭмишн.departmentList.size)
            assertEquals("Молочка", второйЭмишн.departmentList[0].departmentName)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `addDepartment should call repository addDepartment`() = runTest {
        val departmentName = "Новый отдел"
        val ownerId = "user_guest_or_id"
        val expectedDepartment = Department(departmentName = departmentName, ownerId = ownerId)

        coEvery { mockRepository.getAllDepartmentsList() } returns flowOf(emptyList())
        coEvery { mockRepository.addDepartment(any()) } returns Unit

        viewModel = DepartmentsViewModel(mockRepository)
        viewModel.addDepartment(departmentName)
    }
}