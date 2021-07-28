package com.woocommerce.android.ui.prefs.cardreader.onboarding

import androidx.annotation.VisibleForTesting
import com.woocommerce.android.extensions.semverCompareTo
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.prefs.cardreader.onboarding.CardReaderOnboardingState.*
import com.woocommerce.android.util.CoroutineDispatchers
import com.woocommerce.android.util.WooLog
import kotlinx.coroutines.withContext
import org.wordpress.android.fluxc.model.pay.WCPaymentAccountResult
import org.wordpress.android.fluxc.persistence.WCPluginSqlUtils
import org.wordpress.android.fluxc.store.WCPayStore
import org.wordpress.android.fluxc.store.WooCommerceStore
import javax.inject.Inject

private val SUPPORTED_COUNTRIES = listOf("US")

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
const val SUPPORTED_WCPAY_VERSION = "2.5.0"

@Suppress("TooManyFunctions")
class CardReaderOnboardingChecker @Inject constructor(
    private val selectedSite: SelectedSite,
    private val wooStore: WooCommerceStore,
    private val wcPayStore: WCPayStore,
    private val dispatchers: CoroutineDispatchers
) {
    @Suppress("ReturnCount")
    suspend fun getOnboardingState(): CardReaderOnboardingState {
        val countryCode = getCountryCode()
        if (!isCountrySupported(countryCode)) return CountryNotSupported(countryCode)

        val fetchSitePluginsResult = wooStore.fetchSitePlugins(selectedSite.get())
        if (fetchSitePluginsResult.isError) return GenericError
        val pluginInfo = wooStore.getSitePlugin(selectedSite.get(), WooCommerceStore.WooPlugin.WOO_PAYMENTS)

        if (!isWCPayInstalled(pluginInfo)) return WcpayNotInstalled
        if (!isWCPayVersionSupported(requireNotNull(pluginInfo))) return WcpayUnsupportedVersion
        if (!isWCPayActivated(pluginInfo)) return WcpayNotActivated

        val paymentAccount = wcPayStore.loadAccount(selectedSite.get()).model ?: return GenericError

        if (!isWCPaySetupCompleted(paymentAccount)) return WcpaySetupNotCompleted
        if (isWCPayInTestModeWithLiveStripeAccount()) return WcpayInTestModeWithLiveStripeAccount
        if (isStripeAccountUnderReview(paymentAccount)) return StripeAccountUnderReview
        if (isStripeAccountPendingRequirements(paymentAccount)) return StripeAccountPendingRequirement
        if (isStripeAccountOverdueRequirements(paymentAccount)) return StripeAccountOverdueRequirement
        if (isStripeAccountRejected(paymentAccount)) return StripeAccountRejected
        if (isInUndefinedState(paymentAccount)) return GenericError

        return OnboardingCompleted
    }

    private suspend fun getCountryCode(): String? {
        return withContext(dispatchers.io) {
            wooStore.getStoreCountryCode(selectedSite.get()) ?: null.also {
                WooLog.e(WooLog.T.CARD_READER, "Store's country code not found.")
            }
        }
    }

    private fun isCountrySupported(countryCode: String?): Boolean {
        return countryCode?.let { storeCountryCode ->
            SUPPORTED_COUNTRIES.any { it.equals(storeCountryCode, ignoreCase = true) }
        } ?: false.also { WooLog.e(WooLog.T.CARD_READER, "Store's country code not found.") }
    }

    private fun isWCPayInstalled(pluginInfo: WCPluginSqlUtils.WCPluginModel?): Boolean = pluginInfo != null

    private fun isWCPayVersionSupported(pluginInfo: WCPluginSqlUtils.WCPluginModel): Boolean =
        (pluginInfo.version).semverCompareTo(SUPPORTED_WCPAY_VERSION) >= 0

    private fun isWCPayActivated(pluginInfo: WCPluginSqlUtils.WCPluginModel): Boolean = pluginInfo.active

    private fun isWCPaySetupCompleted(paymentAccount: WCPaymentAccountResult): Boolean =
        paymentAccount.status != WCPaymentAccountResult.WCPayAccountStatusEnum.NO_ACCOUNT

    // TODO cardreader Implement
    @Suppress("FunctionOnlyReturningConstant")
    private fun isWCPayInTestModeWithLiveStripeAccount(): Boolean = false

    private fun isStripeAccountUnderReview(paymentAccount: WCPaymentAccountResult): Boolean =
        paymentAccount.status == WCPaymentAccountResult.WCPayAccountStatusEnum.RESTRICTED &&
            !paymentAccount.hasPendingRequirements &&
            !paymentAccount.hasOverdueRequirements

    private fun isStripeAccountPendingRequirements(paymentAccount: WCPaymentAccountResult): Boolean =
        paymentAccount.status == WCPaymentAccountResult.WCPayAccountStatusEnum.RESTRICTED &&
            paymentAccount.hasPendingRequirements

    private fun isStripeAccountOverdueRequirements(paymentAccount: WCPaymentAccountResult): Boolean =
        paymentAccount.status == WCPaymentAccountResult.WCPayAccountStatusEnum.RESTRICTED &&
            paymentAccount.hasOverdueRequirements

    private fun isStripeAccountRejected(paymentAccount: WCPaymentAccountResult): Boolean =
        paymentAccount.status == WCPaymentAccountResult.WCPayAccountStatusEnum.REJECTED_FRAUD ||
            paymentAccount.status == WCPaymentAccountResult.WCPayAccountStatusEnum.REJECTED_LISTED ||
            paymentAccount.status == WCPaymentAccountResult.WCPayAccountStatusEnum.REJECTED_TERMS_OF_SERVICE ||
            paymentAccount.status == WCPaymentAccountResult.WCPayAccountStatusEnum.REJECTED_OTHER
}

