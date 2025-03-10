package com.stripe.android.paymentsheet.analytics

import com.google.common.truth.Truth.assertThat
import com.stripe.android.core.StripeError
import com.stripe.android.core.exception.APIException
import com.stripe.android.core.strings.resolvableString
import com.stripe.android.link.LinkPaymentDetails
import com.stripe.android.model.CardBrand
import com.stripe.android.model.PaymentDetailsFixtures
import com.stripe.android.model.PaymentMethodCreateParamsFixtures
import com.stripe.android.model.PaymentMethodFixtures
import com.stripe.android.paymentsheet.PaymentSheetFixtures
import com.stripe.android.paymentsheet.model.PaymentSelection
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Suppress("LargeClass")
class PaymentSheetEventTest {

    @Test
    fun `Init event with full config should return expected params`() {
        val event = PaymentSheetEvent.Init(
            mode = EventReporter.Mode.Complete,
            configuration = PaymentSheetFixtures.CONFIG_CUSTOMER_WITH_GOOGLEPAY,
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )

        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_complete_init_customer_googlepay"
        )

        val expectedConfig = mapOf(
            "customer" to true,
            "googlepay" to true,
            "primary_button_color" to false,
            "default_billing_details" to false,
            "allows_delayed_payment_methods" to false,
            "appearance" to mapOf(
                "colorsLight" to false,
                "colorsDark" to false,
                "corner_radius" to false,
                "border_width" to false,
                "font" to false,
                "size_scale_factor" to false,
                "primary_button" to mapOf(
                    "colorsLight" to false,
                    "colorsDark" to false,
                    "corner_radius" to false,
                    "border_width" to false,
                    "font" to false,
                ),
                "usage" to false,
            ),
            "payment_method_order" to listOf<String>(),
            "allows_payment_methods_requiring_shipping_address" to false,
            "allows_removal_of_last_saved_payment_method" to true,
            "billing_details_collection_configuration" to mapOf(
                "attach_defaults" to false,
                "name" to "Automatic",
                "email" to "Automatic",
                "phone" to "Automatic",
                "address" to "Automatic",
            ),
            "preferred_networks" to null,
            "external_payment_methods" to null,
        )

