package co.edu.konradlorenz.kapp.ui.invitation

/**
 * The visitor pass form's fields and their rules, which are `POST /auth/visitor-passes/{code}/redeem`
 * in docs/api/auth.openapi.yaml (ADR 0007). Pure, so the rules are unit-tested without a screen.
 */
data class VisitorPassForm(
    val code: String = "",
    val documentType: DocumentType? = null,
    val documentNumber: String = "",
    val visitorName: String = "",
) {
    /** What is sent: upper case and without spaces, the way reception reads it out. */
    val normalisedCode: String get() = code.uppercase().filterNot(Char::isWhitespace)

    val codeError: FieldError?
        get() = when {
            normalisedCode.isEmpty() -> FieldError.CodeEmpty
            normalisedCode.any { it in CONFUSABLE } -> FieldError.CodeConfusable
            !CODE.matches(normalisedCode) -> FieldError.CodeFormat
            else -> null
        }

    val documentTypeError: FieldError?
        get() = if (documentType == null) FieldError.DocumentTypeMissing else null

    val documentNumberError: FieldError?
        get() = documentNumber.trim().let { number ->
            when {
                number.isEmpty() -> FieldError.NumberEmpty
                !number.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' } -> FieldError.NumberCharacters
                number.length < 4 -> FieldError.NumberTooShort
                number.length > 20 -> FieldError.NumberTooLong
                else -> null
            }
        }

    val visitorNameError: FieldError?
        get() = visitorName.trim().let { name ->
            when {
                name.isEmpty() -> FieldError.NameEmpty
                name.length > 120 -> FieldError.NameTooLong
                else -> null
            }
        }

    val isValid: Boolean
        get() = FormField.entries.all { errorOf(it) == null }

    fun errorOf(field: FormField): FieldError? = when (field) {
        FormField.Code -> codeError
        FormField.DocumentType -> documentTypeError
        FormField.DocumentNumber -> documentNumberError
        FormField.VisitorName -> visitorNameError
    }

    /** The error the screen shows under [field]: all of them once it was [left], before that only [FieldError.whileTyping]. */
    fun shownError(field: FormField, left: Boolean): FieldError? =
        errorOf(field)?.takeIf { left || it.whileTyping }

    private companion object {
        /**
         * `KL-V-` and two groups of four from the alphabet auth-service draws codes from
         * (VisitorPassService), which leaves out 0, O, 1 and I because they are misread aloud.
         */
        val CODE = Regex("KL-V-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}")
        const val CONFUSABLE = "0O1I"
    }
}

/** The form's four fields, in screen order. */
enum class FormField { Code, DocumentType, DocumentNumber, VisitorName }

/** `DocumentType` in the contract, in the order the picker lists them. */
enum class DocumentType { CC, CE, TI, PASAPORTE, PPT }

/**
 * Why a field is not valid yet. Each has its message in strings.xml.
 *
 * [whileTyping] errors show as soon as they appear, because nothing typed later can fix them: a
 * dot in a document number stays wrong. The others, like a code that is still incomplete, wait
 * until the person leaves the field, so the form does not complain about every half-typed value.
 */
enum class FieldError(val whileTyping: Boolean = false) {
    CodeEmpty,
    CodeConfusable(whileTyping = true),
    CodeFormat,
    DocumentTypeMissing,
    NumberEmpty,
    NumberCharacters(whileTyping = true),
    NumberTooShort,
    NumberTooLong(whileTyping = true),
    NameEmpty,
    NameTooLong(whileTyping = true),
}
