package com.somnath.codestack.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.somnath.codestack.Screen
import com.somnath.codestack.ui.components.ActionCard
import com.somnath.codestack.ui.components.DashboardHeader
import com.somnath.codestack.ui.theme.*

@Composable
fun DashboardPage(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(DeepSlate),
        horizontalAlignment = Alignment.Start
    ) {
        DashboardHeader()

        Spacer(modifier = Modifier.height(32.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                ActionCard(
                    title = "Initialize Project",
                    description = "Start an autonomous development session.",
                    icon = Icons.Default.FlightTakeoff,
                    iconColor = ElectricBlue,
                    onClick = { navController.navigate(Screen.Terminal.createRoute(true)) }
                )
            }

            item {
                ActionCard(
                    title = "General Query",
                    description = "Consult CodeStack AI for logic or debugging.",
                    icon = Icons.Default.ChatBubble,
                    iconColor = Violet,
                    onClick = { navController.navigate(Screen.Terminal.createRoute(false)) }
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                ActionCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "System Settings",
                    description = "Manage Gemini and GitHub API Credentials.",
                    icon = Icons.Default.Settings,
                    iconColor = SlateCard,
                    onClick = { navController.navigate(Screen.Settings.route) }
                )
            }
        }
    }
}
