package com.woocommerce.android.widgets

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import androidx.annotation.AttrRes
import androidx.lifecycle.LiveData
import com.google.android.material.textfield.TextInputLayout
import com.woocommerce.android.R
import com.woocommerce.android.databinding.ViewMaterialOutlinedCurrencyEdittextBinding
import com.woocommerce.android.extensions.isNotEqualTo
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.util.CurrencyFormatter
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.fluxc.model.WCSettingsModel.CurrencyPosition.*
import org.wordpress.android.fluxc.store.WooCommerceStore
import java.math.BigDecimal
import javax.inject.Inject

/**
 * Custom View that mimics a TextInputEditText that supports a prefix to the EditText using [CurrencyEditText].
 * This view will display a text box with a prefix. The entire view acts as a single component.
 *
 * This is a temporary view created to handle adding a prefix to the EditText. The material-components-android
 * already provides a way to add prefix to an EditText but this is currently in alpha.
 *
 * https://github.com/material-components/material-components-android/releases/tag/1.2.0-alpha01
 *
 * Once a release version is provided, we can update the [WCMaterialOutlinedEditTextView] to use a prefix
 * and deprecate this class.
 *
 */
@AndroidEntryPoint
class WCMaterialOutlinedCurrencyEditTextView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleRes: Int = R.attr.wcMaterialOutlinedCurrencyEditTextViewStyle
) : TextInputLayout(ctx, attrs, defStyleRes) {
    private val binding = ViewMaterialOutlinedCurrencyEdittextBinding.inflate(LayoutInflater.from(context), this)

    companion object {
        private const val KEY_SUPER_STATE = "WC-OUTLINED-CURRENCY-VIEW-SUPER-STATE"
    }

    @Inject lateinit var wcStore: WooCommerceStore
    @Inject lateinit var selectedSite: SelectedSite

    private val siteSettings by lazy {
        selectedSite.getIfExists()?.let {
            wcStore.getSiteSettings(it)
        }
    }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                attrs,
                R.styleable.WCMaterialOutlinedCurrencyEditTextView
            )
            try {
                isEnabled = a.getBoolean(R.styleable.WCMaterialOutlinedCurrencyEditTextView_android_enabled, true)
            } finally {
                a.recycle()
            }
        }

        siteSettings?.let { siteSettings ->
            val currencySymbol = wcStore.getSiteCurrency(selectedSite.get(), siteSettings.currencyCode)
            when (siteSettings.currencyPosition) {
                LEFT, LEFT_SPACE -> prefixText = currencySymbol
                RIGHT, RIGHT_SPACE -> suffixText = currencySymbol
            }
        }
    }

    val value: LiveData<BigDecimal>
        get() = binding.currencyEditText.value

    fun initView(
        currency: String,
        decimals: Int,
        currencyFormatter: CurrencyFormatter
    ) {
        siteSettings?.let { binding.currencyEditText.initView(it, it.currencyDecimalNumber) }
    }

    fun getText() = binding.currencyEditText.text.toString()

    fun setValue(currentValue: BigDecimal) {
        binding.currencyEditText.setValue(currentValue)
    }

    /**
     * Updates the value only if the current one is different from the supplied one.
     * Helpful when binding the value to a state in the ViewModel without losing the cursor position
     */
    fun setValueIfDifferent(newValue: BigDecimal) {
        if (newValue isNotEqualTo binding.currencyEditText.value.value) {
            setValue(newValue)
        }
    }

    fun getCurrencyEditText(): CurrencyEditText = binding.currencyEditText

    override fun onSaveInstanceState(): Parcelable? {
        val bundle = Bundle()
        binding.currencyEditText.onSaveInstanceState()?.let {
            bundle.putParcelable(KEY_SUPER_STATE, WCSavedState(super.onSaveInstanceState(), it))
        }
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val bundle = (state as? Bundle)?.getParcelable<WCSavedState>(KEY_SUPER_STATE)?.let {
            restoreViewState(it)
        } ?: state
        super.onRestoreInstanceState(bundle)
    }

    private fun restoreViewState(state: WCSavedState): Parcelable {
        binding.currencyEditText.onRestoreInstanceState(state.savedState)
        return state.superState
    }

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>?) {
        super.dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        super.dispatchThawSelfOnly(container)
    }
}
