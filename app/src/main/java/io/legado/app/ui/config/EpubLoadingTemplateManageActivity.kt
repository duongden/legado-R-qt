package io.legado.app.ui.config

import io.legado.app.utils.uiString

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityThemeManageBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.EpubLoadingTemplate
import io.legado.app.help.config.EpubLoadingTemplateStore
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.compose.AppManagementMenuAction
import io.legado.app.ui.widget.compose.ComposeActionListDialog
import io.legado.app.ui.widget.compose.ComposeConfirmDialog
import io.legado.app.ui.widget.compose.LegadoComposeTheme
import io.legado.app.utils.inputStream
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EpubLoadingTemplateManageActivity : BaseActivity<ActivityThemeManageBinding>() {
    override val binding by viewBinding(ActivityThemeManageBinding::inflate)
    private val templates = mutableStateOf(EpubLoadingTemplate.builtins)
    private val selectedId = mutableStateOf(EpubLoadingTemplate.default.id)
    private val previewNight = mutableStateOf(AppConfig.isNightTheme)
    private var editingId: String? = null

    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val text = result.data?.getStringExtra("text") ?: return@registerForActivityResult
        runCatching { EpubLoadingTemplateStore.save(this, text, editingId) }
            .onSuccess { editingId = null; reload() }
            .onFailure {
                toastOnUi(it.localizedMessage ?: uiString(R.string.wrong_format))
                launchEditor(text, editingId)
            }
    }
    private val importer = registerForActivityResult(HandleFileContract()) { result ->
        val uri = result.uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val source = withContext(Dispatchers.IO) {
                    uri.inputStream(this@EpubLoadingTemplateManageActivity).getOrThrow().use { input ->
                        val limit = EpubLoadingTemplateStore.MAX_IMPORT_BYTES
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            require(output.size() + read <= limit) { "模板文件过大" }
                            output.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                        output.toString(Charsets.UTF_8.name())
                    }
                }
                EpubLoadingTemplateStore.save(this@EpubLoadingTemplateManageActivity, source)
                reload()
                toastOnUi(R.string.success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toastOnUi(e.localizedMessage ?: uiString(R.string.wrong_format))
            }
        }
    }
    private val exporter = registerForActivityResult(HandleFileContract()) { result ->
        if (result.uri != null) toastOnUi(R.string.export_success)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        editingId = savedInstanceState?.getString("editingId")
        previewNight.value = savedInstanceState?.getBoolean("previewNight") ?: AppConfig.isNightTheme
        binding.titleBar.title = uiString(R.string.epub_loading_templates)
        binding.tabBar.visibility = View.GONE
        binding.tvSummary.visibility = View.GONE
        binding.btnAdd.visibility = View.GONE
        val container = binding.recyclerView.parent as ViewGroup
        val index = container.indexOfChild(binding.recyclerView)
        val params = binding.recyclerView.layoutParams
        container.removeView(binding.recyclerView)
        container.addView(ComposeView(this).apply {
            layoutParams = params
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoComposeTheme {
                    EpubLoadingTemplateManageScreen(templates.value, selectedId.value, previewNight.value,
                        onNightChanged = { previewNight.value = it },
                        onApply = {
                            EpubLoadingTemplateStore.apply(this@EpubLoadingTemplateManageActivity, it)
                            reload()
                        },
                        onPreview = ::preview, onMoreActions = ::actions, onAdd = ::add)
                }
            }
        }, index)
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("editingId", editingId)
        outState.putBoolean("previewNight", previewNight.value)
        super.onSaveInstanceState(outState)
    }

    private fun reload() {
        templates.value = EpubLoadingTemplateStore.all(this)
        selectedId.value = EpubLoadingTemplateStore.selected(this).id
    }

    private fun preview(template: EpubLoadingTemplate) {
        startActivity<EpubLoadingTemplatePreviewActivity> {
            putExtra("templateId", template.id)
            putExtra("night", previewNight.value)
        }
    }

    private fun launchEditor(text: String, id: String?) {
        editingId = id
        editor.launch(Intent(this, CodeEditActivity::class.java).apply {
            putExtra("title", uiString(R.string.epub_loading_templates))
            putExtra("text", text)
            // The bundled JavaScript grammar also highlights JSON; no JSON grammar is registered.
            putExtra("languageName", "source.js")
        })
    }

    private fun copy(template: EpubLoadingTemplate) {
        val draft = template.copy(name = uiString(R.string.epub_loading_copy_name, template.name.take(48)))
        launchEditor(EpubLoadingTemplateStore.encode(draft), null)
    }

    private fun add() {
        showDialogFragment(ComposeActionListDialog.create(
            title = uiString(R.string.epub_loading_add),
            labels = listOf(uiString(R.string.epub_loading_copy), uiString(R.string.epub_loading_import)),
            negativeText = uiString(R.string.cancel)
        ) { index ->
            if (index == 0) copy(EpubLoadingTemplateStore.selected(this))
            else importer.launch {
                mode = HandleFileContract.FILE
                title = uiString(R.string.epub_loading_import)
                allowExtensions = arrayOf("json")
            }
        })
    }

    private fun actions(template: EpubLoadingTemplate): List<AppManagementMenuAction> = buildList {
        add(AppManagementMenuAction(uiString(R.string.epub_loading_copy)) { copy(template) })
        if (!template.builtIn) add(AppManagementMenuAction(uiString(R.string.edit)) {
            launchEditor(EpubLoadingTemplateStore.encode(template), template.id)
        })
        add(AppManagementMenuAction(uiString(R.string.epub_loading_export)) {
            exporter.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    template.name.replace(Regex("""[\\/:*?"<>|]"""), "_") + ".json",
                    EpubLoadingTemplateStore.encode(template).toByteArray(Charsets.UTF_8), "application/json")
            }
        })
        if (!template.builtIn) add(AppManagementMenuAction(uiString(R.string.delete)) {
            showDialogFragment(ComposeConfirmDialog.create(
                title = uiString(R.string.delete), message = template.name,
                positiveText = uiString(R.string.delete), negativeText = uiString(R.string.cancel),
                dangerPositive = true, onPositive = {
                    EpubLoadingTemplateStore.delete(this@EpubLoadingTemplateManageActivity, template)
                    reload()
                }
            ))
        })
    }
}
