package com.stripe.android.paymentsheet.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.stripe.android.core.strings.resolvableString
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodFixtures
import com.stripe.android.paymentsheet.PaymentOptionsItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PaymentOptionsItemsMapperTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val paymentMethodsFlow = MutableStateFlow<List<PaymentMethod>>(emptyList())
    private val isGooglePayReadyFlow = MutableStateFlow(false)
    private val isLinkEnabledFlow = MutableStateFlow<Boolean?>(null)

    @Test
    fun `Only emits value if required flows have emitted values`() = runTest {
        val mapper = PaymentOptionsItemsMapper(
            paymentMethods = paymentMethodsFlow,
            isGooglePayReady = isGooglePayReadyFlow,
            isLinkEnabled = isLinkEnabledFlow,
            isNotPaymentFlow = true,
            nameProvider = { it!!.resolvableString },
            isCbcEligible = { false }
        )

        mapper().test {
            assertThat(awaitItem()).isEqualTo(emptyList<PaymentOptionsItem>())

            paymentMethodsFlow.value = PaymentMethodFixtures.createCards(2)
            isGooglePayReadyFlow.value = true
            isLinkEnabledFlow.value = true

            val state = awaitItem()
            assertThat(state).hasSize(5)
            assertThat(state[0].viewType).isEqualTo(PaymentOptionsItem.ViewType.AddCard)
            assertThat(state[1].viewType).isEqualTo(PaymentOptionsItem.ViewType.GooglePay)
            assertThat(state[2].viewType).isEqualTo(PaymentOptionsItem.ViewType.Link)
            assertThat(state[3].viewType).isEqualTo(PaymentOptionsItem.ViewType.SavedPaymentMethod)
            assertThat(state[4].viewType).isEqualTo(PaymentOptionsItem.ViewType.SavedPaymentMethod)
        }
    }

    @Test
    fun `Doesn't include Google Pay and Link in payment flow`() = runTest {
        val mapper = PaymentOptionsItemsMapper(
            paymentMethods = paymentMethodsFlow,
            isGooglePayReady = isGooglePayReadyFlow,
            isLinkEnabled = isLinkEnabledFlow,
            isNotPaymentFlow = false,
            nameProvider = { it!!.resolvableString },
            isCbcEligible = { false }
        )

        mapper().test {
            assertThat(awaitItem()).isEqualTo(emptyList<PaymentOptionsItem>())

            val cards = PaymentMethodFixtures.createCards(2)
            paymentMethodsFlow.value = cards
            isGooglePayReadyFlow.value = true
            isLinkEnabledFlow.value = true

            assertThat(awaitItem()).containsNoneOf(
                PaymentOptionsItem.GooglePay,
                PaymentOptionsItem.Link,
            )
        }
    }
}
