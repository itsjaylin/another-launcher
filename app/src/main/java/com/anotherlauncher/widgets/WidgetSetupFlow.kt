package com.anotherlauncher.widgets

/** Only newly allocated bindings are owned by setup and may be released on cancellation. */
data class PendingWidget(val id: Int, val pageId: String, val replaceItemId: String? = null)

sealed interface WidgetSetupState {
    data object Idle : WidgetSetupState
    data class Binding(val widget: PendingWidget) : WidgetSetupState
    data class Configuring(val widget: PendingWidget) : WidgetSetupState
    data class Reconfiguring(val id: Int) : WidgetSetupState
}

/** Android callbacks are effects at the controller boundary; transition rules are JVM-testable. */
class WidgetSetupFlow(initial: WidgetSetupState = WidgetSetupState.Idle) {
    var state: WidgetSetupState = initial
        private set
    val idle: Boolean get() = state == WidgetSetupState.Idle
    val pending: PendingWidget? get() = when (val current = state) {
        is WidgetSetupState.Binding -> current.widget
        is WidgetSetupState.Configuring -> current.widget
        else -> null
    }

    fun begin(widget: PendingWidget): Boolean {
        if (!idle) return false
        state = WidgetSetupState.Binding(widget)
        return true
    }

    fun configure(): Boolean {
        val binding = state as? WidgetSetupState.Binding ?: return false
        state = WidgetSetupState.Configuring(binding.widget)
        return true
    }

    fun reconfigure(id: Int): Boolean {
        if (!idle) return false
        state = WidgetSetupState.Reconfiguring(id)
        return true
    }

    /** Consumes the transaction before callbacks run, preventing duplicate placement/deletion. */
    fun finish(): PendingWidget? {
        val result = pending
        state = WidgetSetupState.Idle
        return result
    }

    /** An existing widget is never returned for deletion. */
    fun cancel(): PendingWidget? = finish()
}
