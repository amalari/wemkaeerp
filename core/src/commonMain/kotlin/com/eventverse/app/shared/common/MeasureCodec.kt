package com.eventverse.app.shared.common

import com.eventverse.app.domain.common.*
import com.eventverse.app.shared.json.*

/**
 * Exact JSON codecs for numeric and measurement primitives.
 * Avoids any floating-point string conversion on the wire.
 */
object MeasureCodec {

    fun encodeUnitOfMeasure(uom: UnitOfMeasure): JsonValue = jsonOf(uom.code)

    fun decodeUnitOfMeasure(value: JsonValue?): UnitOfMeasure {
        val code = when (value) {
            is JsonValue.Str -> value.value
            else -> null
        }
        return UnitOfMeasure.fromCode(code) ?: UnitOfMeasure.PIECE
    }

    fun encodeQuantity(qty: Quantity): JsonValue.Obj = jsonObjectOf(
        "micros" to jsonOf(qty.micros),
        "uom" to jsonOf(qty.uom.code)
    )

    fun decodeQuantity(obj: JsonValue.Obj?): Quantity {
        if (obj == null) return Quantity.zero(UnitOfMeasure.PIECE)
        val micros = obj.long("micros") ?: 0L
        val uom = UnitOfMeasure.fromCode(obj.string("uom")) ?: UnitOfMeasure.PIECE
        return Quantity(micros, uom)
    }

    fun encodeRatio(ratio: Ratio): JsonValue.Obj = jsonObjectOf(
        "numerator" to jsonOf(ratio.numerator),
        "denominator" to jsonOf(ratio.denominator)
    )

    fun decodeRatio(obj: JsonValue.Obj?): Ratio {
        if (obj == null) return Ratio.ONE
        val numerator = obj.long("numerator") ?: 0L
        val denominator = obj.long("denominator") ?: 1L
        return Ratio.of(numerator, if (denominator == 0L) 1L else denominator)
    }

    fun encodeMoney(money: Money): JsonValue.Obj = jsonObjectOf(
        "minor" to jsonOf(money.minorUnits),
        "currency" to jsonOf(money.currency.code)
    )

    fun decodeMoney(obj: JsonValue.Obj?): Money {
        if (obj == null) return Money.zero(CurrencyCode.IDR)
        val minor = obj.long("minor") ?: 0L
        val currencyCodeStr = obj.string("currency") ?: "IDR"
        val currency = runCatching { CurrencyCode.valueOf(currencyCodeStr) }.getOrNull() ?: CurrencyCode.IDR
        return Money(minor, currency)
    }

    fun encodeUnitPrice(unitPrice: UnitPrice): JsonValue.Obj = jsonObjectOf(
        "amount" to encodeMoney(unitPrice.amount),
        "per" to encodeQuantity(unitPrice.per)
    )

    fun decodeUnitPrice(obj: JsonValue.Obj?): UnitPrice {
        if (obj == null) {
            return UnitPrice(Money.zero(CurrencyCode.IDR), Quantity.of(1.0, UnitOfMeasure.PIECE))
        }
        val amount = decodeMoney(obj.obj("amount"))
        val perObj = obj.obj("per")
        val per = if (perObj != null) decodeQuantity(perObj) else Quantity.of(1.0, UnitOfMeasure.PIECE)
        val safePer = if (per.isPositive) per else Quantity.of(1.0, UnitOfMeasure.PIECE)
        return UnitPrice(amount, safePer)
    }
}
