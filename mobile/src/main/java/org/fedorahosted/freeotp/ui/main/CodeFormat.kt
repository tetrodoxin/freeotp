package org.fedorahosted.freeotp.ui.main

/**
 * Splits a code into groups for readability, as the View-based list did: the first group size of
 * 7, 5, 4 or 3 that divides the code into at least two equal groups wins ("123 456",
 * "1234 5678"). Codes longer than 13 characters wrap onto further lines. Codes that can't be
 * split evenly are returned unchanged.
 *
 * Only for display: share and copy the raw code.
 */
fun formatCode(code: String): String {
    for (segment in intArrayOf(7, 5, 4, 3)) {
        if (code.length % segment != 0) continue
        if (code.length / segment < 2) break

        val sb = StringBuilder()
        for (i in code.indices step segment) {
            if (i > 0) sb.append(if (i % 13 > (i + segment) % 13) "\n" else " ")
            sb.append(code, i, i + segment)
        }
        return sb.toString()
    }
    return code
}
