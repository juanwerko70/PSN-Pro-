package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.example.R
import com.example.engine.Mesh3D
import com.example.engine.TextureTransformParams
import com.example.ui.components.GlassMetricTile
import com.example.ui.components.GlassStatusPill
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.lStr
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IridescentIndigo
import com.example.viewmodel.PkgStudioState
import com.example.viewmodel.PkgStudioViewModel
import kotlin.math.roundToInt

@Composable
fun ResourceStudioSubScreen(
    state: PkgStudioState,
    viewModel: PkgStudioViewModel,
    canModify: Boolean,
    onLog: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var studioMode by remember { mutableIntStateOf(0) } // 0 = Textures, 1 = 3D Models

    val textureImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importTexture(context, uri, onLog)
    }

    val pngExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) viewModel.exportTexture(context, uri, asDds = false, onLog = onLog)
    }

    val ddsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.exportTexture(context, uri, asDds = true, onLog = onLog)
    }

    val modelImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.import3dModel(context, uri, onLog)
    }

    val objExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) viewModel.export3dModel(context, uri, asFbx = false, onLog = onLog)
    }

    val fbxExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) viewModel.export3dModel(context, uri, asFbx = true, onLog = onLog)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = studioMode == 0,
                onClick = { studioMode = 0 },
                label = { Text(lStr(R.string.res_mode_texture)) },
                leadingIcon = { Icon(Icons.Rounded.Image, null, Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).testTag("res_tab_textures")
            )
            FilterChip(
                selected = studioMode == 1,
                onClick = { studioMode = 1 },
                label = { Text(lStr(R.string.res_mode_model)) },
                leadingIcon = { Icon(Icons.Rounded.ViewInAr, null, Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f).testTag("res_tab_3d")
            )
        }

        if (studioMode == 0) {
            TextureStudioPanel(
                state = state,
                canModify = canModify,
                onImport = { textureImportLauncher.launch(arrayOf("image/png", "image/*", "application/octet-stream")) },
                onExportPng = {
                    val base = state.textureName.substringBeforeLast('.').ifBlank { "texture" }
                    pngExportLauncher.launch("${base}_edited.png")
                },
                onExportDds = {
                    val base = state.textureName.substringBeforeLast('.').ifBlank { "texture" }
                    ddsExportLauncher.launch("${base}_edited.dds")
                },
                onUpdate = { transform -> viewModel.updateTextureParams(transform) }
            )
        } else {
            Model3dStudioPanel(
                state = state,
                canModify = canModify,
                onImport = { modelImportLauncher.launch(arrayOf("*/*")) },
                onLoadSample = { viewModel.loadSample3dCrate() },
                onExportObj = {
                    val base = state.sourceMesh?.name?.substringBeforeLast('.') ?: "model"
                    objExportLauncher.launch("${base}_edited.obj")
                },
                onExportFbx = {
                    val base = state.sourceMesh?.name?.substringBeforeLast('.') ?: "model"
                    fbxExportLauncher.launch("${base}_edited.fbx")
                },
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun TextureStudioPanel(
    state: PkgStudioState,
    canModify: Boolean,
    onImport: () -> Unit,
    onExportPng: () -> Unit,
    onExportDds: () -> Unit,
    onUpdate: ((TextureTransformParams) -> TextureTransformParams) -> Unit
) {
    LiquidGlassCard(accentColor = ElectricCyan) {
        SectionHeaderRow(
            title = lStr(R.string.res_mode_texture),
            subtitle = if (state.textureName.isNotBlank()) "${state.textureName} • ${state.textureFormat}" else "DDS (DXT1/DXT3/DXT5/RGBA8) & PNG",
            icon = Icons.Rounded.Brush
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onImport,
                modifier = Modifier.weight(1f).testTag("import_texture_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(lStr(R.string.res_import_texture), maxLines = 1)
            }
        }

        val bmp = state.previewBitmap ?: state.sourceBitmap
        if (bmp != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF070B14)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = state.textureName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassMetricTile("Dimensions", "${bmp.width} × ${bmp.height}", ElectricCyan, Modifier.weight(1f))
                GlassMetricTile("Rotation", "${state.textureParams.rotationDegrees.roundToInt()}°", IridescentIndigo, Modifier.weight(1f))
                GlassMetricTile("Scale", "${state.textureParams.scalePercent.roundToInt()}%", EmeraldSuccess, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            // Quick Rotate / Flip / Reset controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onUpdate { it.copy(rotationDegrees = (it.rotationDegrees + 90f) % 360f) }
                    },
                    enabled = canModify,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.RotateRight, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("90°")
                }
                OutlinedButton(
                    onClick = { onUpdate { it.copy(flipHorizontal = !it.flipHorizontal) } },
                    enabled = canModify,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Flip, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Flip H")
                }
                OutlinedButton(
                    onClick = { onUpdate { TextureTransformParams() } },
                    enabled = canModify,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }
            }

            Spacer(Modifier.height(8.dp))
            val p = state.textureParams
            LabeledSlider("Hue (${p.hueDegrees.roundToInt()}°)", p.hueDegrees, -180f..180f, canModify) { v ->
                onUpdate { it.copy(hueDegrees = v) }
            }
            LabeledSlider("Saturation (${"%.2f".format(p.saturation)})", p.saturation, 0f..2f, canModify) { v ->
                onUpdate { it.copy(saturation = v) }
            }
            LabeledSlider("Brightness (${p.brightness.roundToInt()})", p.brightness, -100f..100f, canModify) { v ->
                onUpdate { it.copy(brightness = v) }
            }
            LabeledSlider("Contrast (${"%.2f".format(p.contrast)})", p.contrast, 0.4f..2.2f, canModify) { v ->
                onUpdate { it.copy(contrast = v) }
            }
            LabeledSlider("Scale (${p.scalePercent.roundToInt()}%)", p.scalePercent, 25f..200f, canModify) { v ->
                onUpdate { it.copy(scalePercent = v) }
            }
            LabeledSlider("Free Rotate (${p.rotationDegrees.roundToInt()}°)", p.rotationDegrees, 0f..360f, canModify) { v ->
                onUpdate { it.copy(rotationDegrees = v) }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExportPng,
                    enabled = canModify,
                    modifier = Modifier.weight(1f).testTag("export_png_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(lStr(R.string.res_export_png))
                }
                OutlinedButton(
                    onClick = onExportDds,
                    enabled = canModify,
                    modifier = Modifier.weight(1f).testTag("export_dds_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(lStr(R.string.res_export_dds))
                }
            }
        }
    }
}

