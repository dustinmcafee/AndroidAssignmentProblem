package com.shipments.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.shipments.app.BuildConfig
import com.shipments.app.model.AssignmentProblemSolver
import com.shipments.app.model.SuitabilityScoreCalculator
import com.shipments.app.model.data.DriverAssignment
import com.shipments.app.model.data.ShipmentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShipmentsUiState(
    val isLoading: Boolean = true,
    val assignments: List<DriverAssignment> = emptyList(),
    val error: String? = null
)

class ShipmentsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ShipmentsUiState())
    val uiState: StateFlow<ShipmentsUiState> = _uiState.asStateFlow()

    private val scoreCalculator = SuitabilityScoreCalculator()
    private val hungarianAlgorithm = AssignmentProblemSolver()

    init {
        loadAssignments()
    }

    private fun loadAssignments() {
        viewModelScope.launch {
            try {
                val data = loadShipmentData()
                val assignments = computeOptimalAssignments(data)
                _uiState.value = ShipmentsUiState(
                    isLoading = false,
                    assignments = assignments
                )
            } catch (e: Exception) {
                _uiState.value = ShipmentsUiState(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
                if (BuildConfig.DEBUG) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun loadShipmentData(): ShipmentData = withContext(Dispatchers.IO) {
        val json = getApplication<Application>().assets.open("input.json")
            .bufferedReader()
            .use { it.readText() }
        Gson().fromJson(json, ShipmentData::class.java)
    }

    private fun computeOptimalAssignments(data: ShipmentData): List<DriverAssignment> {
        val drivers = data.drivers
        val shipments = data.shipments
        val numDrivers = drivers.size
        val numShipments = shipments.size
        val n = maxOf(numDrivers, numShipments)

        // Build NxN suitability score matrix, padding with 0.0 for dummy rows/columns
        val scoreMatrix = Array(n) { i ->
            DoubleArray(n) { j ->
                if (i < numDrivers && j < numShipments) {
                    scoreCalculator.calculate(shipments[j], drivers[i])
                } else {
                    0.0 // Not really used for current input.json, but useful, nonetheless
                }
            }
        }

        // Find optimal assignment maximizing total SS
        val assignment = hungarianAlgorithm.findOptimalAssignment(scoreMatrix)

        // Only return assignments for real drivers matched to real shipments
        return drivers.indices
            .filter { i -> assignment[i] < numShipments }   // Again, not really used for the current input.json, but in case using nxm matrix where n!=m
            .map { i ->
                val j = assignment[i]
                DriverAssignment(
                    driverName = drivers[i],
                    shipmentAddress = shipments[j],
                    suitabilityScore = scoreMatrix[i][j]
                )
            }
    }
}
