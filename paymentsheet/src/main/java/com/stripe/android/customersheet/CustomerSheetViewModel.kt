package com.stripe.android.customersheet

import android.app.Application
import android.content.res.Resources
import androidx.activity.result.ActivityResultCaller
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.stripe.android.PaymentConfiguration
import com.stripe.android.cards.DefaultCardAccountRangeRepositoryFactory
import com.stripe.android.common.exception.stripeErrorMessage
import com.stripe.android.core.Logger
import com.stripe.android.core.injection.IS_LIVE_MODE
import com.stripe.android.core.networking.ApiRequest
import com.stripe.android.core.strings.ResolvableString
import com.stripe.android.core.strings.orEmpty
import com.stripe.android.core.strings.resolvableString
import com.stripe.android.core.utils.requireApplication
import com.stripe.android.customersheet.CustomerAdapter.PaymentOption.Companion.toPaymentOption
import com.stripe.android.customersheet.analytics.CustomerSheetEventReporter
import com.stripe.android.customersheet.injection.CustomerSheetViewModelScope
import com.stripe.android.customersheet.injection.DaggerCustomerSheetViewModelComponent
import com.stripe.android.customersheet.util.CustomerSheetHacks
import com.stripe.android.customersheet.util.isUnverifiedUSBankAccount
import com.stripe.android.lpmfoundations.luxe.SupportedPaymentMethod
import com.stripe.android.lpmfoundations.paymentmethod.PaymentMethodMetadata
import com.stripe.android.lpmfoundations.paymentmethod.UiDefinitionFactory
import com.stripe.android.model.CardBrand
import com.stripe.android.model.ConfirmSetupIntentParams
import com.stripe.android.model.ConfirmStripeIntentParams
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCode
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.model.PaymentMethodUpdateParams
import com.stripe.android.model.SetupIntent
import com.stripe.android.model.StripeIntent
import com.stripe.android.networking.StripeRepository
import com.stripe.android.payments.bankaccount.CollectBankAccountLauncher
import com.stripe.android.payments.bankaccount.navigation.CollectBankAccountResultInternal
import com.stripe.android.payments.core.analytics.ErrorReporter
import com.stripe.android.payments.financialconnections.IsFinancialConnectionsAvailable
import com.stripe.android.payments.paymentlauncher.PaymentLauncher
import com.stripe.android.payments.paymentlauncher.PaymentLauncherContract
import com.stripe.android.payments.paymentlauncher.PaymentResult
import com.stripe.android.payments.paymentlauncher.StripePaymentLauncherAssistedFactory
import com.stripe.android.payments.paymentlauncher.toInternalPaymentResultCallback
import com.stripe.android.paymentsheet.IntentConfirmationInterceptor
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.R
import com.stripe.android.paymentsheet.forms.FormArgumentsFactory
import com.stripe.android.paymentsheet.forms.FormFieldValues
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.parseAppearance
import com.stripe.android.paymentsheet.paymentdatacollection.ach.USBankAccountFormArguments
import com.stripe.android.paymentsheet.ui.EditPaymentMethodViewInteractor
import com.stripe.android.paymentsheet.ui.ModifiableEditPaymentMethodViewInteractor
import com.stripe.android.paymentsheet.ui.PaymentMethodRemovalDelayMillis
import com.stripe.android.paymentsheet.ui.PrimaryButton
import com.stripe.android.paymentsheet.ui.transformToPaymentMethodCreateParams
import com.stripe.android.paymentsheet.ui.transformToPaymentSelection
import com.stripe.android.ui.core.cbc.CardBrandChoiceEligibility
import com.stripe.android.uicore.utils.mapAsStateFlow
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext
import com.stripe.android.ui.core.R as UiCoreR