        assertThat(event.params).run {
            containsEntry("link_enabled", false)
            containsEntry("google_pay_enabled", false)
            containsEntry("is_decoupled", false)
            containsEntry("mpe_config", expectedConfig)
        }
    }

    @Test
    fun `Init event with external payment methods should return expected params`() {
        val event = PaymentSheetEvent.Init(
            mode = EventReporter.Mode.Complete,
            configuration = PaymentSheetFixtures.CONFIG_CUSTOMER_WITH_EXTERNAL_PAYMENT_METHODS,
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )

        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_complete_init_customer"
        )

        val expectedConfig = mapOf(
            "customer" to true,
            "googlepay" to false,
            "primary_button_color" to false,
            "default_billing_details" to false,
            "allows_delayed_payment_methods" to false,
            "appearance" to mapOf(
                "colorsLight" to false,
                "colorsDark" to false,
                "corner_radius" to false,
                "border_width" to false,
                "font" to false,
                "size_scale_factor" to false,
                "primary_button" to mapOf(
                    "colorsLight" to false,
                    "colorsDark" to false,
                    "corner_radius" to false,
                    "border_width" to false,
                    "font" to false,
                ),
                "usage" to false,
            ),
            "payment_method_order" to listOf<String>(),
            "allows_payment_methods_requiring_shipping_address" to false,
            "allows_removal_of_last_saved_payment_method" to true,
            "billing_details_collection_configuration" to mapOf(
                "attach_defaults" to false,
                "name" to "Automatic",
                "email" to "Automatic",
                "phone" to "Automatic",
                "address" to "Automatic",
            ),
            "preferred_networks" to null,
            "external_payment_methods" to listOf("external_paypal", "external_fawry"),
        )

        assertThat(event.params).run {
            containsEntry("link_enabled", false)
            containsEntry("google_pay_enabled", false)
            containsEntry("is_decoupled", false)
            containsEntry("mpe_config", expectedConfig)
        }
    }

    @Test
    fun `Init event with minimum config should return expected params`() {
        val event = PaymentSheetEvent.Init(
            mode = EventReporter.Mode.Complete,
            configuration = PaymentSheetFixtures.CONFIG_MINIMUM,
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )

        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_complete_init_default"
        )

        val expectedConfig = mapOf(
            "customer" to false,
            "googlepay" to false,
            "primary_button_color" to false,
            "default_billing_details" to false,
            "allows_delayed_payment_methods" to false,
            "appearance" to mapOf(
                "colorsLight" to false,
                "colorsDark" to false,
                "corner_radius" to false,
                "border_width" to false,
                "font" to false,
                "size_scale_factor" to false,
                "primary_button" to mapOf(
                    "colorsLight" to false,
                    "colorsDark" to false,
                    "corner_radius" to false,
                    "border_width" to false,
                    "font" to false,
                ),
                "usage" to false,
            ),
            "payment_method_order" to listOf<String>(),
            "allows_payment_methods_requiring_shipping_address" to false,
            "allows_removal_of_last_saved_payment_method" to true,
            "billing_details_collection_configuration" to mapOf(
                "attach_defaults" to false,
                "name" to "Automatic",
                "email" to "Automatic",
                "phone" to "Automatic",
                "address" to "Automatic",
            ),
            "preferred_networks" to null,
            "external_payment_methods" to null,
        )

        assertThat(event.params).run {
            containsEntry("link_enabled", false)
            containsEntry("google_pay_enabled", false)
            containsEntry("is_decoupled", false)
            containsEntry("mpe_config", expectedConfig)
        }
    }

    @Test
    fun `Init event with preferred networks`() {
        val event = PaymentSheetEvent.Init(
            mode = EventReporter.Mode.Complete,
            configuration = PaymentSheetFixtures.CONFIG_MINIMUM.copy(
                preferredNetworks = listOf(CardBrand.CartesBancaires, CardBrand.Visa)
            ),
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )

        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_complete_init_default"
        )

        val expectedConfig = mapOf(
            "customer" to false,
            "googlepay" to false,
            "primary_button_color" to false,
            "default_billing_details" to false,
            "allows_delayed_payment_methods" to false,
            "appearance" to mapOf(
                "colorsLight" to false,
                "colorsDark" to false,
                "corner_radius" to false,
                "border_width" to false,
                "font" to false,
                "size_scale_factor" to false,
                "primary_button" to mapOf(
                    "colorsLight" to false,
                    "colorsDark" to false,
                    "corner_radius" to false,
                    "border_width" to false,
                    "font" to false,
                ),
                "usage" to false,
            ),
            "payment_method_order" to listOf<String>(),
            "allows_payment_methods_requiring_shipping_address" to false,
            "allows_removal_of_last_saved_payment_method" to true,
            "billing_details_collection_configuration" to mapOf(
                "attach_defaults" to false,
                "name" to "Automatic",
                "email" to "Automatic",
                "phone" to "Automatic",
                "address" to "Automatic",
            ),
            "preferred_networks" to "cartes_bancaires, visa",
            "external_payment_methods" to null,
        )

        assertThat(event.params).run {
            containsEntry("link_enabled", false)
            containsEntry("google_pay_enabled", false)
            containsEntry("is_decoupled", false)
            containsEntry("mpe_config", expectedConfig)
        }
    }

    @Test
    fun `LoadSucceeded event should return expected toString()`() {
        val event = PaymentSheetEvent.LoadSucceeded(
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            duration = (5L).seconds,
            paymentSelection = null
        )

        assertThat(event.eventName).isEqualTo("mc_load_succeeded")
        assertThat(event.params).isEqualTo(
            mapOf(
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "duration" to 5f,
                "selected_lpm" to "none"
            )
        )
    }

    @Test
    fun `LoadSucceeded event should return 'google_pay' for selected lpm when saved selection is Google Pay`() {
        val event = PaymentSheetEvent.LoadSucceeded(
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            duration = (5L).seconds,
            paymentSelection = PaymentSelection.GooglePay
        )

        assertThat(event.params).containsEntry("selected_lpm", "google_pay")
    }

    @Test
    fun `LoadSucceeded event should return 'link' for selected lpm when saved selection is Link`() {
        val event = PaymentSheetEvent.LoadSucceeded(
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            duration = (5L).seconds,
            paymentSelection = PaymentSelection.Link
        )

        assertThat(event.params).containsEntry("selected_lpm", "link")
    }

    @Test
    fun `LoadSucceeded event should return id for selected lpm when saved selection is a payment method`() {
        val event = PaymentSheetEvent.LoadSucceeded(
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            duration = (5L).seconds,
            paymentSelection = PaymentSelection.Saved(
                paymentMethod = PaymentMethodFixtures.SEPA_DEBIT_PAYMENT_METHOD
            )
        )

        assertThat(event.params).containsEntry("selected_lpm", "sepa_debit")
    }

    @Test
    fun `New payment method event should return expected event`() {
        val newPMEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.New.Card(
                PaymentMethodCreateParamsFixtures.DEFAULT_CARD,
                mock(),
                mock()
            ),
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Success,
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            newPMEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_newpm_success"
        )
        assertThat(
            newPMEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "selected_lpm" to "card",
            )
        )
    }

    @Test
    fun `Saved payment method event should return expected event`() {
        val savedPMEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.Saved(PaymentMethodFixtures.CARD_PAYMENT_METHOD),
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Success,
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            savedPMEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_savedpm_success"
        )
        assertThat(
            savedPMEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "selected_lpm" to "card",
            )
        )
    }

    @Test
    fun `Google pay payment method event should return expected event`() {
        val googlePayEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.GooglePay,
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Success,
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            googlePayEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_googlepay_success"
        )
        assertThat(
            googlePayEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "selected_lpm" to "google_pay",
            )
        )
    }

    @Test
    fun `Link payment method event should return expected event`() {
        val linkEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.Link,
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Success,
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            linkEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_link_success"
        )
        assertThat(
            linkEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "selected_lpm" to "link",
                "link_context" to "wallet",
            )
        )
    }

    @Test
    fun `Inline Link payment method event should return expected event`() {
        val inlineLinkEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.New.LinkInline(
                LinkPaymentDetails.New(
                    PaymentDetailsFixtures.CONSUMER_SINGLE_PAYMENT_DETAILS.paymentDetails.first(),
                    mock(),
                    mock()
                ),
                PaymentSelection.CustomerRequestedSave.NoRequest,
            ),
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Success,
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            inlineLinkEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_link_success"
        )
        assertThat(
            inlineLinkEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `External payment method event should return expected event`() {
        val newPMEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.ExternalPaymentMethod(
                type = "external_fawry",
                billingDetails = null,
                label = "Fawry".resolvableString,
                iconResource = 0,
                lightThemeIconUrl = "some_url",
                darkThemeIconUrl = null,
            ),
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Success,
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            newPMEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_unknown_success"
        )
        assertThat(
            newPMEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "selected_lpm" to "external_fawry",
            )
        )
    }

    @Test
    fun `External payment method failure event should return expected event`() {
        val newPMEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.ExternalPaymentMethod(
                type = "external_fawry",
                billingDetails = null,
                label = "Fawry".resolvableString,
                iconResource = 0,
                lightThemeIconUrl = "some_url",
                darkThemeIconUrl = null,
            ),
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Failure(
                error = PaymentSheetConfirmationError.ExternalPaymentMethod,
            ),
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            newPMEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_unknown_failure"
        )
        assertThat(
            newPMEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "selected_lpm" to "external_fawry",
                "error_message" to "externalPaymentMethodError",
            )
        )
    }

    @Test
    fun `New payment method failure event should return expected event`() {
        val newPMEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.New.Card(
                PaymentMethodCreateParamsFixtures.DEFAULT_CARD,
                mock(),
                mock()
            ),
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Failure(
                error = PaymentSheetConfirmationError.Stripe(APIException()),
            ),
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            newPMEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_newpm_failure"
        )
        assertThat(
            newPMEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "selected_lpm" to "card",
                "error_message" to "apiError",
            )
        )
    }

    @Test
    fun `Saved payment method failure event should return expected event`() {
        val savedPMEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.Saved(PaymentMethodFixtures.CARD_PAYMENT_METHOD),
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Failure(
                error = PaymentSheetConfirmationError.Stripe(APIException()),
            ),
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            savedPMEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_savedpm_failure"
        )
        assertThat(
            savedPMEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "selected_lpm" to "card",
                "error_message" to "apiError",
            )
        )
    }

    @Test
    fun `Google pay payment method failure event should return expected event`() {
        val googlePayEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.GooglePay,
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Failure(
                error = PaymentSheetConfirmationError.Stripe(APIException()),
            ),
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            googlePayEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_googlepay_failure"
        )
        assertThat(
            googlePayEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "selected_lpm" to "google_pay",
                "error_message" to "apiError",
            )
        )
    }

    @Test
    fun `Link payment method failure event should return expected event`() {
        val linkEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.Link,
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Failure(
                error = PaymentSheetConfirmationError.Stripe(APIException()),
            ),
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            linkEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_link_failure"
        )
        assertThat(
            linkEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "selected_lpm" to "link",
                "error_message" to "apiError",
                "link_context" to "wallet",
            )
        )
    }

    @Test
    fun `Inline Link payment method failure event should return expected event`() {
        val inlineLinkEvent = PaymentSheetEvent.Payment(
            mode = EventReporter.Mode.Complete,
            paymentSelection = PaymentSelection.New.LinkInline(
                LinkPaymentDetails.New(
                    PaymentDetailsFixtures.CONSUMER_SINGLE_PAYMENT_DETAILS.paymentDetails.first(),
                    mock(),
                    mock()
                ),
                PaymentSelection.CustomerRequestedSave.NoRequest,
            ),
            duration = 1.milliseconds,
            result = PaymentSheetEvent.Payment.Result.Failure(
                error = PaymentSheetConfirmationError.Stripe(APIException()),
            ),
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            deferredIntentConfirmationType = null,
        )
        assertThat(
            inlineLinkEvent.eventName
        ).isEqualTo(
            "mc_complete_payment_link_failure"
        )
        assertThat(
            inlineLinkEvent.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "duration" to 0.001F,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "error_message" to "apiError",
            )
        )
    }

    @Test
    fun `SelectPaymentOption event should return expected toString()`() {
        val event = PaymentSheetEvent.SelectPaymentOption(
            mode = EventReporter.Mode.Custom,
            paymentSelection = PaymentSelection.GooglePay,
            currency = "usd",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_custom_paymentoption_googlepay_select"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "currency" to "usd",
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `ShowPaymentOptionForm event should return expected toString()`() {
        val event = PaymentSheetEvent.ShowPaymentOptionForm(
            code = "card",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_form_shown"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "selected_lpm" to "card",
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `PaymentOptionFormInteraction event should return expected toString()`() {
        val event = PaymentSheetEvent.PaymentOptionFormInteraction(
            code = "card",
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_form_interacted"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "selected_lpm" to "card",
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `CardNumberCompleted event should return expected toString()`() {
        val event = PaymentSheetEvent.CardNumberCompleted(
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_card_number_completed"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `ShowEditablePaymentOption event should return expected toString()`() {
        val event = PaymentSheetEvent.ShowEditablePaymentOption(
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_open_edit_screen",
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `HideEditablePaymentOption event should return expected toString()`() {
        val event = PaymentSheetEvent.HideEditablePaymentOption(
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_cancel_edit_screen",
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `ShowPaymentOptionBrands event with edit source should return expected toString()`() {
        val event = PaymentSheetEvent.ShowPaymentOptionBrands(
            selectedBrand = CardBrand.Visa,
            source = PaymentSheetEvent.ShowPaymentOptionBrands.Source.Edit,
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_open_cbc_dropdown"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "cbc_event_source" to "edit",
                "selected_card_brand" to "visa",
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `ShowPaymentOptionBrands event with add source should return expected toString()`() {
        val event = PaymentSheetEvent.ShowPaymentOptionBrands(
            selectedBrand = CardBrand.Visa,
            source = PaymentSheetEvent.ShowPaymentOptionBrands.Source.Add,
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_open_cbc_dropdown"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "cbc_event_source" to "add",
                "selected_card_brand" to "visa",
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `HidePaymentOptionBrands event with add source should return expected toString()`() {
        val event = PaymentSheetEvent.HidePaymentOptionBrands(
            selectedBrand = CardBrand.CartesBancaires,
            source = PaymentSheetEvent.HidePaymentOptionBrands.Source.Add,
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_close_cbc_dropdown"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "cbc_event_source" to "add",
                "selected_card_brand" to "cartes_bancaires",
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `HidePaymentOptionBrands event with edit source should return expected toString()`() {
        val event = PaymentSheetEvent.HidePaymentOptionBrands(
            selectedBrand = CardBrand.CartesBancaires,
            source = PaymentSheetEvent.HidePaymentOptionBrands.Source.Edit,
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_close_cbc_dropdown"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "cbc_event_source" to "edit",
                "selected_card_brand" to "cartes_bancaires",
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `UpdatePaymentOptionSucceeded event should return expected toString()`() {
        val event = PaymentSheetEvent.UpdatePaymentOptionSucceeded(
            selectedBrand = CardBrand.CartesBancaires,
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_update_card"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "selected_card_brand" to "cartes_bancaires",
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `UpdatePaymentOptionFailed event should return expected toString()`() {
        val event = PaymentSheetEvent.UpdatePaymentOptionFailed(
            selectedBrand = CardBrand.CartesBancaires,
            error = APIException(
                StripeError(type = "network_error", code = "error_123"),
                requestId = "request_123",
                message = "No network available!"
            ),
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_update_card_failed"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "selected_card_brand" to "cartes_bancaires",
                "error_message" to "No network available!",
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "analytics_value" to "apiError",
                "request_id" to "request_123",
                "error_type" to "network_error",
                "error_code" to "error_123",
            )
        )
    }

    @Test
    fun `Init event should have default params if config is all defaults`() {
        val expectedPrimaryButton = mapOf(
            "colorsLight" to false,
            "colorsDark" to false,
            "corner_radius" to false,
            "border_width" to false,
            "font" to false
        )
        val expectedAppearance = mapOf(
            "colorsLight" to false,
            "colorsDark" to false,
            "corner_radius" to false,
            "border_width" to false,
            "size_scale_factor" to false,
            "font" to false,
            "primary_button" to expectedPrimaryButton,
            "usage" to false
        )
        val expectedBillingDetailsCollection = mapOf(
            "attach_defaults" to false,
            "name" to "Automatic",
            "email" to "Automatic",
            "phone" to "Automatic",
            "address" to "Automatic",
        )
        val expectedConfigMap = mapOf(
            "customer" to false,
            "googlepay" to false,
            "primary_button_color" to false,
            "default_billing_details" to false,
            "allows_delayed_payment_methods" to false,
            "payment_method_order" to listOf<String>(),
            "allows_payment_methods_requiring_shipping_address" to false,
            "allows_removal_of_last_saved_payment_method" to true,
            "appearance" to expectedAppearance,
            "billing_details_collection_configuration" to expectedBillingDetailsCollection,
            "preferred_networks" to null,
            "external_payment_methods" to null,
        )
        assertThat(
            PaymentSheetEvent.Init(
                mode = EventReporter.Mode.Complete,
                configuration = PaymentSheetFixtures.CONFIG_MINIMUM,
                isDeferred = false,
                linkEnabled = false,
                googlePaySupported = false,
            ).params
        ).isEqualTo(
            mapOf(
                "mpe_config" to expectedConfigMap,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `Init event should should mark all optional params present if they are there`() {
        val expectedPrimaryButton = mapOf(
            "colorsLight" to true,
            "colorsDark" to true,
            "corner_radius" to true,
            "border_width" to true,
            "font" to true
        )
        val expectedAppearance = mapOf(
            "colorsLight" to true,
            "colorsDark" to true,
            "corner_radius" to true,
            "border_width" to true,
            "size_scale_factor" to true,
            "font" to true,
            "primary_button" to expectedPrimaryButton,
            "usage" to true
        )
        val expectedBillingDetailsCollection = mapOf(
            "attach_defaults" to true,
            "name" to "Always",
            "email" to "Always",
            "phone" to "Always",
            "address" to "Full",
        )
        val expectedConfigMap = mapOf(
            "customer" to true,
            "googlepay" to true,
            "primary_button_color" to true,
            "default_billing_details" to true,
            "allows_delayed_payment_methods" to true,
            "payment_method_order" to listOf("klarna", "afterpay", "card"),
            "allows_payment_methods_requiring_shipping_address" to true,
            "allows_removal_of_last_saved_payment_method" to false,
            "appearance" to expectedAppearance,
            "billing_details_collection_configuration" to expectedBillingDetailsCollection,
            "preferred_networks" to null,
            "external_payment_methods" to null,
        )
        assertThat(
            PaymentSheetEvent.Init(
                mode = EventReporter.Mode.Complete,
                configuration = PaymentSheetFixtures.CONFIG_WITH_EVERYTHING,
                isDeferred = false,
                linkEnabled = false,
                googlePaySupported = false,
            ).params
        ).isEqualTo(
            mapOf(
                "mpe_config" to expectedConfigMap,
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `PressConfirmButton event should return expected toString()`() {
        val event = PaymentSheetEvent.PressConfirmButton(
            selectedLpm = "card",
            currency = "USD",
            duration = 60.seconds,
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            linkContext = null,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_confirm_button_tapped"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "selected_lpm" to "card",
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
                "duration" to 60f,
                "currency" to "USD",
            )
        )
    }

    @Test
    fun `PressConfirmButton event should return expected toString() with null values`() {
        val event = PaymentSheetEvent.PressConfirmButton(
            selectedLpm = null,
            currency = null,
            duration = null,
            isDeferred = false,
            linkEnabled = false,
            googlePaySupported = false,
            linkContext = null,
        )
        assertThat(
            event.eventName
        ).isEqualTo(
            "mc_confirm_button_tapped"
        )
        assertThat(
            event.params
        ).isEqualTo(
            mapOf(
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }

    @Test
    fun `CannotProperlyReturnFromLinkAndLPMs event should return expected toString() with null values`() {
        val completeEvent = PaymentSheetEvent.CannotProperlyReturnFromLinkAndLPMs(
            mode = EventReporter.Mode.Complete,
        )

        assertThat(
            completeEvent.eventName
        ).isEqualTo(
            "mc_complete_cannot_return_from_link_and_lpms"
        )

        assertThat(
            completeEvent.params
        ).isEqualTo(
            mapOf(
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )

        val customEvent = PaymentSheetEvent.CannotProperlyReturnFromLinkAndLPMs(
            mode = EventReporter.Mode.Custom,
        )

        assertThat(
            customEvent.eventName
        ).isEqualTo(
            "mc_custom_cannot_return_from_link_and_lpms"
        )

        assertThat(
            customEvent.params
        ).isEqualTo(
            mapOf(
                "is_decoupled" to false,
                "link_enabled" to false,
                "google_pay_enabled" to false,
            )
        )
    }
}
