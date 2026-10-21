package co.edu.konradlorenz.kapp.ui.invitation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.konradlorenz.kapp.KAppApplication
import co.edu.konradlorenz.kapp.data.network.VisitorPassRedemptionRequest
import co.edu.konradlorenz.kapp.data.visitor.Redemption
import co.edu.konradlorenz.kapp.data.visitor.VisitorPassRepository
import kotlinx.coroutines.launch

/** Invitación (issue #160): the visitor pass form, which fields were left, and how sending went. */
class InvitationViewModel(private val passes: VisitorPassRepository) : ViewModel() {

    var form by mutableStateOf(VisitorPassForm())
        private set

    /** The fields the person has left at least once: from then on they show every error. */
    var left by mutableStateOf(emptySet<FormField>())
        private set

    var sending by mutableStateOf(false)
        private set

    /** The last answer: `null` until the form is sent, and again as soon as it is edited. */
    var result by mutableStateOf<Redemption?>(null)
        private set

    fun edit(change: (VisitorPassForm) -> VisitorPassForm) {
        form = change(form)
        if (result != Redemption.Redeemed) result = null
    }

    fun leave(field: FormField) {
        left = left + field
    }

    fun errorShown(field: FormField) = form.shownError(field, field in left)

    fun submit() {
        if (!form.isValid || sending) return
        sending = true
        val sent = form
        viewModelScope.launch {
            result = passes.redeem(
                code = sent.normalisedCode,
                request = VisitorPassRedemptionRequest(
                    documentType = sent.documentType!!.name,
                    documentNumber = sent.documentNumber.trim(),
                    visitorName = sent.visitorName.trim(),
                ),
            )
            sending = false
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                InvitationViewModel((this[APPLICATION_KEY] as KAppApplication).container.visitorPasses)
            }
        }
    }
}
