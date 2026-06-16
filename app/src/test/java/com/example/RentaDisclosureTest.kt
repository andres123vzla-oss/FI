package com.example

import com.example.domain.DisclosureLevel
import com.example.domain.DividendResult
import com.example.domain.DividendSource
import com.example.domain.RentaDisclaimer
import com.example.domain.RentaDisclosure
import com.example.domain.RentaRegime
import com.example.domain.RentaResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la capa de disclosure legal (v1): Art. 107 y dividendos extranjeros (Art. 41 A) se
 * degradan a "organizar respaldos" sin mostrar cifra; el resto se muestra referencial con
 * disclaimer versionado por año tributario.
 */
class RentaDisclosureTest {

    @Test
    fun `Art107 se degrada a organizar respaldos sin mostrar cifra`() {
        val r = RentaResult("SQM", 143_000.0, 110_250.0, 32_750.0, RentaRegime.ART_107, 3_275.0, false, 100.0)
        val v = RentaDisclosure.forSale(r, 2026)
        assertEquals(DisclosureLevel.ORGANIZE_ONLY, v.level)
        assertNull(v.taxEstimateShown)
        assertTrue(v.requiresAccountant)
        assertTrue(v.disclaimer.contains("2026"))
    }

    @Test
    fun `ingreso no renta se muestra referencial`() {
        val r = RentaResult("CHILE", 15_000.0, 10_000.0, 5_000.0, RentaRegime.INGRESO_NO_RENTA, 0.0, true, 10.0)
        val v = RentaDisclosure.forSale(r, 2026)
        assertEquals(DisclosureLevel.REFERENCIAL, v.level)
        assertEquals(0.0, v.taxEstimateShown!!, 0.0)
        assertFalse(v.requiresAccountant)
    }

    @Test
    fun `dividendo extranjero Art41A se degrada a organizar respaldos`() {
        val d = DividendResult("AAPL", DividendSource.EXTRANJERO, 100_000.0, 35_000.0, 135_000.0, 35_000.0, 0.0)
        val v = RentaDisclosure.forDividend(d, 2026)
        assertEquals(DisclosureLevel.ORGANIZE_ONLY, v.level)
        assertNull(v.taxableBaseShown)
        assertNull(v.creditShown)
        assertTrue(v.requiresAccountant)
    }

    @Test
    fun `dividendo nacional con credito SAC se muestra referencial`() {
        val d = DividendResult("BCI", DividendSource.NACIONAL, 100_000.0, 20_000.0, 120_000.0, 20_000.0, 0.0)
        val v = RentaDisclosure.forDividend(d, 2026)
        assertEquals(DisclosureLevel.REFERENCIAL, v.level)
        assertEquals(120_000.0, v.taxableBaseShown!!, 0.0)
        assertEquals(20_000.0, v.creditShown!!, 0.0)
    }

    @Test
    fun `disclaimer versionado contiene año, no-asesoria y sii punto cl`() {
        val disc = RentaDisclaimer.forYear(2026)
        assertTrue(disc.contains("2026"))
        assertTrue(disc.lowercase().contains("asesor"))
        assertTrue(disc.lowercase().contains("sii.cl"))
    }
}
