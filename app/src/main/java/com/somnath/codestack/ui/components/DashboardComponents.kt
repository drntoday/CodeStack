package com.somnath.codestack.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.somnath.codestack.Screen
import com.somnath.codestack.ui.theme.DeepSlate
import com.somnath.codestack.ui.theme.ElectricBlue
import com.somnath.codestack.ui.theme.SlateCard
import com.somnath.codestack.ui.theme.Violet
import kotlinx.coroutines.launch

@Composable
fun DashboardHeader(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Welcome back, Developer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "System Status: Optimal | Ready for Deployment",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "scale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .height(140.dp)
            .border(1.dp, iconColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                color = iconColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.padding(12.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.Gray, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
fun DrawerContent(
    navController: NavController,
    drawerState: DrawerState,
    onClose: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val scope = rememberCoroutineScope()

    ModalDrawerSheet {
        Spacer(Modifier.height(12.dp))
        Text("CODESTACK", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        
        NavigationDrawerItem(
            label = { Text("Dashboard") },
            selected = currentRoute == "dashboard",
            onClick = {
                navController.navigate("dashboard") {
                    popUpTo("dashboard") { inclusive = true }
                }
                scope.launch { drawerState.close() }
            },
            icon = { Icon(Icons.Default.Menu, contentDescription = null) }
        )
        NavigationDrawerItem(
            label = { Text("Terminal") },
            selected = currentRoute?.contains("terminal") == true,
            onClick = {
                navController.navigate(Screen.Terminal.createRoute(false))
                scope.launch { drawerState.close() }
            },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
        )
        NavigationDrawerItem(
            label = { Text("Vault") },
            selected = currentRoute == "vault",
            onClick = {
                navController.navigate("vault")
                scope.launch { drawerState.close() }
            },
            icon = { Icon(Icons.Default.Code, contentDescription = null) }
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = currentRoute == "settings",
            onClick = {
                navController.navigate(Screen.Settings.route)
                scope.launch { drawerState.close() }
            },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) }
        )
    }
}
