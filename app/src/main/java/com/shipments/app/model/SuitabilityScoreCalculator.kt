package com.shipments.app.model

/**
 * Calculates the suitability score (SS) for assigning a shipment to a driver.
 *
 * Rules:
 * - If the street name length is even: base SS = vowels in driver name * 1.5
 * - If the street name length is odd: base SS = consonants in driver name * 1.0
 * - If street name length and driver name length share a common factor > 1: SS * 1.5
 */
class SuitabilityScoreCalculator {

    fun calculate(shipmentAddress: String, driverName: String): Double {
        val streetName = extractStreetName(shipmentAddress)
        val streetNameLength = streetName.length
        val driverNameLength = driverName.length

        val baseSS = if (streetNameLength % 2 == 0) {
            countVowels(driverName) * 1.5
        } else {
            countConsonants(driverName).toDouble()
        }

        return if (shareCommonFactor(streetNameLength, driverNameLength)) {
            baseSS * 1.5
        } else {
            baseSS
        }
    }

    /** Strips the leading house number and trailing unit designators (Suite/Apt.). */
    internal fun extractStreetName(address: String): String {
        val parts = address.trim().split("\\s+".toRegex())
        val withoutNumber = if (parts.size > 1) parts.drop(1).joinToString(" ") else address
        return withoutNumber.replace("\\s+(Suite|Apt\\.?)\\s+\\S+$".toRegex(), "")
    }

    internal fun countVowels(name: String): Int =
        name.lowercase().count { it in "aeiou" }

    internal fun countConsonants(name: String): Int =
        name.lowercase().count { it.isLetter() && it !in "aeiou" }

    /** Returns true if a and b share any common factor greater than 1. */
    internal fun shareCommonFactor(a: Int, b: Int): Boolean =
        gcd(a, b) > 1

    private fun gcd(a: Int, b: Int): Int =
        if (b == 0) a else gcd(b, a % b)
}
