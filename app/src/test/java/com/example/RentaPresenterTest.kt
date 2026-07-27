package com.example

import com.example.data.entity.InvestmentEntity
import com.example.domain.SiiPolicy
import com.example.ui.screens.RentaPresenter
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del presentador de la pantalla Renta.
 *
 * Blindan: (C4) el mapeo cartera→lotes FIFO movido desde la UI, y las invariantes LEGALES del
 * estado vacío — el sello "no se presenta al SII", la etiqueta del botón final de [SiiPolicy]
 * (jamás "Presentar"/"Declarar") y un CSV sin filas de datos cuando no hay operaciones.
 */
class RentaPresenterTest {

    @Test
    fun `fromInvestments mapea ticker, cantidad, precio y fecha desde createdAt`() {
        val cal = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.MARCH, 15)
        }
        val inv = InvestmentEntity(
            id = 1, ticker = "PLTR", companyName = "Palantir Technologies",
            quantity = 5.085598192, purchasePrice = 146.40, currentPrice = 156.54,
            createdAt = cal.timeInMillis, updatedAt = cal.timeInMillis,
        )

        val lots = RentaPresenter.fromInvestments(listOf(inv))

        assertEquals(1, lots.size)
        val lot = lots.first()
        assertEquals("PLTR", lot.ticker)
        assertEquals(5.085598192, lot.quantity, 1e-12)
        assertEquals(146.40, lot.unitPrice, 1e-9)
        assertEquals(0.0, lot.commission, 0.0)
        assertEquals(2026, lot.date.year)
        assertEquals(3, lot.date.month)
    }

    @Test
    fun `fromInvestments con cartera vacia devuelve lista vacia`() {
        assertTrue(RentaPresenter.fromInvestments(emptyList()).isEmpty())
    }

    @Test
    fun `estado vacio conserva las invariantes legales`() {
        val state = RentaPresenter.build(taxYear = 2026)

        // Sello obligatorio y disclaimer versionado por año tributario.
        assertTrue(state.sealHeadline.contains("no se presenta al SII"))
        assertTrue(state.sealDetail.contains("2026"))

        // Botón final: SIEMPRE la etiqueta de SiiPolicy (jamás "Presentar"/"Declarar").
        assertEquals(SiiPolicy.FINAL_ACTION_LABEL, state.copyLabel)

        // Las dos tarjetas "requiere contador" (política v1) existen incluso sin datos.
        assertTrue(state.art107.title.contains("Art. 107"))
        assertTrue(state.art41a.title.contains("Art. 41 A"))
        assertEquals("Requiere contador", state.art107.chipLabel)

        // Estados vacíos amables presentes; conciliación sin filas.
        assertNotNull(state.emptyHint)
        assertNotNull(state.reconEmptyMessage)
        assertTrue(state.reconRows.isEmpty())

        // CSV sin operaciones: solo encabezado + disclaimer (ninguna fila de datos).
        val lines = state.copyPayload.split('\n')
        assertEquals(2, lines.size)
        assertEquals("codigo,glosa,monto_clp,origen", lines[0])
        assertTrue(lines[1].startsWith("# "))
    }
}