@Composable
private fun Model3dStudioPanel(
    state: PkgStudioState,
    canModify: Boolean,
    onImport: () -> Unit,
    onLoadSample: () -> Unit,
    onExportObj: () -> Unit,
    onExportFbx: () -> Unit,
    viewModel: PkgStudioViewModel
) {
    val mesh = state.previewMesh ?: state.sourceMesh
    val tintOptions = listOf("#38BDF8", "#818CF8", "#10B981", "#F59E0B", "#F43F5E", "#F8FAFC")

    LiquidGlassCard(accentColor = IridescentIndigo) {
        SectionHeaderRow(
            title = lStr(R.string.res_mode_model),
            subtitle = mesh?.let { "${it.name} • ${it.formatLabel}" } ?: "Wavefront OBJ & Autodesk FBX",
            icon = Icons.Rounded.ViewInAr
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onImport,
                modifier = Modifier.weight(1f).testTag("import_3d_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(lStr(R.string.res_import_model))
            }
            OutlinedButton(
                onClick = onLoadSample,
                modifier = Modifier.testTag("load_sample_mesh_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("3D Crate")
            }
        }

        if (mesh != null) {
            Spacer(Modifier.height(12.dp))
            Mesh3DViewport(
                mesh = mesh,
                tintColor = Color(runCatching { state.meshTintHex.toColorInt() }.getOrDefault(0xFF38BDF8.toInt())),
                onOrbitDelta = { dx, dy ->
                    if (canModify) {
                        viewModel.updateMeshTransform(
                            rotY = (state.meshRotY + dx * 0.5f) % 360f,
                            rotX = (state.meshRotX + dy * 0.5f) % 360f
                        )
                    }
                }
            )
            if (mesh.fbxSummary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = mesh.fbxSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassMetricTile("Vertices", "${mesh.vertices.size}", ElectricCyan, Modifier.weight(1f))
                GlassMetricTile("Triangles", "${mesh.faces.size}", IridescentIndigo, Modifier.weight(1f))
                GlassMetricTile("UVs", "${mesh.uvs.size}", EmeraldSuccess, Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            Text("Material / Wireframe Color", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                tintOptions.forEach { hex ->
                    val c = Color(hex.toColorInt())
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canModify) { viewModel.updateMeshTransform(tintHex = hex) },
                        shape = CircleShape,
                        color = c,
                        border = BorderStroke(
                            width = if (state.meshTintHex == hex) 3.dp else 1.dp,
                            color = if (state.meshTintHex == hex) Color.White else Color.White.copy(alpha = 0.25f)
                        )
                    ) {}
                }
            }

            Spacer(Modifier.height(8.dp))
            LabeledSlider("Uniform Scale (${"%.2f".format(state.meshScaleX)}×)", state.meshScaleX, 0.2f..3.0f, canModify) { v ->
                viewModel.updateMeshTransform(scaleX = v, scaleY = v, scaleZ = v)
            }
            LabeledSlider("Rotate X (${state.meshRotX.roundToInt()}°)", state.meshRotX, -180f..180f, canModify) { v ->
                viewModel.updateMeshTransform(rotX = v)
            }
            LabeledSlider("Rotate Y (${state.meshRotY.roundToInt()}°)", state.meshRotY, -180f..180f, canModify) { v ->
                viewModel.updateMeshTransform(rotY = v)
            }
            LabeledSlider("Rotate Z (${state.meshRotZ.roundToInt()}°)", state.meshRotZ, -180f..180f, canModify) { v ->
                viewModel.updateMeshTransform(rotZ = v)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExportObj,
                    enabled = canModify,
                    modifier = Modifier.weight(1f).testTag("export_obj_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(lStr(R.string.res_export_obj))
                }
                OutlinedButton(
                    onClick = onExportFbx,
                    enabled = canModify,
                    modifier = Modifier.weight(1f).testTag("export_fbx_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(lStr(R.string.res_export_fbx))
                }
            }
        }
    }
}

@Composable
private fun Mesh3DViewport(
    mesh: Mesh3D,
    tintColor: Color,
    onOrbitDelta: (Float, Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF060B15))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onOrbitDelta(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (mesh.vertices.isEmpty()) return@Canvas
            val maxExtent = mesh.vertices.maxOfOrNull {
                maxOf(kotlin.math.abs(it.x), kotlin.math.abs(it.y), kotlin.math.abs(it.z))
            }?.coerceAtLeast(0.01f) ?: 1f

            val scale = minOf(size.width, size.height) * 0.34f / maxExtent
            val cx = size.width / 2f
            val cy = size.height / 2f
            val cameraDist = maxExtent * 4.2f

            fun project(vx: Float, vy: Float, vz: Float): Offset {
                val perspective = cameraDist / (cameraDist - vz).coerceAtLeast(0.5f)
                return Offset(cx + vx * scale * perspective, cy - vy * scale * perspective)
            }

            // Subtle floor grid
            val gridColor = Color.White.copy(alpha = 0.06f)
            for (g in -4..4) {
                val step = g * size.width * 0.1f
                drawLine(gridColor, Offset(cx + step, 0f), Offset(cx + step, size.height), 1f)
            }

            val maxFacesToDraw = minOf(mesh.faces.size, 1500)
            for (i in 0 until maxFacesToDraw) {
                val f = mesh.faces[i]
                if (f.size < 3) continue
                if (f[0] >= mesh.vertices.size || f[1] >= mesh.vertices.size || f[2] >= mesh.vertices.size) continue
                val v0 = mesh.vertices[f[0]]
                val v1 = mesh.vertices[f[1]]
                val v2 = mesh.vertices[f[2]]
                val p0 = project(v0.x, v0.y, v0.z)
                val p1 = project(v1.x, v1.y, v1.z)
                val p2 = project(v2.x, v2.y, v2.z)

                val path = Path().apply {
                    moveTo(p0.x, p0.y)
                    lineTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    close()
                }
                drawPath(path, color = tintColor.copy(alpha = 0.14f))
                drawPath(path, color = tintColor.copy(alpha = 0.85f), style = Stroke(width = 1.6f))
            }

            // Axis gizmo top-left
            drawLine(CoralError, Offset(36f, 44f), Offset(64f, 44f), 3f)
            drawLine(EmeraldSuccess, Offset(36f, 44f), Offset(36f, 18f), 3f)
            drawLine(ElectricCyan, Offset(36f, 44f), Offset(22f, 58f), 3f)
        }
        GlassStatusPill(
            text = "Drag to Orbit",
            color = tintColor,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled
        )
    }
}
