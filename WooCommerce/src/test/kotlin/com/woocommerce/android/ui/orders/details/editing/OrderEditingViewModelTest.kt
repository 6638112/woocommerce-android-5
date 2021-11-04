package com.woocommerce.android.ui.orders.details.editing

import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.R
import com.woocommerce.android.model.Address
import com.woocommerce.android.model.toAppModel
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.ui.orders.details.OrderDetailRepository
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.MultiLiveEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.RobolectricTestRunner
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.fluxc.store.WCOrderStore.OnOrderChanged
import org.wordpress.android.fluxc.store.WCOrderStore.OrderError
import org.wordpress.android.fluxc.store.WCOrderStore.OrderErrorType
import org.wordpress.android.fluxc.store.WCOrderStore.UpdateOrderResult.RemoteUpdateResult

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class OrderEditingViewModelTest : BaseUnitTest() {
    private lateinit var sut: OrderEditingViewModel

    private val orderEditingRepository: OrderEditingRepository = mock()
    private val orderDetailRepository: OrderDetailRepository = mock {
        on { getOrder(any()) } doReturn testOrder
    }
    private val networkStatus: NetworkStatus = mock {
        on { isConnected() } doReturn true
    }

    @Before
    fun setUp() {
        sut = OrderEditingViewModel(
            SavedStateHandle(),
            coroutinesTestRule.testDispatchers,
            orderDetailRepository,
            orderEditingRepository,
            networkStatus
        )
    }

    @Test
    fun `should emit success event if update was successful`() {
        orderEditingRepository.stub {
            onBlocking {
                updateOrderAddress(testOrder.localId, addressToUpdate.toBillingAddressModel())
            } doReturn flowOf(
                RemoteUpdateResult(
                    OnOrderChanged(0)
                )
            )
        }

        sut.apply {
            start()
            updateBillingAddress(addressToUpdate)
        }

        observeEvents { event ->
            assertThat(event).isEqualTo(OrderEditingViewModel.OrderEdited)
        }
    }

    @Test
    fun `should emit generic error event for errors other than empty billing mail error`() {
        orderEditingRepository.stub {
            onBlocking {
                updateOrderAddress(testOrder.localId, addressToUpdate.toBillingAddressModel())
            } doReturn flowOf(
                RemoteUpdateResult(
                    OnOrderChanged(0).apply {
                        error = OrderError(type = OrderErrorType.INVALID_RESPONSE)
                    }
                )
            )
        }

        sut.apply {
            start()
            updateBillingAddress(addressToUpdate)
        }

        observeEvents { event ->
            assertThat(event).isEqualTo(OrderEditingViewModel.OrderEditFailed(R.string.order_error_update_general))
        }
    }

    @Test
    fun `should emit empty mail failure if store returns empty billing mail error`() {
        orderEditingRepository.stub {
            onBlocking {
                updateOrderAddress(testOrder.localId, addressToUpdate.toBillingAddressModel())
            } doReturn flowOf(
                RemoteUpdateResult(
                    OnOrderChanged(0).apply {
                        error = OrderError(type = OrderErrorType.EMPTY_BILLING_EMAIL)
                    }
                )
            )
        }

        sut.apply {
            start()
            updateBillingAddress(addressToUpdate)
        }

        observeEvents { event ->
            assertThat(event).isEqualTo(OrderEditingViewModel.OrderEditFailed(R.string.order_error_update_empty_mail))
        }
    }

    private fun observeEvents(check: (MultiLiveEvent.Event) -> Unit) =
        sut.event.observeForever { check(it) }

    private companion object {
        val addressToUpdate = Address(
            company = "Automattic",
            firstName = "Joe",
            lastName = "Doe",
            phone = "123456789",
            country = "United States",
            state = "California",
            address1 = "Address 1",
            address2 = "",
            city = "San Francisco",
            postcode = "12345",
            email = ""
        )

        val testOrder = WCOrderModel().toAppModel()
    }
}