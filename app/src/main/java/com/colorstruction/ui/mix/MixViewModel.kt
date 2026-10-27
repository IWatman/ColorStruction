package com.colorstruction.ui.mix

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.colorstruction.color.Rgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Default target color, a mid green. */
private const val DEFAULT_TARGET_ARGB: Int = 0xFF3D933E.toInt()

/** Key under which the target color is stored in the [SavedStateHandle]. */
private const val KEY_TARGET_ARGB = "mix_target_argb"

/**
 * Holds the mix screen state.
 *
 * The target color is persisted in the [SavedStateHandle] as an opaque ARGB integer, so it
 * survives configuration changes and process death.
 */
class MixViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val initialArgb: Int = savedStateHandle.get<Int>(KEY_TARGET_ARGB) ?: DEFAULT_TARGET_ARGB
    private val _target = MutableStateFlow(Rgb.fromArgbInt(initialArgb))

    /** Currently selected target color. */
    val target: StateFlow<Rgb> = _target.asStateFlow()

    /** Sets and persists [rgb] as the target color. */
    fun setTarget(rgb: Rgb) {
        val argb = rgb.toArgbInt()
        savedStateHandle[KEY_TARGET_ARGB] = argb
        _target.value = Rgb.fromArgbInt(argb)
    }
}
