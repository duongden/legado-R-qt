package io.legado.app.lib.dialogs

import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.TranslateUtils
import io.legado.app.utils.UiTranslation
import kotlinx.coroutines.CoroutineStart
import androidx.appcompat.app.AlertDialog
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.applyUiBodyTypefaceDeep
import io.legado.app.lib.theme.dialogSurfaceBackground
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.utils.applyTint

internal class AndroidAlertBuilder(override val ctx: Context) : AlertBuilder<AlertDialog> {
    private val builder = AlertDialog.Builder(ctx)

    override fun setTitle(title: CharSequence) {
        builder.setTitle(title)
    }

    override fun setTitle(titleResource: Int) {
        builder.setTitle(titleResource)
    }

    override fun setMessage(message: CharSequence) {
        builder.setMessage(message)
    }

    override fun setMessage(messageResource: Int) {
        builder.setMessage(messageResource)
    }

    override fun setIcon(icon: Drawable) {
        builder.setIcon(icon)
    }

    override fun setIcon(iconResource: Int) {
        builder.setIcon(iconResource)
    }

    override fun setCustomTitle(customTitle: View) {
        customTitle.applyUiBodyTypefaceDeep(ctx.uiTypeface())
        builder.setCustomTitle(customTitle)
    }

    override fun setCustomView(customView: View) {
        customView.applyUiBodyTypefaceDeep(ctx.uiTypeface())
        builder.setView(customView)
    }

    override fun setCancelable(isCancelable: Boolean) {
        builder.setCancelable(isCancelable)
    }

    override fun onCancelled(handler: (DialogInterface) -> Unit) {
        builder.setOnCancelListener(handler)
    }

    override fun onKeyPressed(handler: (dialog: DialogInterface, keyCode: Int, e: KeyEvent) -> Boolean) {
        builder.setOnKeyListener(handler)
    }

    override fun positiveButton(
        buttonText: String,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        builder.setPositiveButton(buttonText) { dialog, _ -> onClicked?.invoke(dialog) }
    }

    override fun positiveButton(
        buttonTextResource: Int,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        builder.setPositiveButton(buttonTextResource) { dialog, _ -> onClicked?.invoke(dialog) }
    }

    override fun negativeButton(
        buttonText: String,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        builder.setNegativeButton(buttonText) { dialog, _ -> onClicked?.invoke(dialog) }
    }

    override fun negativeButton(
        buttonTextResource: Int,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        builder.setNegativeButton(buttonTextResource) { dialog, _ -> onClicked?.invoke(dialog) }
    }

    override fun neutralButton(
        buttonText: String,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        builder.setNeutralButton(buttonText) { dialog, _ -> onClicked?.invoke(dialog) }
    }

    override fun neutralButton(
        buttonTextResource: Int,
        onClicked: ((dialog: DialogInterface) -> Unit)?
    ) {
        builder.setNeutralButton(buttonTextResource) { dialog, _ -> onClicked?.invoke(dialog) }
    }

    override fun onDismiss(handler: (dialog: DialogInterface) -> Unit) {
        builder.setOnDismissListener(handler)
    }

    override fun items(
        items: List<CharSequence>,
        onItemSelected: (dialog: DialogInterface, index: Int) -> Unit
    ) {
        builder.setItems(Array(items.size) { i -> items[i].toString() }) { dialog, which ->
            onItemSelected(dialog, which)
        }
    }

    override fun <T> items(
        items: List<T>,
        onItemSelected: (dialog: DialogInterface, item: T, index: Int) -> Unit
    ) {
        builder.setItems(Array(items.size) { i -> items[i].toString() }) { dialog, which ->
            onItemSelected(dialog, items[which], which)
        }
    }

    override fun multiChoiceItems(
        items: Array<String>,
        checkedItems: BooleanArray,
        onClick: (dialog: DialogInterface, which: Int, isChecked: Boolean) -> Unit
    ) {
        builder.setMultiChoiceItems(items, checkedItems) { dialog, which, isChecked ->
            onClick(dialog, which, isChecked)
        }
    }

    override fun singleChoiceItems(
        items: Array<String>,
        checkedItem: Int,
        onClick: ((dialog: DialogInterface, which: Int) -> Unit)?
    ) {
        builder.setSingleChoiceItems(items, checkedItem) { dialog, which ->
            onClick?.invoke(dialog, which)
        }
    }

    private fun translateLabels(dialog: AlertDialog) {
        if (!UiTranslation.isEnabled()) return
        val labels = listOfNotNull(
            dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle),
            dialog.findViewById<TextView>(android.R.id.message),
            dialog.getButton(DialogInterface.BUTTON_POSITIVE),
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE),
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
        ).map { it to it.text.toString() }
        val block: suspend kotlinx.coroutines.CoroutineScope.() -> List<String> = {
            labels.map { UiTranslation.translateMessage(it.second) }
        }
        val owner = ctx as? LifecycleOwner
        val job = if (owner != null) Coroutine.async(scope = owner.lifecycleScope, start = CoroutineStart.LAZY, block = block)
            else Coroutine.async(start = CoroutineStart.LAZY, block = block)
        job.onSuccess { translated ->
            if (dialog.isShowing && UiTranslation.isEnabled()) {
                labels.forEachIndexed { index, (view, raw) ->
                    if (view.text.toString() == raw) view.text = translated[index]
                }
            }
        }
        job.start()
    }

    override fun build(): AlertDialog {
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.window?.decorView?.applyUiBodyTypefaceDeep(ctx.uiTypeface())
            translateLabels(dialog)
        }
        dialog.window?.run {
            if (AppConfig.isEInkMode) {
                val attr = attributes
                attr.dimAmount = 0f
                attr.windowAnimations = 0
                attributes = attr
                setBackgroundDrawableResource(R.drawable.bg_eink_border_dialog)
            } else {
                val attr = attributes
                attr.windowAnimations = R.style.AnimDialogCenter
                attributes = attr
                setBackgroundDrawable(ctx.dialogSurfaceBackground)
            }
        }
        return dialog
    }

    override fun show(): AlertDialog {
        val dialog = builder.show().applyTint()
        translateLabels(dialog)
        dialog.window?.run {
            if (AppConfig.isEInkMode) {
                val attr = attributes
                attr.dimAmount = 0f
                attr.windowAnimations = 0
                attributes = attr
                setBackgroundDrawableResource(R.drawable.bg_eink_border_dialog)
            } else {
                val attr = attributes
                attr.windowAnimations = R.style.AnimDialogCenter
                attributes = attr
                setBackgroundDrawable(ctx.dialogSurfaceBackground)
            }
        }
        return dialog
    }
}
