package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.GitHubBranch
import com.example.engine.GitHubCommitWrapper
import com.example.engine.GitHubRepoInfo
import com.example.engine.GitHubWorkflowRun
import com.example.engine.VcsClient
import com.example.engine.WorkflowDispatchRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevToolsState(
    val owner: String = "Al-Azif",
    val repo: String = "ps4-payload-guest",
    val branch: String = "main",
    val workflowFile: String = "build.yml",
    val deployEnv: String = "staging",
    val patToken: String = "",
    val isLoading: Boolean = false,
    val repoInfo: GitHubRepoInfo? = null,
    val branches: List<GitHubBranch> = emptyList(),
    val commits: List<GitHubCommitWrapper> = emptyList(),
    val workflowRuns: List<GitHubWorkflowRun> = emptyList(),
    val generatedYaml: String = VcsClient.generateCiWorkflowYaml("PKG Forge Project", "main", "staging", "CUSA00001"),
    val statusMessage: String? = null
)

class DevToolsViewModel : ViewModel() {
    private val _state = MutableStateFlow(DevToolsState())
    val state: StateFlow<DevToolsState> = _state.asStateFlow()

    fun updateFields(
        owner: String = _state.value.owner,
        repo: String = _state.value.repo,
        branch: String = _state.value.branch,
        workflowFile: String = _state.value.workflowFile,
        deployEnv: String = _state.value.deployEnv,
        patToken: String = _state.value.patToken
    ) {
        _state.update {
            it.copy(
                owner = owner,
                repo = repo,
                branch = branch,
                workflowFile = workflowFile,
                deployEnv = deployEnv,
                patToken = patToken
            )
        }
    }

    fun clearMessage() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun syncRepository(onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        val authHeader = st.patToken.trim().takeIf { it.isNotEmpty() }?.let { "Bearer $it" }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                val repoInfo = VcsClient.api.getRepo(st.owner.trim(), st.repo.trim(), authHeader)
                val targetBranch = st.branch.ifBlank { repoInfo.defaultBranch }
                val branches = runCatching {
                    VcsClient.api.getBranches(st.owner.trim(), st.repo.trim(), 15, authHeader)
                }.getOrDefault(emptyList())
                val commits = runCatching {
                    VcsClient.api.getCommits(st.owner.trim(), st.repo.trim(), targetBranch, 10, authHeader)
                }.getOrDefault(emptyList())
                val runs = runCatching {
                    VcsClient.api.getWorkflowRuns(st.owner.trim(), st.repo.trim(), 8, authHeader).workflowRuns
                }.getOrDefault(emptyList())

                _state.update {
                    it.copy(
                        isLoading = false,
                        branch = targetBranch,
                        repoInfo = repoInfo,
                        branches = branches,
                        commits = commits,
                        workflowRuns = runs,
                        statusMessage = "Synced ${repoInfo.fullName} (${commits.size} commits, ${runs.size} runs)"
                    )
                }
                onLog("VCS", "INFO", "REPO_SYNCED", "${repoInfo.fullName}@$targetBranch")
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, statusMessage = "GitHub sync failed: ${e.localizedMessage}")
                }
                onLog("VCS", "ERROR", "REPO_SYNC_ERROR", e.localizedMessage ?: "Network error")
            }
        }
    }

    fun triggerWorkflowDispatch(onLog: (String, String, String, String) -> Unit) {
        val st = _state.value
        val token = st.patToken.trim()
        if (token.isEmpty()) {
            _state.update { it.copy(statusMessage = "Enter a GitHub Personal Access Token (in-memory only) to dispatch workflows.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val resp = VcsClient.api.triggerWorkflow(
                    owner = st.owner.trim(),
                    repo = st.repo.trim(),
                    workflowFile = st.workflowFile.trim(),
                    authHeader = "Bearer $token",
                    body = WorkflowDispatchRequest(
                        ref = st.branch.trim(),
                        inputs = mapOf("environment" to st.deployEnv.trim())
                    )
                )
                if (resp.isSuccessful) {
                    _state.update { it.copy(isLoading = false, statusMessage = "Triggered ${st.workflowFile} on ${st.branch}") }
                    onLog("VCS", "INFO", "WORKFLOW_DISPATCHED", "${st.owner}/${st.repo}:${st.workflowFile}")
                } else {
                    _state.update { it.copy(isLoading = false, statusMessage = "Dispatch HTTP ${resp.code()}: ${resp.message()}") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, statusMessage = "Dispatch failed: ${e.localizedMessage}") }
            }
        }
    }

    fun regenerateYaml(titleId: String) {
        val st = _state.value
        _state.update {
            it.copy(
                generatedYaml = VcsClient.generateCiWorkflowYaml(
                    projectName = st.repo.ifBlank { "PKG Forge Project" },
                    branch = st.branch.ifBlank { "main" },
                    deployEnv = st.deployEnv.ifBlank { "production" },
                    titleId = titleId.ifBlank { "CUSA00001" }
                ),
                statusMessage = "Generated CI/CD workflow YAML for $titleId"
            )
        }
    }
}
