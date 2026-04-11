package com.somnath.codestack.model

enum class FileStatus { Pending, Syncing, Ready, Error }

data class ChatMessage(val text: String, val isUser: Boolean)

data class ProjectFile(
    val name: String,
    val path: String,
    val content: String,
    var status: FileStatus = FileStatus.Pending
)

data class ArchitectResponse(val files: List<ProjectFile>)

data class ArchitectureBlueprint(
    val type: String,
    val infra: String,
    val security: String,
    val summary: String
)

enum class WorkflowPhase {
    Discussion, // Phase 1: Chat + Chips
    Blueprint,  // Phase 2: HLD/LLD Summary
    Execution   // Phase 3: Real Build
}

data class WorkflowStep(val id: Int, val title: String)
