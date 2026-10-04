package io.legado.app.utils

import android.content.Context
import androidx.fragment.app.Fragment

/** Select Vietnamese display resources while translation is enabled. */
fun Context.uiString(id: Int, vararg args: Any): String =
    if (UiTranslation.isEnabled()) UiTranslation.vietnameseString(id, *args)
    else getString(id, *args)

fun Fragment.uiString(id: Int, vararg args: Any): String = requireContext().uiString(id, *args)
