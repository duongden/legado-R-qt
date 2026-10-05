package io.legado.app.ui.config

import io.legado.app.R
import io.legado.app.utils.uiString
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityAiWorldBookManageBinding
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.importHttpClient as okHttpClient
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.ai.compose.AiWorldBookImportPayload
import io.legado.app.ui.main.ai.compose.AiWorldBookManageRoute
import io.legado.app.ui.widget.compose.showComposeTextInputDialog
import io.legado.app.utils.readText
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiWorldBookManageActivity : BaseActivity<ActivityAiWorldBookManageBinding>(
    fullScreen = false,
    imageBg = false
) {

    override val binding by viewBinding(ActivityAiWorldBookManageBinding::inflate)
    private var importPayload by mutableStateOf<AiWorldBookImportPayload?>(null)
    private var importRequestId = 0L
    private val importWorldBook = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            lifecycleScope.launch {
                kotlin.runCatching {
                    uri.readText(this@AiWorldBookManageActivity)
                }.onSuccess(::emitImportPayload)
                    .onFailure {
                        toastOnUi(it.localizedMessage ?: uiString(R.string.ai_world_file_error))
                    }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeRoot.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeRoot.setContent {
            AiWorldBookManageRoute(
                initialTargetType = intent.getStringExtra(EXTRA_TARGET_TYPE).orEmpty(),
                initialTargetKey = intent.getStringExtra(EXTRA_TARGET_KEY).orEmpty(),
                importPayload = importPayload,
                onImportConsumed = { importPayload = null },
                onRequestImportLocal = ::launchImportFile,
                onRequestImportNetwork = ::showImportUrlDialog,
                onBack = ::finish
            )
        }
    }

    private fun launchImportFile() {
        importWorldBook.launch {
            mode = HandleFileContract.FILE
            title = uiString(R.string.ai_world_import)
            allowExtensions = arrayOf("json")
        }
    }

    private fun showImportUrlDialog() {
        showComposeTextInputDialog(
            title = uiString(R.string.ai_world_import_url),
            hint = "https://...",
            onPositive = { value ->
                val url = value.trim()
                if (url.isNotEmpty()) importWorldBookFromUrl(url)
            }
        )
    }

    private fun importWorldBookFromUrl(url: String) {
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    okHttpClient.newCallResponseBody { url(url) }.use { it.string() }
                }
            }.onSuccess(::emitImportPayload)
                .onFailure {
                    toastOnUi(it.localizedMessage ?: uiString(R.string.ai_world_download_error))
                }
        }
    }

    private fun emitImportPayload(raw: String) {
        if (raw.isBlank()) {
            toastOnUi(uiString(R.string.ai_world_empty_content))
            return
        }
        importPayload = AiWorldBookImportPayload(++importRequestId, raw)
    }

    companion object {
        const val EXTRA_TARGET_TYPE = "targetType"
        const val EXTRA_TARGET_KEY = "targetKey"
    }
}
