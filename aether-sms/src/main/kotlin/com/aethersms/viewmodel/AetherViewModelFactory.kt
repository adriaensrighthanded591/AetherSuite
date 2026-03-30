package com.aethersms.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory centralisée pour tous les ViewModels d'AetherSMS.
 *
 * Avantages vs viewModel() par défaut :
 *  - Injection explicite des dépendances (testabilité)
 *  - Contrôle du cycle de vie
 *  - Possible de substituer des fakes en tests
 *
 * Usage dans un Composable :
 *   val vm: ConversationListViewModel = viewModel(factory = AetherViewModelFactory(app))
 */
class AetherViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ConversationListViewModel::class.java) ->
            ConversationListViewModel(application) as T

        modelClass.isAssignableFrom(ConversationViewModel::class.java) ->
            ConversationViewModel(application) as T

        modelClass.isAssignableFrom(ContactSearchViewModel::class.java) ->
            ContactSearchViewModel(application) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(application) as T

        else -> throw IllegalArgumentException(
            "ViewModel inconnu : ${modelClass.name}\n" +
            "Ajoutez-le dans AetherViewModelFactory."
        )
    }
}
