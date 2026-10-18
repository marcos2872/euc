package world.euc.app.tour

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpxWriterTest {

    @Test
    fun `gera gpx valido com um ponto por amostra`() {
        val gpx = GpxWriter.write(tour())

        assertTrue(gpx.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(gpx.contains("""<gpx version="1.1" creator="euc""""))
        assertTrue(gpx.contains("""xmlns:euc="${GpxWriter.EXTENSION_NAMESPACE}""""))
        assertEquals(2, Regex("<trkpt ").findAll(gpx).count())
        assertEquals(2, Regex("</trkpt>").findAll(gpx).count())
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun `ponto leva posicao, altitude, horario e dados da roda`() {
        val gpx = GpxWriter.write(tour())

        assertTrue(gpx.contains("""<trkpt lat="-23.5012345" lon="-46.5012345">"""))
        assertTrue(gpx.contains("<ele>760.5</ele>"))
        assertTrue(gpx.contains("<time>1970-01-01T00:00:01Z</time>"))
        assertTrue(gpx.contains("<euc:speed>25.50</euc:speed>"))
        assertTrue(gpx.contains("<euc:battery>77</euc:battery>"))
        assertTrue(gpx.contains("<euc:power>944</euc:power>"))
        assertTrue(gpx.contains("<euc:current>12.50</euc:current>"))
        assertTrue(gpx.contains("<euc:temperature>42.0</euc:temperature>"))
    }

    @Test
    fun `horario sai em utc`() {
        val gpx = GpxWriter.write(tour().copy(startedAtMs = 0L))

        assertTrue(gpx.contains("<time>1970-01-01T00:00:00Z</time>"))
    }

    @Test
    fun `nome com caracteres especiais e escapado`() {
        val gpx = GpxWriter.write(tour().copy(name = "Passeio & trilha <sul>"))

        assertTrue(gpx.contains("<name>Passeio &amp; trilha &lt;sul&gt;</name>"))
        assertFalse(gpx.contains("Passeio & trilha <sul>"))
    }

    @Test
    fun `ponto sem altitude nem bateria nao gera as tags`() {
        val gpx = GpxWriter.write(
            tour().copy(
                points = listOf(
                    TourPoint(timeMs = 1_000, latitude = -23.5, longitude = -46.5, speedKmh = 0.0),
                ),
            ),
        )

        assertFalse(gpx.contains("<ele>"))
        assertFalse(gpx.contains("<euc:battery>"))
        assertTrue(gpx.contains("<euc:speed>0.00</euc:speed>"))
    }

    private fun tour() = Tour(
        id = "teste",
        name = "Passeio de teste",
        startedAtMs = 0L,
        endedAtMs = 2_000L,
        wheelModel = "V11Y",
        points = listOf(
            TourPoint(
                timeMs = 1_000,
                latitude = -23.5012345,
                longitude = -46.5012345,
                speedKmh = 25.5,
                altitudeM = 760.5,
                batteryPercent = 77,
                voltage = 75.5,
                current = 12.5,
                powerW = 943.75,
                temperatureC = 42.0,
                distanceKm = 0.0,
            ),
            TourPoint(
                timeMs = 2_000,
                latitude = -23.5022345,
                longitude = -46.5012345,
                speedKmh = 20.0,
                distanceKm = 0.111,
            ),
        ),
    )
}