private fun isInUndefinedState(paymentAccount: WCPaymentAccountResult): Boolean =
    paymentAccount.status != WCPaymentAccountResult.WCPayAccountStatusEnum.COMPLETE

sealed class CardReaderOnboardingState {
    object OnboardingCompleted : CardReaderOnboardingState()

    /**
     * Store is not located in one of the supported countries.
     */
    data class CountryNotSupported(val countryCode: String?) : CardReaderOnboardingState()

    /**
     * WCPay plugin is not installed on the store.
     */
    object WcpayNotInstalled : CardReaderOnboardingState()

    /**
     * WCPay plugin is installed on the store, but the version is out-dated and doesn't contain required APIs
     * for card present payments.
     */
    object WcpayUnsupportedVersion : CardReaderOnboardingState()

    /**
     * WCPay is installed on the store but is not activated.
     */
    object WcpayNotActivated : CardReaderOnboardingState()

    /**
     * WCPay is installed and activated but requires to be setup first.
     */
    object WcpaySetupNotCompleted : CardReaderOnboardingState()

    /**
     * This is a bit special case: WCPay is set to "dev mode" but the connected Stripe account is in live mode.
     * Connecting to a reader or accepting payments is not supported in this state.
     */
    object WcpayInTestModeWithLiveStripeAccount : CardReaderOnboardingState()

    /**
     * The connected Stripe account has not been reviewed by Stripe yet. This is a temporary state and
     * the user needs to wait.
     */
    object StripeAccountUnderReview : CardReaderOnboardingState()

    /**
     * There are some pending requirements on the connected Stripe account. The merchant still has some time before the
     * deadline to fix them expires. In-person payments should work without issues.
     */
    object StripeAccountPendingRequirement : CardReaderOnboardingState()

    /**
     * There are some overdue requirements on the connected Stripe account. Connecting to a reader or accepting
     * payments is not supported in this state.
     */
    object StripeAccountOverdueRequirement : CardReaderOnboardingState()

    /**
     * The Stripe account was rejected by Stripe. This can happen for example when the account is flagged as fraudulent
     * or the merchant violates the terms of service
     */
    object StripeAccountRejected : CardReaderOnboardingState()

    /**
     * Generic error - for example, one of the requests failed.
     */
    object GenericError : CardReaderOnboardingState()

    /**
     * Internet connection is not available.
     */
    object NoConnectionError : CardReaderOnboardingState()
}