package com.aidev.assistant.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encrypted API key vault.
 * Fully reversible. Keys never stored in plaintext in the APK.
 */
object SecretVault {

    // Obfuscated master passphrase parts (joined + lightly transformed at runtime)
    private val p1 = intArrayOf(65, 73, 68, 101, 118, 65, 115, 115, 105, 115, 116, 97, 110, 116) // AIDevAssistant
    private val p2 = intArrayOf(35, 50, 48, 50, 54, 33) // #2026!
    private val p3 = intArrayOf(83, 101, 99, 114, 51, 116, 75, 51, 121, 36, 120, 65, 73) // Secr3tK3y$xAI

    private val SALT = Base64.decode("YWlkZXZfc2FsdF92MV85ZjNh", Base64.DEFAULT)
    private const val ITERATIONS = 120_000
    private const val KEY_LEN = 256
    private const val GCM_TAG = 128
    private const val NONCE_LEN = 12

    // ===== ENCRYPTED PAYLOADS (nonce || ciphertext) base64 =====
    private val ENC_GEMINI = listOf(
        "zkjQHDkYjNOkb+UeDkutL08+TQ2gzt5RSBEcqCcNXypJeKsM0bBc1tDSxu2gxO8B810ZZ6qqiovkjtyiNHMkET/A5xXYELrwlh+bq+5ubAXd",
        "5i3D+Uoji5vqpH7DSVOaETJJZ6tj0yp7vL1FQW/IV2Bg9MHZJgboPusw83CiN36Zek6autCqe7bbGd0e3ajSeaD2M2HY44XJ9ug6xMXuh2k/",
        "IVtbUlI/nBcPn2yVgEOE7P5NB1JfLfO+EcPYGyDiQA+IIyO1FPXHVIt/3GpovCw42V8HD7wgc4NL5/dBNwvIA/W9fA==",
        "Ka+xAwUHty8eKPMSMZhRmRiTF1XTbnx6vl2m96aLHmmmdDI6rP3xriPVpp+/v0Py10+w87ecx6OMM36WOo/ltZheKGptl28qfqc6DPQOwsM3",
        "q3zWzf35q/8Lmw+NHfnqhp6AOLYy5lSUyPb7hp5E8Wr96o4nU6dRwE9M6kY3+68KNjlAAvU4lA0sVATtJOHBXK3gottKWGyBr0qKLV2JZcqU",
        "CJReWDGGTKeASna3pEztV6bxjrMhl91QkQvakWDotk4NBuP/5SDGWW6Qt9//gjQecdc3VV0IPxg2Ed398z3R6ROEmF8wNMv2YjE8C3TJzkku"
    )
    private val ENC_TAVILY = listOf(
        "JGokdVFiMz+WyREkzJAIrT9iNluHLlwPcHQwLxGeH5v0d9SIl11PR62nUwo/PnAtyyYIAad4j+sko/B7Y4jD6/JtJstiUPLFrh+fVKmgGsEZwiEmX3M=",
        "vn6cUSTayAjmqdIZ2OC80RHB4Jutd3c/axXr84ZqhISJQHZJmzUdtPplPs4+8Pe8SGZ5T5swb8HyuN45hGRArt6Eg++GdA86RR6hPrvF9WArKYNHufE=",
        "5+qy7yeaYLrOLHRa52XSatZC6EdFBlBSXb467aYq/EOih+/wMiCAu/teHMazFZsU8BT3UogLn1kaeLPoHYG95PscNnwhqwJMKOa/FjWULSA8MdtCo54=",
        "IRcCm0JqAEvuLNs338gMWbl26j6dXA5GZKkLo2eEMPsRpuNFybgU7vYVnDFh/aIOxhEV/oam6k2hsxMOAOuS4y0JXLLdgmQZiB9BEtEM1+gPRPRW/Gw=",
        "6M+jy7RkqbY7uYEzMUGtgqsjDHXdeZpp0A0+WO2jIG7A1qbIxTEMnMIqxb7J0nzGoc+IJTJou3ajd8csAxrBzR5uFWgoqzE23dkN2aEJmCOzLcJVUEk=",
        "n5fnV1MKkLnRLlospI6ZqC8SHhhA3nujZeX0lxXLXL/M6dUt37bGyavbiExKyBECyjZiZuoPGUWMm3FkH1S1+TYsy4EpT4RWiBRbmmNs27W0P/bLYU0=",
        "C2NhxFZyEn2fFYHVqBVce7lMdGuHLtUKJYXqz6a1wokob8h+ItP16WiMcEWyI4y2yz+0AmlV3o3em3SbF3brs6hN0NgX91T7XFmWrnj3FXSK3OIybJY="
    )
    private val ENC_GROQ = listOf(
        "8gCMyfSRhNkvVnB4v1D1g3+VexA1lY30mP3ZQ455GK+0VUM7XU/LG40+3XTOQWx+Vf2ZDMvrz50cOTv2A4NSeCwD0rd+pOxRQWrDvnyng21WSd1u",
        "SZVeGPDjRx1632zz+rWVYJO+5iOUASCYTTerAOXTJEsYh30ODqKAzVcyslgF1s+9ab/Vn2hjw/OjO2pOSz1HtfympSnCInZkb1pKALIcC9Y1W2Wt",
        "JDBsg3Lr2eNkXLn2q1Re6+m9Pgler8wP1rX6/U1jJiyW2M1uXvgBezV50yoCKNkq3P7iyXDnfCNGFtgePtY2ebHnYW+L8lWf10qW6ePUmRX7quuQ",
        "WilETmdGzjQWFnVRZ2XAVBbyTTdCXR3Nn68dAQuad60+JSjSOTduPCXrqCFLNDnYGGb73ogmIiiRQuzzow9gVEIEdb8Ajfu3RnCsD7aON7vDkeba",
        "+URQCiA0wh/Hztj8bizkptgMBR3j0G8ugOOo3+LX1Qy2Q37TznbWt9rNdDq2w69n9WoE4jNOGp0XuvT+JIs+1S2bjGH09+O1yku9SUSc/c3G4ODu",
        "+CQhU4XS3KEoHq2P1addutjBZw4JrLPic8/5u2uA0eUo/jCTJJXg+bel8aqhWzPvDyVtwxeVX9t1dysJ3uOp8Qc5vvsZ4ReYTvdqAvzLnUBFyOh9",
        "IoAyPz4gCyx026IziDjMBJPDdqu7E9gpy1JbTt+FYA+LTPQ1pi8plTCPv1tnY36Tnq0rHAWAU8yXroUHdG92ROGEUOrcytCEgOnsdR7X3BVlmIsp"
    )
    private val ENC_OPENROUTER = listOf(
        "TzgGi/BuAQPIp8ilkQUZV2i7YCdR86LU4Uu7X3JWCO1/w5SovqUDyvq5mY4yrRz/CYmg4YJye18FAJjlMO+87g850xQ4Btb6CiNINiysqj5no49Jc+GhYbZ4lMLxkRokCqksgxU=",
        "kHRT/PH6ubX5HsC/vpQDP3kfoozOWp1nT3sl3tjWkVSYiQFP/RZOY31wMD4NllvO8Cjhh7/uMIhjWaYQry/Ieq7so0uEqePsIaU7dIzAGT7nrzLl1ifb1EERSSL/ncwpC+zLVhY=",
        "y+69ry0hNMq+r473m3MVlkYJ0SEbZ+OWkyuokws8S8Q7U0BQdisFOeRD/sMXhfyDTK3meCn/Ie3I52Wd+WTAFtN4pdvJpSpMXgLoYhtEx69+LD5N8CDcj2AgpsJMH6DB6Ht6vQo=",
        "1VmcghbuCgUhY/W9hrnT+hPWUGpVY+BT05o2+PD6IBzXZDTinbo3Jc8kToEx8WyvBcrAXFS8SlSvFNeDKEtHyx+qLjBsH9J7e0MgDPaPfU4+OGwWRXsaK6YPaXdYOBR4QWjfs2M=",
        "irZPHh0MkkgEZSNSZN/P8qromXN845sVTgKri4j42T9Rr2Ot+4V6JKSFaSftygg1MPLzNSuh8rYgaVpxrlwOCGSfmxOl2goZm/4te3z3QNfCBRlBm2Q85p/bIshp8QiA/0hT9Xg=",
        "iZrlt2opJFVnUqDHx5elJ4mRAskOa35j//ICFjo3YR24qTU9OpEYORQQ16xoTJ3fkO4cFhAzCXWeW5uENR9fYdu+X2Xip8WS2CEhYWb4yYzzrqXjHgX4mcE8gd2L667tQbK4fHQ="
    )
    private val ENC_CLOUDFLARE = listOf(
        "88BFsVXz+w6VjYSj49Ba9u2F0OxxBlMkDPHFRxaV9erDYOhj06KNLHTJkJU0SlCh2WLmJI7YvEnWJDLrNVbVI0YLnrk0E11kKMqPFAOMfscjR25YbVLSq5A+oZFly1urubE+v5h3p9Q88Kj+HSfK/8dw",
        "Bt2/ZXRz1iohF+G2/Tc8RGtbLbpQkZpNoFeZr2XDe8KgiZ1l8oUaJgJ9AUkp3o9Vzr/iZ0/NcUVbyO0DzNYS1PSusWk0hXdg+8L/O1jpL675Hacb+QLVgfL7ZL41Gks7z/dAg47x5lgFqqXuN3JTIfgt"
    )
    private val ENC_SERPAPI = listOf(
        "TuooQxE4ZDavXZRj4oMLE55EQX63yy4dbCa/HsOSstvG8YyUEEZD1zAuQxIGdXyj9OHFSwXX3tBKkOOww9R4526ZYMhJUyPQ65+xTZE2AWw04k+1yypNv8gKkow=",
        "by/BlwhGmgZqdOWMtzvlSfeAEqS3KQsZ0oPD0BtDSpyFRfXPOemB2tU5ObQcvpCd65tAC6eCvSgBl6TgDkN50eJAb9TA+C6wWZjgE70l+wlIh2jOLqRFpJq+H74="
    )

