package org.fedorahosted.freeotp.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFormatTest {
    @Test
    fun groupsLikeTheViewBasedList() {
        assertEquals("123 456", formatCode("123456"))
        assertEquals("1234 5678", formatCode("12345678"))
        assertEquals("12345 67890", formatCode("1234567890"))
        assertEquals("123 456 789", formatCode("123456789"))
        // Steam codes: 5 characters, one group.
        assertEquals("ABCDE", formatCode("ABCDE"))
        assertEquals("1234567", formatCode("1234567"))
    }

    @Test
    fun wrapsLongCodes() {
        assertEquals("1234567\n8901234", formatCode("12345678901234"))
        assertEquals("12345 67890\n12345", formatCode("123456789012345"))
    }

    @Test
    fun leavesOddLengthsAlone() {
        assertEquals("ERROR", formatCode("ERROR"))
        assertEquals("", formatCode(""))
    }
}
