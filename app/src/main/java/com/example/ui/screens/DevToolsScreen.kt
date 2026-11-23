package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.UiState
import com.example.model.VcsConfig
import com.example.ui.components.GlassMetricTile
import com.example.ui.components.GlassStatusPill
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.PermissionLockedPanel
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.lStr
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IridescentIndigo
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.DevToolsViewModel

@Composable
fun DevToolsScreen(
    viewModel: DevToolsViewModel,
    vcsConfigsState: UiState<List<VcsConfig>>,
    activeTitleId: String,
    roleName: String,
    canUseDevTools: Boolean,
    onSaveVcsConfig: (VcsConfig) -> Unit,
    onLog: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var titleIdInput by remember(activeTitleId) { mutableStateOf(activeTitleId.ifBlank { "CUSA00001" }) }

    val yamlExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/yaml")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(state.generatedYaml.toByteArray(Charsets.UTF_8))
            }
            onLog("VCS", "INFO", "EXPORTED_CI_YAML", "${state.owner}/${state.repo}")
        }
    }

    if (!canUseDevTools) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            PermissionLockedPanel(roleName = roleName, permissionName = "DEV_VCS_CICD")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (vcsConfigsState is UiState.Success && vcsConfigsState.data.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vcsConfigsState.data.forEach { cfg ->
                    FilterChip(
                        selected = state.owner == cfg.owner && state.repo == cfg.repo,
                        onClick = {
                            viewModel.updateFields(
                                owner = cfg.owner,
                                repo = cfg.repo,
                                branch = cfg.branch,
                                workflowFile = cfg.workflowFile,
                                deployEnv = cfg.deployEnvironment
                            )
                        },
                        label = { Text("${cfg.owner}/${cfg.repo}") }
                    )
                }
            }
        }

        LiquidGlassCard(accentColor = ElectricCyan) {
            SectionHeaderRow(
                title = lStr(R.string.devops_title),
                subtitle = "GitHub REST API • Branches, Commits & Cloud CI/CD",
                icon = Icons.Rounded.AccountTree
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.owner,
                    onValueChange = { viewModel.updateFields(owner = it) },
                    label = { Text("Owner / Org") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.repo,
                    onValueChange = { viewModel.updateFields(repo = it) },
                    label = { Text("Repository") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.branch,
                    onValueChange = { viewModel.updateFields(branch = it) },
                    label = { Text("Branch") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.workflowFile,
                    onValueChange = { viewModel.updateFields(workflowFile = it) },
                    label = { Text("Workflow (.yml)") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.patToken,
                onValueChange = { viewModel.updateFields(patToken = it) },
                label = { Text("GitHub PAT (session memory only, optional for public repos)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.syncRepository(onLog) },
                    modifier = Modifier.weight(1f).testTag("vcs_sync_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(lStr(R.string.devops_fetch_repo))
                }
                OutlinedButton(
                    onClick = { viewModel.triggerWorkflowDispatch(onLog) },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.RocketLaunch, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(lStr(R.string.devops_trigger_deploy))
                }
                OutlinedButton(
                    onClick = {
                        onSaveVcsConfig(
                            VcsConfig(
                                provider = "GITHUB",
                                owner = state.owner,
                                repo = state.repo,
                                branch = state.branch,
                                workflowFile = state.workflowFile,
                                deployEnvironment = state.deployEnv
                            )
                        )
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, "Save Repo", Modifier.size(18.dp))
                }
            }
            if (state.isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!state.statusMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(state.statusMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
            }
        }

        state.repoInfo?.let { info ->
            LiquidGlassCard(accentColor = EmeraldSuccess) {
                SectionHeaderRow(
                    title = info.fullName,
                    subtitle = info.description ?: "No description",
                    icon = Icons.Rounded.CloudDone
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassMetricTile("Default", info.defaultBranch, ElectricCyan, Modifier.weight(1f))
                    GlassMetricTile("Stars", "${info.stars}", AmberWarning, Modifier.weight(1f))
                    GlassMetricTile("Issues", "${info.openIssues}", IridescentIndigo, Modifier.weight(1f))
                }
                if (state.branches.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.branches.forEach { br ->
                            FilterChip(
                                selected = state.branch == br.name,
                                onClick = {
                                    viewModel.updateFields(branch = br.name)
                                    viewModel.syncRepository(onLog)
                                },
                                label = { Text(br.name) }
                            )
                        }
                    }
                }
            }
        }

        if (state.commits.isNotEmpty()) {
            LiquidGlassCard(accentColor = IridescentIndigo) {
                SectionHeaderRow(title = "Recent Commits (${state.branch})", icon = Icons.Rounded.Commit)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.commits.take(6).forEach { cw ->
                        LiquidGlassCard(shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(10.dp)) {
                            Text(
                                text = cw.commit.message.lineSequence().firstOrNull().orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${cw.sha.take(7)} • ${cw.commit.author.name}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }
        }

        if (state.workflowRuns.isNotEmpty()) {
            LiquidGlassCard(accentColor = EmeraldSuccess) {
                SectionHeaderRow(title = "Cloud CI/CD Runs", icon = Icons.Rounded.RocketLaunch)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.workflowRuns.take(5).forEach { run ->
                        val col = when (run.conclusion) {
                            "success" -> EmeraldSuccess
                            "failure" -> CoralError
                            else -> AmberWarning
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(run.name ?: "Workflow #${run.id}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Branch: ${run.headBranch ?: "-"} • ${run.status ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            GlassStatusPill(
                                text = (run.conclusion ?: run.status ?: "RUN").uppercase(),
                                color = col
                            )
                        }
                    }
                }
            }
        }

        // CI/CD Pipeline Generator
        LiquidGlassCard(accentColor = IridescentIndigo) {
            SectionHeaderRow(
                title = "CI/CD Pipeline Studio",
                subtitle = "Generate & export GitHub Actions workflow for PKG builds",
                icon = Icons.Rounded.Code
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = titleIdInput,
                    onValueChange = { titleIdInput = it.uppercase().take(9) },
                    label = { Text("Target Title ID") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { viewModel.regenerateYaml(titleIdInput) }) {
                    Text("Generate")
                }
                OutlinedButton(onClick = { yamlExporter.launch("build-pkg.yml") }) {
                    Icon(Icons.Rounded.Download, "Export YAML", Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.generatedYaml,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