@OptIn(ExperimentalCustomerSheetApi::class)
@CustomerSheetViewModelScope
internal class CustomerSheetViewModel(
    private val application: Application, // TODO (jameswoo) remove application
    initialBackStack: @JvmSuppressWildcards List<CustomerSheetViewState>,
    private var originalPaymentSelection: PaymentSelection?,
    private val paymentConfigurationProvider: Provider<PaymentConfiguration>,
    private val customerAdapterProvider: Deferred<CustomerAdapter>,
    private val resources: Resources,
    private val configuration: CustomerSheet.Configuration,
    private val logger: Logger,
    private val stripeRepository: StripeRepository,
    private val statusBarColor: Int?,
    private val eventReporter: CustomerSheetEventReporter,
    private val workContext: CoroutineContext = Dispatchers.IO,
    @Named(IS_LIVE_MODE) private val isLiveModeProvider: () -> Boolean,
    private val paymentLauncherFactory: StripePaymentLauncherAssistedFactory,
    private val intentConfirmationInterceptor: IntentConfirmationInterceptor,
    private val customerSheetLoader: CustomerSheetLoader,
    private val isFinancialConnectionsAvailable: IsFinancialConnectionsAvailable,
    private val editInteractorFactory: ModifiableEditPaymentMethodViewInteractor.Factory,
    private val errorReporter: ErrorReporter,
) : ViewModel() {

    @Inject constructor(
        application: Application,
        initialBackStack: @JvmSuppressWildcards List<CustomerSheetViewState>,
        originalPaymentSelection: PaymentSelection?,
        paymentConfigurationProvider: Provider<PaymentConfiguration>,
        resources: Resources,
        configuration: CustomerSheet.Configuration,
        logger: Logger,
        stripeRepository: StripeRepository,
        statusBarColor: Int?,
        eventReporter: CustomerSheetEventReporter,
        workContext: CoroutineContext = Dispatchers.IO,
        @Named(IS_LIVE_MODE) isLiveModeProvider: () -> Boolean,
        paymentLauncherFactory: StripePaymentLauncherAssistedFactory,
        intentConfirmationInterceptor: IntentConfirmationInterceptor,
        customerSheetLoader: CustomerSheetLoader,
        isFinancialConnectionsAvailable: IsFinancialConnectionsAvailable,
        editInteractorFactory: ModifiableEditPaymentMethodViewInteractor.Factory,
        errorReporter: ErrorReporter,
    ) : this(
        application = application,
        initialBackStack = initialBackStack,
        originalPaymentSelection = originalPaymentSelection,
        paymentConfigurationProvider = paymentConfigurationProvider,
        customerAdapterProvider = CustomerSheetHacks.adapter,
        resources = resources,
        configuration = configuration,
        logger = logger,
        stripeRepository = stripeRepository,
        statusBarColor = statusBarColor,
        eventReporter = eventReporter,
        workContext = workContext,
        isLiveModeProvider = isLiveModeProvider,
        paymentLauncherFactory = paymentLauncherFactory,
        intentConfirmationInterceptor = intentConfirmationInterceptor,
        customerSheetLoader = customerSheetLoader,
        isFinancialConnectionsAvailable = isFinancialConnectionsAvailable,
        editInteractorFactory = editInteractorFactory,
        errorReporter = errorReporter,
    )

    private val cardAccountRangeRepositoryFactory = DefaultCardAccountRangeRepositoryFactory(application)

    private val backStack = MutableStateFlow(initialBackStack)
    val viewState: StateFlow<CustomerSheetViewState> = backStack.mapAsStateFlow { it.last() }

    private val _result = MutableStateFlow<InternalCustomerSheetResult?>(null)
    val result: StateFlow<InternalCustomerSheetResult?> = _result

    private var paymentLauncher: PaymentLauncher? = null

    private var previouslySelectedPaymentMethod: SupportedPaymentMethod? = null
    private var unconfirmedPaymentMethod: PaymentMethod? = null
    var paymentMethodMetadata: PaymentMethodMetadata? = null
    private var supportedPaymentMethods = mutableListOf<SupportedPaymentMethod>()

    init {
        configuration.appearance.parseAppearance()

        eventReporter.onInit(configuration)

        if (viewState.value is CustomerSheetViewState.Loading) {
            viewModelScope.launch(workContext) {
                loadCustomerSheetState()
            }
        }
    }

    fun handleViewAction(viewAction: CustomerSheetViewAction) {
        when (viewAction) {
            is CustomerSheetViewAction.OnDismissed -> onDismissed()
            is CustomerSheetViewAction.OnAddCardPressed -> onAddCardPressed()
            is CustomerSheetViewAction.OnCardNumberInputCompleted -> onCardNumberInputCompleted()
            is CustomerSheetViewAction.OnBackPressed -> onBackPressed()
            is CustomerSheetViewAction.OnEditPressed -> onEditPressed()
            is CustomerSheetViewAction.OnItemRemoved -> onItemRemoved(viewAction.paymentMethod)
            is CustomerSheetViewAction.OnModifyItem -> onModifyItem(viewAction.paymentMethod)
            is CustomerSheetViewAction.OnItemSelected -> onItemSelected(viewAction.selection)
            is CustomerSheetViewAction.OnPrimaryButtonPressed -> onPrimaryButtonPressed()
            is CustomerSheetViewAction.OnAddPaymentMethodItemChanged ->
                onAddPaymentMethodItemChanged(viewAction.paymentMethod)
            is CustomerSheetViewAction.OnFormFieldValuesCompleted -> {
                onFormFieldValuesCompleted(viewAction.formFieldValues)
            }
            is CustomerSheetViewAction.OnUpdateCustomButtonUIState -> {
                updateCustomButtonUIState(viewAction.callback)
            }
            is CustomerSheetViewAction.OnUpdateMandateText -> {
                updateMandateText(viewAction.mandateText, viewAction.showAbovePrimaryButton)
            }
            is CustomerSheetViewAction.OnCollectBankAccountResult -> {
                onCollectUSBankAccountResult(viewAction.bankAccountResult)
            }
            is CustomerSheetViewAction.OnConfirmUSBankAccount -> {
                onConfirmUSBankAccount(viewAction.usBankAccount)
            }
            is CustomerSheetViewAction.OnFormError -> {
                onFormError(viewAction.error)
            }
            is CustomerSheetViewAction.OnCancelClose -> {
                onCancelCloseForm()
            }
        }
    }

    /**
     * If true, the bottom sheet will be dismissed, otherwise the sheet will stay open
     */
    fun bottomSheetConfirmStateChange(): Boolean {
        val currentViewState = viewState.value
        return if (currentViewState.shouldDisplayDismissConfirmationModal(isFinancialConnectionsAvailable)) {
            updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                it.copy(
                    displayDismissConfirmationModal = true,
                )
            }
            false
        } else {
            true
        }
    }

    fun providePaymentMethodName(code: PaymentMethodCode?): ResolvableString {
        return code?.let {
            paymentMethodMetadata?.supportedPaymentMethodForCode(code)
        }?.displayName.orEmpty()
    }

    fun registerFromActivity(
        activityResultCaller: ActivityResultCaller,
        lifecycleOwner: LifecycleOwner
    ) {
        val launcher = activityResultCaller.registerForActivityResult(
            PaymentLauncherContract(),
            toInternalPaymentResultCallback(::onPaymentLauncherResult)
        )

        paymentLauncher = paymentLauncherFactory.create(
            publishableKey = { paymentConfigurationProvider.get().publishableKey },
            stripeAccountId = { paymentConfigurationProvider.get().stripeAccountId },
            statusBarColor = statusBarColor,
            hostActivityLauncher = launcher,
            includePaymentSheetAuthenticators = true,
        )

        lifecycleOwner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    launcher.unregister()
                    paymentLauncher = null
                    super.onDestroy(owner)
                }
            }
        )
    }

    private fun onPaymentLauncherResult(result: PaymentResult) {
        when (result) {
            is PaymentResult.Canceled -> {
                updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                    it.copy(
                        enabled = true,
                        isProcessing = false,
                        primaryButtonEnabled = it.formFieldValues != null,
                    )
                }
            }
            is PaymentResult.Completed -> {
                safeUpdateSelectPaymentMethodState { viewState ->
                    unconfirmedPaymentMethod?.let { method ->
                        unconfirmedPaymentMethod = null

                        val newPaymentSelection = PaymentSelection.Saved(paymentMethod = method)

                        viewState.copy(
                            savedPaymentMethods = listOf(method) + viewState.savedPaymentMethods,
                            paymentSelection = newPaymentSelection,
                            primaryButtonVisible = true,
                            primaryButtonLabel = resources.getString(
                                R.string.stripe_paymentsheet_confirm
                            ),
                            mandateText = newPaymentSelection.mandateText(
                                merchantName = configuration.merchantDisplayName,
                                isSetupFlow = false,
                            )
                        )
                    } ?: viewState
                }
                onBackPressed()
            }
            is PaymentResult.Failed -> {
                updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                    it.copy(
                        enabled = true,
                        isProcessing = false,
                        primaryButtonEnabled = it.formFieldValues != null,
                        errorMessage = result.throwable.stripeErrorMessage(),
                    )
                }
            }
        }
    }

    private suspend fun loadCustomerSheetState() {
        val result = withContext(workContext) {
            customerSheetLoader.load(
                configuration = configuration,
            )
        }

        result.fold(
            onSuccess = { state ->
                if (state.validationError != null) {
                    _result.update {
                        InternalCustomerSheetResult.Error(state.validationError)
                    }
                } else {
                    supportedPaymentMethods.clear()
                    supportedPaymentMethods.addAll(state.supportedPaymentMethods)

                    originalPaymentSelection = state.paymentSelection
                    paymentMethodMetadata = state.paymentMethodMetadata

                    transitionToInitialScreen(
                        paymentMethods = state.customerPaymentMethods,
                        paymentSelection = state.paymentSelection,
                        cbcEligibility = state.paymentMethodMetadata.cbcEligibility,
                    )
                }
            },
            onFailure = { cause ->
                _result.update {
                    InternalCustomerSheetResult.Error(exception = cause)
                }
            }
        )
    }

    private fun transitionToInitialScreen(
        paymentMethods: List<PaymentMethod>,
        paymentSelection: PaymentSelection?,
        cbcEligibility: CardBrandChoiceEligibility,
    ) {
        if (paymentMethods.isEmpty() && paymentMethodMetadata?.isGooglePayReady == false) {
            transitionToAddPaymentMethod(
                isFirstPaymentMethod = true,
                cbcEligibility = cbcEligibility,
            )
        } else {
            transition(
                to = buildDefaultSelectPaymentMethod {
                    it.copy(
                        savedPaymentMethods = paymentMethods,
                        paymentSelection = paymentSelection,
                        cbcEligibility = cbcEligibility,
                    )
                },
                reset = true
            )
        }
    }

    private fun onAddCardPressed() {
        transitionToAddPaymentMethod(isFirstPaymentMethod = false)
    }

    private fun onDismissed() {
        _result.update {
            InternalCustomerSheetResult.Canceled(originalPaymentSelection)
        }
    }

    private fun onBackPressed() {
        if (backStack.value.size == 1) {
            _result.tryEmit(
                InternalCustomerSheetResult.Canceled(originalPaymentSelection)
            )
        } else {
            backStack.update {
                it.last().eventReporterScreen?.let { screen ->
                    eventReporter.onScreenHidden(screen)
                }

                it.dropLast(1)
            }
        }
    }

    private fun onEditPressed() {
        if (viewState.value.isEditing) {
            eventReporter.onEditCompleted()
        } else {
            eventReporter.onEditTapped()
        }
        updateViewState<CustomerSheetViewState.SelectPaymentMethod> {
            val isEditing = !it.isEditing
            it.copy(
                isEditing = isEditing,
                primaryButtonVisible = !isEditing && originalPaymentSelection != it.paymentSelection,
            )
        }
    }

    private fun onAddPaymentMethodItemChanged(paymentMethod: SupportedPaymentMethod) {
        (viewState.value as? CustomerSheetViewState.AddPaymentMethod)?.let {
            if (it.paymentMethodCode == paymentMethod.code) {
                return
            }
        }

        eventReporter.onPaymentMethodSelected(paymentMethod.code)

        previouslySelectedPaymentMethod = paymentMethod

        updateViewState<CustomerSheetViewState.AddPaymentMethod> {
            it.copy(
                paymentMethodCode = paymentMethod.code,
                formArguments = FormArgumentsFactory.create(
                    paymentMethodCode = paymentMethod.code,
                    configuration = configuration,
                    merchantName = configuration.merchantDisplayName,
                    cbcEligibility = it.cbcEligibility,
                ),
                formElements = paymentMethodMetadata?.formElementsForCode(
                    code = paymentMethod.code,
                    uiDefinitionFactoryArgumentsFactory = UiDefinitionFactory.Arguments.Factory.Default(
                        cardAccountRangeRepositoryFactory = cardAccountRangeRepositoryFactory,
                    ),
                ) ?: listOf(),
                primaryButtonLabel = if (
                    paymentMethod.code == PaymentMethod.Type.USBankAccount.code &&
                    it.bankAccountResult !is CollectBankAccountResultInternal.Completed
                ) {
                    UiCoreR.string.stripe_continue_button_label.resolvableString
                } else {
                    R.string.stripe_paymentsheet_save.resolvableString
                },
                mandateText = it.draftPaymentSelection?.mandateText(
                    merchantName = configuration.merchantDisplayName,
                    isSetupFlow = true,
                ),
                primaryButtonEnabled = it.formFieldValues != null && !it.isProcessing,
            )
        }
    }

    private fun onFormFieldValuesCompleted(formFieldValues: FormFieldValues?) {
        paymentMethodMetadata?.let { paymentMethodMetadata ->
            updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                it.copy(
                    formFieldValues = formFieldValues,
                    primaryButtonEnabled = formFieldValues != null && !it.isProcessing,
                    draftPaymentSelection = formFieldValues?.transformToPaymentSelection(
                        paymentMethod = it.supportedPaymentMethods.first { spm -> spm.code == it.paymentMethodCode },
                        paymentMethodMetadata = paymentMethodMetadata
                    )
                )
            }
        }
    }

    private fun onItemRemoved(paymentMethod: PaymentMethod) {
        viewModelScope.launch(workContext) {
            val result = removePaymentMethod(paymentMethod)

            result.fold(
                onSuccess = ::removePaymentMethodFromState,
                onFailure = { _, displayMessage -> handleFailureToRemovePaymentMethod(displayMessage) }
            )
        }
    }

    private suspend fun removePaymentMethod(paymentMethod: PaymentMethod): CustomerAdapter.Result<PaymentMethod> {
        return awaitCustomerAdapter().detachPaymentMethod(
            paymentMethodId = paymentMethod.id!!,
        ).onSuccess {
            eventReporter.onRemovePaymentMethodSucceeded()
        }.onFailure { cause, _ ->
            eventReporter.onRemovePaymentMethodFailed()
            logger.error(
                msg = "Failed to detach payment method: $paymentMethod",
                t = cause,
            )
        }
    }

    private suspend fun modifyCardPaymentMethod(
        paymentMethod: PaymentMethod,
        brand: CardBrand
    ): CustomerAdapter.Result<PaymentMethod> {
        return awaitCustomerAdapter().updatePaymentMethod(
            paymentMethodId = paymentMethod.id!!,
            params = PaymentMethodUpdateParams.createCard(
                networks = PaymentMethodUpdateParams.Card.Networks(
                    preferred = brand.code
                ),
                productUsageTokens = setOf("CustomerSheet"),
            )
        ).onSuccess { updatedMethod ->
            onBackPressed()
            updatePaymentMethodInState(updatedMethod)

            eventReporter.onUpdatePaymentMethodSucceeded(
                selectedBrand = brand
            )
        }.onFailure { cause, _ ->
            eventReporter.onUpdatePaymentMethodFailed(
                selectedBrand = brand,
                error = cause
            )
        }
    }

    private fun handlePaymentMethodRemovedFromEditScreen(paymentMethod: PaymentMethod) {
        viewModelScope.launch(workContext) {
            delay(PaymentMethodRemovalDelayMillis)
            removePaymentMethodFromState(paymentMethod)
        }
    }

    private fun handleFailureToRemovePaymentMethod(
        displayMessage: String?,
    ) {
        if (viewState.value is CustomerSheetViewState.SelectPaymentMethod) {
            updateViewState<CustomerSheetViewState.SelectPaymentMethod> {
                it.copy(
                    errorMessage = displayMessage,
                    isProcessing = false,
                )
            }
        }
    }

    private fun onModifyItem(paymentMethod: PaymentMethod) {
        val currentViewState = viewState.value

        val canRemove = if (configuration.allowsRemovalOfLastSavedPaymentMethod) {
            true
        } else {
            currentViewState.savedPaymentMethods.size > 1
        }

        transition(
            to = CustomerSheetViewState.EditPaymentMethod(
                editPaymentMethodInteractor = editInteractorFactory.create(
                    initialPaymentMethod = paymentMethod,
                    eventHandler = { event ->
                        when (event) {
                            is EditPaymentMethodViewInteractor.Event.ShowBrands -> {
                                eventReporter.onShowPaymentOptionBrands(
                                    source = CustomerSheetEventReporter.CardBrandChoiceEventSource.Edit,
                                    selectedBrand = event.brand
                                )
                            }
                            is EditPaymentMethodViewInteractor.Event.HideBrands -> {
                                eventReporter.onHidePaymentOptionBrands(
                                    source = CustomerSheetEventReporter.CardBrandChoiceEventSource.Edit,
                                    selectedBrand = event.brand
                                )
                            }
                        }
                    },
                    displayName = providePaymentMethodName(paymentMethod.type?.code),
                    removeExecutor = { pm ->
                        removePaymentMethod(pm).onSuccess {
                            onBackPressed()
                            handlePaymentMethodRemovedFromEditScreen(pm)
                        }.failureOrNull()?.cause
                    },
                    updateExecutor = { method, brand ->
                        when (val result = modifyCardPaymentMethod(method, brand)) {
                            is CustomerAdapter.Result.Success -> Result.success(result.value)
                            is CustomerAdapter.Result.Failure -> Result.failure(result.cause)
                        }
                    },
                    canRemove = canRemove,
                    isLiveMode = requireNotNull(paymentMethodMetadata).stripeIntent.isLiveMode,
                ),
                isLiveMode = currentViewState.isLiveMode,
                cbcEligibility = currentViewState.cbcEligibility,
                savedPaymentMethods = currentViewState.savedPaymentMethods,
                allowsRemovalOfLastSavedPaymentMethod = configuration.allowsRemovalOfLastSavedPaymentMethod,
                // TODO(samer-stripe): Set this based on customer_session permissions
                canRemovePaymentMethods = true,
            )
        )
    }

    private fun removePaymentMethodFromState(paymentMethod: PaymentMethod) {
        val currentViewState = viewState.value
        val newSavedPaymentMethods = currentViewState.savedPaymentMethods.filter { it.id != paymentMethod.id!! }

        if (currentViewState is CustomerSheetViewState.SelectPaymentMethod) {
            updateViewState<CustomerSheetViewState.SelectPaymentMethod> { viewState ->
                val originalSelection = originalPaymentSelection

                val didRemoveCurrentSelection = viewState.paymentSelection is PaymentSelection.Saved &&
                    viewState.paymentSelection.paymentMethod.id == paymentMethod.id

                val didRemoveOriginalSelection = viewState.paymentSelection is PaymentSelection.Saved &&
                    originalSelection is PaymentSelection.Saved &&
                    viewState.paymentSelection.paymentMethod.id == originalSelection.paymentMethod.id

                if (didRemoveOriginalSelection) {
                    originalPaymentSelection = null
                }

                val updatedStateCanUpdate = canEdit(
                    viewState.allowsRemovalOfLastSavedPaymentMethod,
                    newSavedPaymentMethods,
                    viewState.cbcEligibility
                )

                viewState.copy(
                    savedPaymentMethods = newSavedPaymentMethods,
                    paymentSelection = viewState.paymentSelection.takeUnless {
                        didRemoveCurrentSelection
                    } ?: originalPaymentSelection,
                    isEditing = viewState.isEditing && updatedStateCanUpdate
                )
            }
        }

        if (newSavedPaymentMethods.isEmpty() && paymentMethodMetadata?.isGooglePayReady == false) {
            transitionToAddPaymentMethod(isFirstPaymentMethod = true)
        }
    }

    private fun updatePaymentMethodInState(updatedMethod: PaymentMethod) {
        viewModelScope.launch {
            val currentViewState = viewState.value

            val newSavedPaymentMethods = currentViewState.savedPaymentMethods.map { savedMethod ->
                val savedId = savedMethod.id
                val updatedId = updatedMethod.id

                if (updatedId != null && savedId != null && updatedId == savedId) {
                    updatedMethod
                } else {
                    savedMethod
                }
            }

            updateViewState<CustomerSheetViewState.SelectPaymentMethod> { viewState ->
                val originalSelection = originalPaymentSelection
                val currentSelection = viewState.paymentSelection

                val updatedCurrentSelection = if (
                    currentSelection is PaymentSelection.Saved &&
                    currentSelection.paymentMethod.id == updatedMethod.id
                ) {
                    currentSelection.copy(paymentMethod = updatedMethod)
                } else {
                    currentSelection
                }

                originalPaymentSelection = if (
                    currentSelection is PaymentSelection.Saved &&
                    originalSelection is PaymentSelection.Saved &&
                    currentSelection.paymentMethod.id == updatedMethod.id
                ) {
                    originalSelection.copy(paymentMethod = updatedMethod)
                } else {
                    originalSelection
                }

                viewState.copy(
                    paymentSelection = updatedCurrentSelection,
                    savedPaymentMethods = newSavedPaymentMethods
                )
            }
        }
    }

    private fun onItemSelected(paymentSelection: PaymentSelection?) {
        // TODO (jameswoo) consider clearing the error message onItemSelected, currently the only
        // error source is when the payment methods cannot be loaded
        when (paymentSelection) {
            is PaymentSelection.GooglePay, is PaymentSelection.Saved -> {
                if (viewState.value.isEditing) {
                    return
                }

                updateViewState<CustomerSheetViewState.SelectPaymentMethod> {
                    val primaryButtonVisible = originalPaymentSelection != paymentSelection
                    it.copy(
                        paymentSelection = paymentSelection,
                        primaryButtonVisible = primaryButtonVisible,
                        primaryButtonLabel = resources.getString(
                            R.string.stripe_paymentsheet_confirm
                        ),
                        mandateText = paymentSelection.mandateText(
                            merchantName = configuration.merchantDisplayName,
                            isSetupFlow = false,
                        )?.takeIf { primaryButtonVisible },
                    )
                }
            }
            else -> error("Unsupported payment selection $paymentSelection")
        }
    }

    private fun onPrimaryButtonPressed() {
        when (val currentViewState = viewState.value) {
            is CustomerSheetViewState.AddPaymentMethod -> {
                if (currentViewState.customPrimaryButtonUiState != null) {
                    currentViewState.customPrimaryButtonUiState.onClick()
                    return
                }

                updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                    it.copy(
                        isProcessing = true,
                        primaryButtonEnabled = false,
                        enabled = false,
                    )
                }
                val formFieldValues = currentViewState.formFieldValues ?: error("completeFormValues cannot be null")
                val params = formFieldValues
                    .transformToPaymentMethodCreateParams(
                        paymentMethodCode = currentViewState.paymentMethodCode,
                        paymentMethodMetadata = requireNotNull(paymentMethodMetadata)
                    )
                createAndAttach(params)
            }
            is CustomerSheetViewState.SelectPaymentMethod -> {
                updateViewState<CustomerSheetViewState.SelectPaymentMethod> {
                    it.copy(isProcessing = true)
                }
                when (val paymentSelection = currentViewState.paymentSelection) {
                    is PaymentSelection.GooglePay -> selectGooglePay()
                    is PaymentSelection.Saved -> selectSavedPaymentMethod(paymentSelection)
                    null -> selectSavedPaymentMethod(null)
                    else -> error("$paymentSelection is not supported")
                }
            }
            else -> error("${viewState.value} is not supported")
        }
    }

    private fun createAndAttach(
        paymentMethodCreateParams: PaymentMethodCreateParams,
    ) {
        viewModelScope.launch(workContext) {
            createPaymentMethod(paymentMethodCreateParams)
                .onSuccess { paymentMethod ->
                    if (paymentMethod.isUnverifiedUSBankAccount()) {
                        _result.tryEmit(
                            InternalCustomerSheetResult.Selected(
                                paymentSelection = PaymentSelection.Saved(paymentMethod)
                            )
                        )
                    } else {
                        attachPaymentMethodToCustomer(paymentMethod)
                    }
                }.onFailure { throwable ->
                    logger.error(
                        msg = "Failed to create payment method for ${paymentMethodCreateParams.typeCode}",
                        t = throwable,
                    )
                    updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                        it.copy(
                            errorMessage = throwable.stripeErrorMessage(),
                            primaryButtonEnabled = it.formFieldValues != null,
                            isProcessing = false,
                        )
                    }
                }
        }
    }

    private fun transitionToAddPaymentMethod(
        isFirstPaymentMethod: Boolean,
        cbcEligibility: CardBrandChoiceEligibility = viewState.value.cbcEligibility,
    ) {
        val paymentMethodCode = previouslySelectedPaymentMethod?.code
            ?: paymentMethodMetadata?.supportedPaymentMethodTypes()?.firstOrNull()
            ?: PaymentMethod.Type.Card.code

        val formArguments = FormArgumentsFactory.create(
            paymentMethodCode = paymentMethodCode,
            configuration = configuration,
            merchantName = configuration.merchantDisplayName,
            cbcEligibility = cbcEligibility,
        )

        val selectedPaymentMethod = previouslySelectedPaymentMethod
            ?: requireNotNull(paymentMethodMetadata?.supportedPaymentMethodForCode(paymentMethodCode))

        val stripeIntent = paymentMethodMetadata?.stripeIntent
        val formElements = paymentMethodMetadata?.formElementsForCode(
            code = selectedPaymentMethod.code,
            uiDefinitionFactoryArgumentsFactory = UiDefinitionFactory.Arguments.Factory.Default(
                cardAccountRangeRepositoryFactory = cardAccountRangeRepositoryFactory,
            )
        ) ?: emptyList()

        transition(
            to = CustomerSheetViewState.AddPaymentMethod(
                paymentMethodCode = paymentMethodCode,
                supportedPaymentMethods = supportedPaymentMethods,
                formFieldValues = null,
                formElements = formElements,
                formArguments = formArguments,
                usBankAccountFormArguments = USBankAccountFormArguments(
                    instantDebits = false,
                    showCheckbox = false,
                    onBehalfOf = null,
                    isCompleteFlow = false,
                    isPaymentFlow = false,
                    stripeIntentId = stripeIntent?.id,
                    clientSecret = stripeIntent?.clientSecret,
                    shippingDetails = null,
                    draftPaymentSelection = null,
                    onMandateTextChanged = { mandate, showAbove ->
                        handleViewAction(CustomerSheetViewAction.OnUpdateMandateText(mandate, showAbove))
                    },
                    onCollectBankAccountResult = {
                        handleViewAction(CustomerSheetViewAction.OnCollectBankAccountResult(it))
                    },
                    onConfirmUSBankAccount = {
                        handleViewAction(CustomerSheetViewAction.OnConfirmUSBankAccount(it))
                    },
                    onUpdatePrimaryButtonUIState = {
                        handleViewAction(CustomerSheetViewAction.OnUpdateCustomButtonUIState(it))
                    },
                    hostedSurface = CollectBankAccountLauncher.HOSTED_SURFACE_CUSTOMER_SHEET,
                    onUpdatePrimaryButtonState = { /* no-op, CustomerSheetScreen does not use PrimaryButton.State */ },
                    onError = { error ->
                        handleViewAction(CustomerSheetViewAction.OnFormError(error))
                    }
                ),
                draftPaymentSelection = null,
                enabled = true,
                isLiveMode = isLiveModeProvider(),
                isProcessing = false,
                isFirstPaymentMethod = isFirstPaymentMethod,
                primaryButtonLabel = R.string.stripe_paymentsheet_save.resolvableString,
                primaryButtonEnabled = false,
                customPrimaryButtonUiState = null,
                bankAccountResult = null,
                cbcEligibility = cbcEligibility,
                errorReporter = errorReporter,
            ),
            reset = isFirstPaymentMethod
        )
    }

    private fun updateCustomButtonUIState(callback: (PrimaryButton.UIState?) -> PrimaryButton.UIState?) {
        updateViewState<CustomerSheetViewState.AddPaymentMethod> {
            val uiState = callback(it.customPrimaryButtonUiState)
            if (uiState != null) {
                it.copy(
                    primaryButtonEnabled = uiState.enabled,
                    customPrimaryButtonUiState = uiState,
                )
            } else {
                it.copy(
                    primaryButtonEnabled = it.formFieldValues != null && !it.isProcessing,
                    customPrimaryButtonUiState = null,
                )
            }
        }
    }

    private fun updateMandateText(mandateText: ResolvableString?, showAbove: Boolean) {
        updateViewState<CustomerSheetViewState.AddPaymentMethod> {
            it.copy(
                mandateText = mandateText,
                showMandateAbovePrimaryButton = showAbove,
            )
        }
    }

    private fun onCollectUSBankAccountResult(bankAccountResult: CollectBankAccountResultInternal) {
        updateViewState<CustomerSheetViewState.AddPaymentMethod> {
            it.copy(
                bankAccountResult = bankAccountResult,
                primaryButtonLabel = if (bankAccountResult is CollectBankAccountResultInternal.Completed) {
                    R.string.stripe_paymentsheet_save.resolvableString
                } else {
                    UiCoreR.string.stripe_continue_button_label.resolvableString
                },
            )
        }
    }

    private fun onConfirmUSBankAccount(usBankAccount: PaymentSelection.New.USBankAccount) {
        createAndAttach(usBankAccount.paymentMethodCreateParams)
    }

    private fun onCardNumberInputCompleted() {
        eventReporter.onCardNumberCompleted()
    }

    private fun onFormError(error: ResolvableString?) {
        updateViewState<CustomerSheetViewState.AddPaymentMethod> {
            it.copy(
                errorMessage = error
            )
        }
    }

    private fun onCancelCloseForm() {
        updateViewState<CustomerSheetViewState.AddPaymentMethod> {
            it.copy(
                displayDismissConfirmationModal = false,
            )
        }
    }

    private suspend fun createPaymentMethod(
        createParams: PaymentMethodCreateParams
    ): Result<PaymentMethod> {
        return stripeRepository.createPaymentMethod(
            paymentMethodCreateParams = createParams,
            options = ApiRequest.Options(
                apiKey = paymentConfigurationProvider.get().publishableKey,
                stripeAccount = paymentConfigurationProvider.get().stripeAccountId,
            )
        )
    }

    private fun attachPaymentMethodToCustomer(paymentMethod: PaymentMethod) {
        viewModelScope.launch(workContext) {
            if (awaitCustomerAdapter().canCreateSetupIntents) {
                attachWithSetupIntent(paymentMethod = paymentMethod)
            } else {
                attachPaymentMethod(id = paymentMethod.id!!)
            }
        }
    }

    private suspend fun attachWithSetupIntent(paymentMethod: PaymentMethod) {
        awaitCustomerAdapter().setupIntentClientSecretForCustomerAttach()
            .mapCatching { clientSecret ->
                val intent = stripeRepository.retrieveSetupIntent(
                    clientSecret = clientSecret,
                    options = ApiRequest.Options(
                        apiKey = paymentConfigurationProvider.get().publishableKey,
                        stripeAccount = paymentConfigurationProvider.get().stripeAccountId,
                    ),
                ).getOrThrow()

                handleStripeIntent(intent, clientSecret, paymentMethod).getOrThrow()

                eventReporter.onAttachPaymentMethodSucceeded(
                    style = CustomerSheetEventReporter.AddPaymentMethodStyle.SetupIntent
                )
            }.onFailure { cause, displayMessage ->
                eventReporter.onAttachPaymentMethodFailed(
                    style = CustomerSheetEventReporter.AddPaymentMethodStyle.SetupIntent
                )
                logger.error(
                    msg = "Failed to attach payment method to SetupIntent: $paymentMethod",
                    t = cause,
                )
                updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                    it.copy(
                        errorMessage = displayMessage?.resolvableString ?: cause.stripeErrorMessage(),
                        enabled = true,
                        primaryButtonEnabled = it.formFieldValues != null && !it.isProcessing,
                        isProcessing = false,
                    )
                }
            }
    }

    private suspend fun handleStripeIntent(
        stripeIntent: StripeIntent,
        clientSecret: String,
        paymentMethod: PaymentMethod
    ): Result<Unit> {
        val nextStep = intentConfirmationInterceptor.intercept(
            initializationMode = PaymentSheet.InitializationMode.SetupIntent(
                clientSecret = clientSecret,
            ),
            paymentMethod = paymentMethod,
            paymentMethodOptionsParams = null,
            shippingValues = null,
        )

        unconfirmedPaymentMethod = paymentMethod

        return when (nextStep) {
            is IntentConfirmationInterceptor.NextStep.Complete -> {
                safeUpdateSelectPaymentMethodState { viewState ->
                    unconfirmedPaymentMethod?.let { method ->
                        unconfirmedPaymentMethod = null

                        viewState.copy(
                            savedPaymentMethods = listOf(method) + viewState.savedPaymentMethods,
                            paymentSelection = PaymentSelection.Saved(paymentMethod = method),
                            primaryButtonVisible = true,
                            primaryButtonLabel = resources.getString(
                                R.string.stripe_paymentsheet_confirm
                            ),
                        )
                    } ?: viewState
                }
                onBackPressed()
                Result.success(Unit)
            }
            is IntentConfirmationInterceptor.NextStep.Confirm -> {
                confirmStripeIntent(nextStep.confirmParams)
                Result.success(Unit)
            }
            is IntentConfirmationInterceptor.NextStep.Fail -> {
                updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                    it.copy(
                        isProcessing = false,
                        primaryButtonEnabled = it.formFieldValues != null,
                        errorMessage = nextStep.message,
                    )
                }
                Result.failure(nextStep.cause)
            }
            is IntentConfirmationInterceptor.NextStep.HandleNextAction -> {
                handleNextAction(
                    clientSecret = nextStep.clientSecret,
                    stripeIntent = stripeIntent
                )
                Result.success(Unit)
            }
        }
    }

    private fun confirmStripeIntent(confirmStripeIntentParams: ConfirmStripeIntentParams) {
        runCatching {
            requireNotNull(paymentLauncher)
        }.fold(
            onSuccess = {
                when (confirmStripeIntentParams) {
                    is ConfirmSetupIntentParams -> {
                        it.confirm(confirmStripeIntentParams)
                    }
                    else -> error("Only SetupIntents are supported at this time")
                }
            },
            onFailure = { throwable ->
                updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                    it.copy(
                        isProcessing = false,
                        primaryButtonEnabled = it.formFieldValues != null,
                        errorMessage = throwable.stripeErrorMessage(),
                    )
                }
            }
        )
    }

    private fun handleNextAction(
        clientSecret: String,
        stripeIntent: StripeIntent,
    ) {
        runCatching {
            requireNotNull(paymentLauncher)
        }.fold(
            onSuccess = {
                when (stripeIntent) {
                    is SetupIntent -> {
                        it.handleNextActionForSetupIntent(clientSecret)
                    }
                    else -> error("Only SetupIntents are supported at this time")
                }
            },
            onFailure = { throwable ->
                updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                    it.copy(
                        isProcessing = false,
                        primaryButtonEnabled = it.formFieldValues != null,
                        errorMessage = throwable.stripeErrorMessage(),
                    )
                }
            }
        )
    }

    private suspend fun attachPaymentMethod(id: String) {
        awaitCustomerAdapter().attachPaymentMethod(id)
            .onSuccess { attachedPaymentMethod ->
                eventReporter.onAttachPaymentMethodSucceeded(
                    style = CustomerSheetEventReporter.AddPaymentMethodStyle.CreateAttach
                )
                safeUpdateSelectPaymentMethodState {
                    it.copy(
                        savedPaymentMethods = listOf(attachedPaymentMethod) + it.savedPaymentMethods,
                        paymentSelection = PaymentSelection.Saved(attachedPaymentMethod),
                        primaryButtonVisible = true,
                        primaryButtonLabel = resources.getString(
                            R.string.stripe_paymentsheet_confirm
                        ),
                    )
                }
                onBackPressed()
            }.onFailure { cause, displayMessage ->
                eventReporter.onAttachPaymentMethodFailed(
                    style = CustomerSheetEventReporter.AddPaymentMethodStyle.CreateAttach
                )
                logger.error(
                    msg = "Failed to attach payment method $id to customer",
                    t = cause,
                )
                updateViewState<CustomerSheetViewState.AddPaymentMethod> {
                    it.copy(
                        errorMessage = displayMessage?.resolvableString,
                        primaryButtonEnabled = it.formFieldValues != null,
                        isProcessing = false,
                    )
                }
            }
    }

    private fun selectSavedPaymentMethod(savedPaymentSelection: PaymentSelection.Saved?) {
        viewModelScope.launch(workContext) {
            awaitCustomerAdapter().setSelectedPaymentOption(
                savedPaymentSelection?.toPaymentOption()
            ).onSuccess {
                confirmPaymentSelection(
                    paymentSelection = savedPaymentSelection,
                    type = savedPaymentSelection?.paymentMethod?.type?.code,
                )
            }.onFailure { cause, displayMessage ->
                confirmPaymentSelectionError(
                    paymentSelection = savedPaymentSelection,
                    type = savedPaymentSelection?.paymentMethod?.type?.code,
                    cause = cause,
                    displayMessage = displayMessage,
                )
            }
        }
    }

    private fun selectGooglePay() {
        viewModelScope.launch(workContext) {
            awaitCustomerAdapter().setSelectedPaymentOption(CustomerAdapter.PaymentOption.GooglePay)
                .onSuccess {
                    confirmPaymentSelection(
                        paymentSelection = PaymentSelection.GooglePay,
                        type = "google_pay"
                    )
                }.onFailure { cause, displayMessage ->
                    confirmPaymentSelectionError(
                        paymentSelection = PaymentSelection.GooglePay,
                        type = "google_pay",
                        cause = cause,
                        displayMessage = displayMessage,
                    )
                }
        }
    }

    private fun confirmPaymentSelection(paymentSelection: PaymentSelection?, type: String?) {
        type?.let {
            eventReporter.onConfirmPaymentMethodSucceeded(type)
        }
        _result.tryEmit(
            InternalCustomerSheetResult.Selected(
                paymentSelection = paymentSelection,
            )
        )
    }

    private fun confirmPaymentSelectionError(
        paymentSelection: PaymentSelection?,
        type: String?,
        cause: Throwable,
        displayMessage: String?
    ) {
        type?.let {
            eventReporter.onConfirmPaymentMethodFailed(type)
        }
        logger.error(
            msg = "Failed to persist payment selection: $paymentSelection",
            t = cause,
        )
        updateViewState<CustomerSheetViewState.SelectPaymentMethod> {
            it.copy(
                errorMessage = displayMessage,
                isProcessing = false,
            )
        }
    }

    private fun safeUpdateSelectPaymentMethodState(
        update: (state: CustomerSheetViewState.SelectPaymentMethod) -> CustomerSheetViewState.SelectPaymentMethod
    ) {
        val hasSelectPaymentMethodInBackStack = backStack.value.any { viewState ->
            viewState is CustomerSheetViewState.SelectPaymentMethod
        }

        if (hasSelectPaymentMethodInBackStack) {
            updateViewState<CustomerSheetViewState.SelectPaymentMethod> { state ->
                update(state)
            }
        } else {
            backStack.update { currentStack ->
                listOf(buildDefaultSelectPaymentMethod(update)) + currentStack
            }
        }
    }

    private fun buildDefaultSelectPaymentMethod(
        override: (viewState: CustomerSheetViewState.SelectPaymentMethod) -> CustomerSheetViewState.SelectPaymentMethod
    ): CustomerSheetViewState.SelectPaymentMethod {
        return override(
            CustomerSheetViewState.SelectPaymentMethod(
                title = configuration.headerTextForSelectionScreen,
                savedPaymentMethods = emptyList(),
                paymentSelection = null,
                isLiveMode = isLiveModeProvider(),
                isProcessing = false,
                isEditing = false,
                isGooglePayEnabled = paymentMethodMetadata?.isGooglePayReady == true,
                primaryButtonVisible = false,
                primaryButtonLabel = resources.getString(R.string.stripe_paymentsheet_confirm),
                errorMessage = null,
                cbcEligibility = paymentMethodMetadata?.cbcEligibility ?: CardBrandChoiceEligibility.Ineligible,
                allowsRemovalOfLastSavedPaymentMethod = configuration.allowsRemovalOfLastSavedPaymentMethod,
                // TODO(samer-stripe): Set this based on customer_session permissions
                canRemovePaymentMethods = true,
            )
        )
    }

    private fun transition(to: CustomerSheetViewState, reset: Boolean = false) {
        when (to) {
            is CustomerSheetViewState.AddPaymentMethod ->
                eventReporter.onScreenPresented(CustomerSheetEventReporter.Screen.AddPaymentMethod)
            is CustomerSheetViewState.SelectPaymentMethod ->
                eventReporter.onScreenPresented(CustomerSheetEventReporter.Screen.SelectPaymentMethod)
            is CustomerSheetViewState.EditPaymentMethod ->
                eventReporter.onScreenPresented(CustomerSheetEventReporter.Screen.EditPaymentMethod)
            else -> { }
        }

        backStack.update {
            if (reset) listOf(to) else it + to
        }
    }

    private inline fun <reified T : CustomerSheetViewState> updateViewState(transform: (T) -> T) {
        backStack.update { currentBackStack ->
            currentBackStack.map {
                if (it is T) {
                    transform(it)
                } else {
                    it
                }
            }
        }
    }

    private suspend fun awaitCustomerAdapter(): CustomerAdapter {
        return customerAdapterProvider.await()
    }

    private val CustomerSheetViewState.eventReporterScreen: CustomerSheetEventReporter.Screen?
        get() = when (this) {
            is CustomerSheetViewState.AddPaymentMethod -> CustomerSheetEventReporter.Screen.AddPaymentMethod
            is CustomerSheetViewState.SelectPaymentMethod -> CustomerSheetEventReporter.Screen.SelectPaymentMethod
            is CustomerSheetViewState.EditPaymentMethod -> CustomerSheetEventReporter.Screen.EditPaymentMethod
            else -> null
        }

    class Factory(
        private val args: CustomerSheetContract.Args,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val component = DaggerCustomerSheetViewModelComponent.builder()
                .application(extras.requireApplication())
                .configuration(args.configuration)
                .statusBarColor(args.statusBarColor)
                .build()

            return component.viewModel as T
        }
    }
}
