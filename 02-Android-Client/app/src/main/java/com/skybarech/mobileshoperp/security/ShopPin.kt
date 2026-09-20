package com.skybarech.mobileshoperp.security

/** PINs stay strings: leading zeroes are part of the credential. */
object ShopPin {
    const val DEFAULT_LENGTH = 4
    fun valid(value: String, selectedLength: Int? = null): Boolean =
        value.length == 4 && value.all { it in '0'..'9' } &&
            (selectedLength == null || value.length == selectedLength)
}
