package com.doffi4.doffisecure.ui.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Encapsulates UI text that can either be a static dynamic string or a localized string resource.
 * Allows ViewModels to emit localized messages without retaining Android Context.
 */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    class StringResource(
        @param:StringRes val resId: Int,
        vararg val args: Any
    ) : UiText {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StringResource) return false
            if (resId != other.resId) return false
            return args.contentEquals(other.args)
        }

        override fun hashCode(): Int {
            var result = resId
            result = 31 * result + args.contentHashCode()
            return result
        }
    }

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> {
            val flattened: Array<out Any> = args.flatMap {
                when (it) {
                    is Array<*> -> it.filterNotNull()
                    is Collection<*> -> it.filterNotNull()
                    else -> listOf(it)
                }
            }.toTypedArray()
            stringResource(resId, *flattened)
        }
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> {
            val flattened: Array<out Any> = args.flatMap {
                when (it) {
                    is Array<*> -> it.filterNotNull()
                    is Collection<*> -> it.filterNotNull()
                    else -> listOf(it)
                }
            }.toTypedArray()
            context.getString(resId, *flattened)
        }
    }
}
