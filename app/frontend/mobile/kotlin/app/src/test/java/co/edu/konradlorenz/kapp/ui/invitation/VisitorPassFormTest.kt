package co.edu.konradlorenz.kapp.ui.invitation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The visitor pass form's rules, as the redeem contract states them. */
class VisitorPassFormTest {

    private val valid = VisitorPassForm(
        code = "KL-V-7K2M-QX9P",
        documentType = DocumentType.CC,
        documentNumber = "1032456789",
        visitorName = "Pepita Perez",
    )

    @Test
    fun `a complete form is valid`() {
        assertTrue(valid.isValid)
    }

    @Test
    fun `the code is read in upper case and without spaces`() {
        val typed = valid.copy(code = " kl-v-7k2m-qx9p ")
        assertEquals("KL-V-7K2M-QX9P", typed.normalisedCode)
        assertNull(typed.codeError)
    }

    @Test
    fun `the code must have the pass's shape`() {
        assertEquals(FieldError.CodeEmpty, valid.copy(code = "").codeError)
        assertEquals(FieldError.CodeFormat, valid.copy(code = "KL-V-7K2M").codeError)
        assertEquals(FieldError.CodeFormat, valid.copy(code = "KL-X-7K2M-QX9P").codeError)
        assertEquals(FieldError.CodeFormat, valid.copy(code = "KLV7K2MQX9P").codeError)
    }

    @Test
    fun `zero, O, one and I are never in a code`() {
        assertEquals(FieldError.CodeConfusable, valid.copy(code = "KL-V-7K2M-QX0P").codeError)
        assertEquals(FieldError.CodeConfusable, valid.copy(code = "KL-V-7K2M-QXIP").codeError)
    }

    @Test
    fun `a document type has to be chosen`() {
        assertEquals(FieldError.DocumentTypeMissing, valid.copy(documentType = null).documentTypeError)
        assertFalse(valid.copy(documentType = null).isValid)
    }

    @Test
    fun `the document number is 4 to 20 letters and digits`() {
        assertEquals(FieldError.NumberEmpty, valid.copy(documentNumber = "  ").documentNumberError)
        assertEquals(FieldError.NumberCharacters, valid.copy(documentNumber = "1.032.456").documentNumberError)
        assertEquals(FieldError.NumberCharacters, valid.copy(documentNumber = "1032-456").documentNumberError)
        assertEquals(FieldError.NumberCharacters, valid.copy(documentNumber = "ÑA12345").documentNumberError)
        assertEquals(FieldError.NumberTooShort, valid.copy(documentNumber = "123").documentNumberError)
        assertEquals(FieldError.NumberTooLong, valid.copy(documentNumber = "1".repeat(21)).documentNumberError)
        assertNull(valid.copy(documentNumber = "AB1234").documentNumberError)
        assertNull(valid.copy(documentNumber = "1".repeat(20)).documentNumberError)
    }

    @Test
    fun `the name is required and at most 120 characters`() {
        assertEquals(FieldError.NameEmpty, valid.copy(visitorName = "   ").visitorNameError)
        assertEquals(FieldError.NameTooLong, valid.copy(visitorName = "a".repeat(121)).visitorNameError)
        assertNull(valid.copy(visitorName = "a".repeat(120)).visitorNameError)
    }

    @Test
    fun `only errors nothing later can fix show while typing`() {
        val whileTyping = FieldError.entries.filter { it.whileTyping }.toSet()
        assertEquals(
            setOf(FieldError.CodeConfusable, FieldError.NumberCharacters, FieldError.NumberTooLong, FieldError.NameTooLong),
            whileTyping,
        )
    }

    @Test
    fun `the prefix is the app's and the hyphen goes in after the first four`() {
        assertEquals("", VisitorPassForm.codeFromInput(""))
        assertEquals("KL-V-7k2", VisitorPassForm.codeFromInput("7k2"))
        assertEquals("KL-V-7K2M", VisitorPassForm.codeFromInput("7K2M"))
        assertEquals("KL-V-7K2M-Q", VisitorPassForm.codeFromInput("7K2MQ"))
        assertEquals("KL-V-7k2m-qx9p", VisitorPassForm.codeFromInput("7k2mqx9p"))
        assertNull(VisitorPassForm().copy(code = VisitorPassForm.codeFromInput("7k2mqx9p")).codeError)
    }

    @Test
    fun `a whole code pasted in, or a hyphen typed by hand, comes out the same`() {
        assertEquals("KL-V-7K2M-QX9P", VisitorPassForm.codeFromInput("KL-V-7K2M-QX9P"))
        assertEquals("KL-V-7K2M-QX9P", VisitorPassForm.codeFromInput(" kl-v-7K2M-QX9P "))
        assertEquals("KL-V-7K2M-QX9P", VisitorPassForm.codeFromInput("7K2M-QX9P"))
    }

    @Test
    fun `nothing past the eight characters`() {
        assertEquals("KL-V-7K2M-QX9P", VisitorPassForm.codeFromInput("7K2MQX9PZZ"))
    }

    @Test
    fun `the field gets back exactly what was typed, so deleting never gets stuck`() {
        listOf("", "7", "7k2m", "7k2mq", "7k2mqx9p").forEach { typed ->
            assertEquals(typed, VisitorPassForm.inputOf(VisitorPassForm.codeFromInput(typed)))
        }
    }
}
