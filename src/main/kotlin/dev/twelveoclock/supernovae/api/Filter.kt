package dev.twelveoclock.supernovae.api

import kotlin.reflect.KProperty1

data class Filter<T, R>(
    val property: KProperty1<T, R>,
    val check: Check,
    val expected: R
) {

    enum class Check {
        EQUAL,
        LESSER_THAN,
        GREATER_THAN,
        LESSER_THAN_OR_EQUALS,
        GREATER_THAN_OR_EQUALS,
    }

    companion object {

        // Equals
        fun <T, R> eq(property: KProperty1<T, R>, value: R): Filter<T, R> {
            return Filter(property, Check.EQUAL, value)
        }


        // Lesser than
        fun <T, R> lt(property: KProperty1<T, R>, value: R): Filter<T, R> {
            return Filter(property, Check.LESSER_THAN, value)
        }

        // Greater than
        fun <T, R> gt(property: KProperty1<T, R>, value: R): Filter<T, R> {
            return Filter(property, Check.GREATER_THAN, value)
        }


        // Lesser than or equals
        fun <T, R> lte(property: KProperty1<T, R>, value: R): Filter<T, R> {
            return Filter(property, Check.LESSER_THAN_OR_EQUALS, value)
        }

        // Greater than or equals
        fun <T, R> gte(property: KProperty1<T, R>, value: R): Filter<T, R> {
            return Filter(property, Check.GREATER_THAN_OR_EQUALS, value)
        }

    }

}