    @Volatile private var cachedKey: ByteArray? = null

    private fun masterPassphrase(): CharArray {
        val chars = (p1 + p2 + p3).map { it.toChar() }.toCharArray()
        return chars
    }

    private fun deriveKey(): ByteArray {
        cachedKey?.let { return it }
        val spec = PBEKeySpec(masterPassphrase(), SALT, ITERATIONS, KEY_LEN)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        cachedKey = key
        return key
    }

    private fun decrypt(payloadB64: String): String {
        val raw = Base64.decode(payloadB64, Base64.DEFAULT)
        require(raw.size > NONCE_LEN) { "Invalid payload" }
        val nonce = raw.copyOfRange(0, NONCE_LEN)
        val ciphertext = raw.copyOfRange(NONCE_LEN, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(deriveKey(), "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG, nonce))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun decryptAll(list: List<String>): List<String> =
        list.mapNotNull { runCatching { decrypt(it) }.getOrNull() }

    // Public API
    fun geminiKeys(): List<String> = decryptAll(ENC_GEMINI)
    fun tavilyKeys(): List<String> = decryptAll(ENC_TAVILY)
    fun groqKeys(): List<String> = decryptAll(ENC_GROQ)
    fun openRouterKeys(): List<String> = decryptAll(ENC_OPENROUTER)
    fun cloudflareKeys(): List<String> = decryptAll(ENC_CLOUDFLARE) // token:accountId
    fun serpApiKeys(): List<String> = decryptAll(ENC_SERPAPI)

    fun randomKey(service: String): String? {
        val keys = when (service.lowercase()) {
            "gemini" -> geminiKeys()
            "tavily" -> tavilyKeys()
            "groq" -> groqKeys()
            "openrouter" -> openRouterKeys()
            "cloudflare" -> cloudflareKeys()
            "serpapi" -> serpApiKeys()
            else -> emptyList()
        }
        return keys.randomOrNull()
    }

    fun allServices(): Map<String, List<String>> = mapOf(
        "gemini" to geminiKeys(),
        "tavily" to tavilyKeys(),
        "groq" to groqKeys(),
        "openrouter" to openRouterKeys(),
        "cloudflare" to cloudflareKeys(),
        "serpapi" to serpApiKeys()
    )
}
