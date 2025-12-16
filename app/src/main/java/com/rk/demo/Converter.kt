package com.rk.demo

import java.math.BigInteger
import java.util.Base64

object Converter {
    // 1. Decimal <-> Hex
    fun decToHex(dec: String): String = try { BigInteger(dec).toString(16) } catch(e: Exception) { "Error" }
    fun hexToDec(hex: String): String = try { BigInteger(hex, 16).toString() } catch(e: Exception) { "Error" }

    // 2. Base64
    fun toBase64(input: String): String = 
        Base64.getEncoder().encodeToString(input.toByteArray())
    fun fromBase64(b64: String): String = try {
        String(Base64.getDecoder().decode(b64))
    } catch(e: Exception) { "Invalid Base64" }

    // 3. Endian Swap (Hex)
    fun endianSwap(hex: String): String {
        val clean = hex.replace("0x", "")
        val padded = if (clean.length % 2 != 0) "0$clean" else clean
        return padded.chunked(2).reversed().joinToString("")
    }

    // 4. Base58 (Bitcoin style)
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = BigInteger.valueOf(58)

    fun toBase58(hex: String): String {
        var num = try { BigInteger(hex, 16) } catch(e: Exception) { return "Invalid Hex" }
        if (num == BigInteger.ZERO) return "1"
        val sb = StringBuilder()
        while (num > BigInteger.ZERO) {
            val (div, rem) = num.divideAndRemainder(BASE)
            sb.append(ALPHABET[rem.toInt()])
            num = div
        }
        return sb.reverse().toString()
    }
}