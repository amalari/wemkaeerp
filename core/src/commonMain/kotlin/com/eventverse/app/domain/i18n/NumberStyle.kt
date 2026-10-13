package com.eventverse.app.domain.i18n

enum class NumberStyle {
    INDONESIAN,
    ENGLISH;

    companion object {
        fun fromLanguageCode(languageCode: LanguageCode): NumberStyle {
            return when (languageCode.code.lowercase()) {
                "id" -> INDONESIAN
                "en" -> ENGLISH
                else -> INDONESIAN
            }
        }

        fun default(): NumberStyle = INDONESIAN
    }
}

object NumberFormatter {
    fun formatDecimal(
        value: Double,
        style: NumberStyle,
        decimals: Int = 0
    ): String {
        return when (style) {
            NumberStyle.INDONESIAN -> formatIndonesianDecimal(value, decimals)
            NumberStyle.ENGLISH -> formatEnglishDecimal(value, decimals)
        }
    }

    fun formatCurrency(
        amount: Long,
        style: NumberStyle,
        currencyCode: String = "IDR"
    ): String {
        val prefix = when (currencyCode) {
            "IDR" -> when (style) {
                NumberStyle.INDONESIAN -> "Rp"
                NumberStyle.ENGLISH -> "IDR"
            }
            "USD" -> "USD"
            "EUR" -> "EUR"
            else -> currencyCode
        }

        val formatted = when (style) {
            NumberStyle.INDONESIAN -> groupIndonesian(amount)
            NumberStyle.ENGLISH -> groupEnglish(amount)
        }

        return "$prefix $formatted"
    }

    fun formatPercent(
        value: Double,
        style: NumberStyle,
        decimals: Int = 1
    ): String {
        val decimalSeparator = when (style) {
            NumberStyle.INDONESIAN -> ","
            NumberStyle.ENGLISH -> "."
        }
        val formatted = formatDecimal(value, style, decimals)
        var numberPart = formatted
        while (numberPart.endsWith("${decimalSeparator}0") || numberPart.endsWith("${decimalSeparator}00")) {
            numberPart = if (numberPart.endsWith("${decimalSeparator}00")) {
                numberPart.dropLast(3)
            } else {
                numberPart.dropLast(2)
            }
        }
        return "$numberPart %"
    }

    private fun formatIndonesianDecimal(value: Double, decimals: Int): String {
        val decimalSeparator = ","
        val scaled = if (decimals > 0) {
            String.format("%.${decimals}f", value)
        } else {
            value.toLong().toString()
        }
        return groupIndonesianDecimal(scaled, decimalSeparator)
    }

    private fun formatEnglishDecimal(value: Double, decimals: Int): String {
        val scaled = if (decimals > 0) {
            String.format("%.${decimals}f", value)
        } else {
            value.toLong().toString()
        }
        return groupEnglishDecimal(scaled)
    }

    private fun groupIndonesian(value: Long): String {
        val digits = value.toString()
        return digits.reversed().chunked(3).joinToString(".").reversed()
    }

    private fun groupIndonesianDecimal(value: String, decimalSeparator: String): String {
        val isNegative = value.startsWith("-")
        val digitsOnly = value.removePrefix("-")
        val integerPart = digitsOnly.substringBefore(".")
        val fractionalPart = digitsOnly.substringAfter(".", "")
        val groupedInteger = integerPart.reversed().chunked(3).joinToString(".").reversed()
        val result = if (fractionalPart.isNotEmpty()) {
            "$groupedInteger$decimalSeparator$fractionalPart"
        } else {
            groupedInteger
        }
        return if (isNegative) "-$result" else result
    }

    private fun groupEnglishDecimal(value: String): String {
        val isNegative = value.startsWith("-")
        val digitsOnly = value.removePrefix("-")
        val integerPart = digitsOnly.substringBefore(".")
        val fractionalPart = digitsOnly.substringAfter(".", "")
        val groupedInteger = integerPart.reversed().chunked(3).joinToString(",").reversed()
        val result = if (fractionalPart.isNotEmpty()) {
            "$groupedInteger.$fractionalPart"
        } else {
            groupedInteger
        }
        return if (isNegative) "-$result" else result
    }

    private fun groupEnglish(value: Long): String {
        val digits = value.toString()
        return digits.reversed().chunked(3).joinToString(",").reversed()
    }
}
