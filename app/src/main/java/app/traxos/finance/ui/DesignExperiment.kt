package app.traxos.finance.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reversible visual preference only; no ledger, security or Room schema writes. */
class DesignExperimentStore(context: Context, name: String = "traxos.design.experiment") {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val current = MutableStateFlow(preferences.getBoolean("unslop", true))
    val enabled = current.asStateFlow()

    fun setEnabled(value: Boolean) {
        preferences.edit { putBoolean("unslop", value) }
        current.value = value
    }
}

internal val LocalUnslop = staticCompositionLocalOf { false }

internal fun experimentCaption(value: String): String {
    if (value in app.traxos.finance.domain.Money.currencies || value == "TRAXOS") return value
    return value
        .lowercase(Locale.ROOT)
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
        .replace(Regex("\\bv3\\b"), "V3")
        .replace(Regex("\\bui\\b"), "UI")
        .replace(Regex("\\bsms\\b"), "SMS")
        .replace(Regex("\\bpin\\b"), "PIN")
}
