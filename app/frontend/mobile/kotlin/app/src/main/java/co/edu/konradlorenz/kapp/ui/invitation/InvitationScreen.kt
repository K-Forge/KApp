package co.edu.konradlorenz.kapp.ui.invitation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.edu.konradlorenz.kapp.R
import co.edu.konradlorenz.kapp.data.visitor.Redemption
import co.edu.konradlorenz.kapp.ui.common.BrandBand
import co.edu.konradlorenz.kapp.ui.common.ScreenPadding

/**
 * Invitación (issue #160): a visitor redeems the day pass reception gave them. The rules are
 * VisitorPassForm's; this screen only says, under each field, what is wrong with it - and keeps
 * the button off until nothing is.
 */
@Composable
fun InvitationScreen(onBack: () -> Unit, viewModel: InvitationViewModel = viewModel(factory = InvitationViewModel.Factory)) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The purple band of the other screens, which is also what keeps the white status bar
            // icons MainActivity sets readable.
            BrandBand(title = stringResource(R.string.invitation_band))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (viewModel.result == Redemption.Redeemed) {
                    Redeemed(name = viewModel.form.visitorName.trim().substringBefore(' '), onBack = onBack)
                } else {
                    Form(viewModel, onBack)
                }
            }
        }
    }
}

@Composable
private fun Form(viewModel: InvitationViewModel, onBack: () -> Unit) {
    val form = viewModel.form
    Text(stringResource(R.string.invitation_title), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.invitation_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    stringResource(R.string.invitation_demo_hint).takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }

    FormTextField(
        value = form.code,
        onValueChange = { value -> viewModel.edit { it.copy(code = value) } },
        label = R.string.invitation_code_label,
        help = R.string.invitation_code_help,
        error = viewModel.errorShown(FormField.Code),
        onLeave = { viewModel.leave(FormField.Code) },
        keyboard = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
        ),
        display = UpperCase,
    )
    DocumentTypePicker(
        selected = form.documentType,
        onSelect = { type -> viewModel.edit { it.copy(documentType = type) } },
        error = viewModel.errorShown(FormField.DocumentType),
        onLeave = { viewModel.leave(FormField.DocumentType) },
    )
    FormTextField(
        value = form.documentNumber,
        onValueChange = { value -> viewModel.edit { it.copy(documentNumber = value) } },
        label = R.string.invitation_number_label,
        help = R.string.invitation_number_help,
        error = viewModel.errorShown(FormField.DocumentNumber),
        onLeave = { viewModel.leave(FormField.DocumentNumber) },
        keyboard = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
    )
    FormTextField(
        value = form.visitorName,
        onValueChange = { value -> viewModel.edit { it.copy(visitorName = value) } },
        label = R.string.invitation_name_label,
        help = null,
        error = viewModel.errorShown(FormField.VisitorName),
        onLeave = { viewModel.leave(FormField.VisitorName) },
        keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
    )

    Text(
        stringResource(R.string.invitation_privacy),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = viewModel::submit,
        enabled = form.isValid && !viewModel.sending,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(stringResource(if (viewModel.sending) R.string.invitation_sending else R.string.invitation_submit))
    }
    viewModel.result?.let { result ->
        Text(
            stringResource(messageOf(result)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    TextButton(onClick = onBack) { Text(stringResource(R.string.invitation_back)) }
}

/** A text field that reports when the person leaves it, and shows its error or its help below. */
@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes help: Int?,
    error: FieldError?,
    onLeave: () -> Unit,
    keyboard: KeyboardOptions,
    display: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = (error?.let { messageOf(it) } ?: help)?.let { { Text(stringResource(it)) } },
        singleLine = true,
        keyboardOptions = keyboard,
        visualTransformation = display,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (focused && !it.isFocused) onLeave()
                focused = it.isFocused
            },
    )
}

/**
 * Shows the code in upper case, the way reception reads it out, without touching what was typed:
 * rewriting the value itself while the keyboard is still composing a word loses characters.
 * VisitorPassForm upper-cases it before checking and sending.
 */
private val UpperCase = VisualTransformation { TransformedText(AnnotatedString(it.text.uppercase()), OffsetMapping.Identity) }

/** The five documents the contract accepts, as a menu: nothing to type, so nothing to mistype. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentTypePicker(
    selected: DocumentType?,
    onSelect: (DocumentType) -> Unit,
    error: FieldError?,
    onLeave: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { stringResource(labelOf(it)) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.invitation_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = error != null,
            supportingText = error?.let { { Text(stringResource(messageOf(it))) } },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onLeave()
            },
        ) {
            DocumentType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelOf(type))) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                        onLeave()
                    },
                )
            }
        }
    }
}

@Composable
private fun Redeemed(name: String, onBack: () -> Unit) {
    Text(stringResource(R.string.invitation_done_title, name), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.invitation_done_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
        Text(stringResource(R.string.invitation_done_back))
    }
}

@StringRes
private fun labelOf(type: DocumentType): Int = when (type) {
    DocumentType.CC -> R.string.invitation_type_cc
    DocumentType.CE -> R.string.invitation_type_ce
    DocumentType.TI -> R.string.invitation_type_ti
    DocumentType.PASAPORTE -> R.string.invitation_type_pasaporte
    DocumentType.PPT -> R.string.invitation_type_ppt
}

@StringRes
private fun messageOf(error: FieldError): Int = when (error) {
    FieldError.CodeEmpty -> R.string.invitation_error_code_empty
    FieldError.CodeConfusable -> R.string.invitation_error_code_confusable
    FieldError.CodeFormat -> R.string.invitation_error_code_format
    FieldError.DocumentTypeMissing -> R.string.invitation_error_type_missing
    FieldError.NumberEmpty -> R.string.invitation_error_number_empty
    FieldError.NumberCharacters -> R.string.invitation_error_number_characters
    FieldError.NumberTooShort -> R.string.invitation_error_number_short
    FieldError.NumberTooLong -> R.string.invitation_error_number_long
    FieldError.NameEmpty -> R.string.invitation_error_name_empty
    FieldError.NameTooLong -> R.string.invitation_error_name_long
}

@StringRes
private fun messageOf(result: Redemption): Int = when (result) {
    Redemption.NotFound -> R.string.invitation_result_not_found
    Redemption.AlreadyUsed -> R.string.invitation_result_used
    Redemption.Expired -> R.string.invitation_result_expired
    Redemption.Offline -> R.string.invitation_result_offline
    Redemption.Redeemed, Redemption.Failed -> R.string.invitation_result_failed
}
