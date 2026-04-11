package com.somnath.codestack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.somnath.codestack.model.ArchitectureBlueprint
import com.somnath.codestack.model.ChatMessage
import com.somnath.codestack.model.FileStatus
import com.somnath.codestack.model.ProjectFile
import com.somnath.codestack.model.WorkflowPhase
import com.somnath.codestack.model.WorkflowStep
import com.somnath.codestack.ui.theme.Emerald
import com.somnath.codestack.ui.theme.TerminalGreen
import com.somnath.codestack.ui.theme.Violet

@Composable
fun ChatBubble(message: ChatMessage, onSaveCode: (String) -> Unit) {
    val backgroundColor = if (message.isUser) MaterialTheme.colorScheme.primary else Color(0xFF1E293B)
    val textColor = if (message.isUser) Color.Black else Color.White

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = backgroundColor,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (!message.isUser) {
            Row(
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // Save button could go here if needed
            }
        }
    }
}

@Composable
fun BlueprintRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ArchitectureBlueprintView(
    modifier: Modifier = Modifier,
    blueprint: ArchitectureBlueprint
) {
    Card(
        colors = CardDefaults.cardColors(Color(0xFF1E293B)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "ARCHITECTURE BLUEPRINT",
                color = Violet,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            BlueprintRow("HLD Type", blueprint.type)
            BlueprintRow("Infrastructure", blueprint.infra)
            BlueprintRow("Security", blueprint.security)
            Spacer(Modifier.height(4.dp))
            Text(
                text = blueprint.summary,
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun ProjectPanel(
    modifier: Modifier = Modifier,
    phase: WorkflowPhase,
    currentStep: Int,
    steps: List<WorkflowStep>,
    logs: List<String>,
    manifest: List<ProjectFile>,
    terminalListState: LazyListState,
    blueprint: ArchitectureBlueprint?
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1120))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "WORKFLOW",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = phase.name,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                steps.forEach { step ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    if (step.id <= currentStep) Emerald else Color(0xFF1E293B),
                                    CircleShape
                                )
                                .border(1.dp, Color.Gray, CircleShape)
                        ) {
                            if (step.id < currentStep) Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = Color.Black,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                        Text(
                            text = step.title,
                            color = Color.Gray,
                            fontSize = 8.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (phase == WorkflowPhase.Blueprint && blueprint != null) {
                ArchitectureBlueprintView(
                    modifier = Modifier.fillMaxWidth(),
                    blueprint = blueprint
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = "TERMINAL OUTPUT",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                LazyColumn(
                    state = terminalListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = TerminalGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = Color.Gray.copy(alpha = 0.3f)
        )

        Column(
            modifier = Modifier
                .weight(0.6f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = "FILE MANIFEST",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (manifest.isEmpty()) {
                    if (phase == WorkflowPhase.Discussion) {
                        item {
                            Text(
                                text = "WAITING FOR BLUEPRINT...",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "GENERATING FILES...",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                items(manifest) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Code,
                            null,
                            tint = Violet,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Text(
                                text = file.path,
                                color = Color.Gray,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                        when (file.status) {
                            FileStatus.Syncing -> CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FileStatus.Ready -> Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = Emerald,
                                modifier = Modifier.size(16.dp)
                            )
                            FileStatus.Error -> Icon(
                                Icons.Default.ArrowBack,
                                null,
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                            else -> Box {}
                        }
                    }
                }
            }
        }
    }
}
