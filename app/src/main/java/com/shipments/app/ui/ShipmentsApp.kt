package com.shipments.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shipments.app.viewmodel.ShipmentsViewModel

@Composable
fun ShipmentsApp(viewModel: ShipmentsViewModel = viewModel()) {
    var selectedDriverIndex by remember { mutableStateOf(-1) }

    if (selectedDriverIndex >= 0) {
        BackHandler { selectedDriverIndex = -1 }
        DriverDetailScreen(
            viewModel = viewModel,
            driverIndex = selectedDriverIndex,
            onBackClick = { selectedDriverIndex = -1 }
        )
    } else {
        DriverListScreen(
            viewModel = viewModel,
            onDriverClick = { selectedDriverIndex = it }
        )
    }
}
