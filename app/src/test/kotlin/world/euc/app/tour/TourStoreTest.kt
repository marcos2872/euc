package world.euc.app.tour

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TourStoreTest {

    @Test
    fun `salva e le de volta mantendo os valores`() {
        val store = TourStore(tempDir())
        val tour = tour()

        store.save(tour)
        val loaded = store.load(tour.id)

        assertNotNull(loaded)
        assertEquals(tour.id, loaded.id)
        assertEquals(tour.name, loaded.name)
        assertEquals(tour.startedAtMs, loaded.startedAtMs)
        assertEquals(tour.endedAtMs, loaded.endedAtMs)
        assertEquals("V11Y", loaded.wheelModel)
        assertEquals(2, loaded.points.size)
        assertEquals(-23.5012345, loaded.points[0].latitude, 1e-7)
        assertEquals(25.5, loaded.points[0].speedKmh, 1e-6)
        assertEquals(77, loaded.points[0].batteryPercent)
        assertEquals(0.111, loaded.points[1].distanceKm, 0.002)
    }

    @Test
    fun `campos ausentes voltam nulos`() {
        val store = TourStore(tempDir())
        val tour = tour().copy(
            wheelModel = null,
            points = listOf(
                TourPoint(
                    timeMs = 1_000,
                    latitude = -23.5,
                    longitude = -46.5,
                    speedKmh = 0.0,
                ),
            ),
        )

        store.save(tour)
        val loaded = store.load(tour.id)

        assertNotNull(loaded)
        assertNull(loaded.wheelModel)
        assertNull(loaded.points[0].batteryPercent)
        assertNull(loaded.points[0].voltage)
        assertNull(loaded.points[0].altitudeM)
    }

    @Test
    fun `listagem traz resumo do mais recente para o mais antigo`() {
        val store = TourStore(tempDir())
        val older = tour().copy(id = "2026-10-17-100000", startedAtMs = 1_000, endedAtMs = 61_000)
        val newer = tour().copy(id = "2026-10-18-100000", startedAtMs = 5_000, endedAtMs = 65_000)

        store.save(older)
        store.save(newer)
        val summaries = store.list()

        assertEquals(listOf(newer.id, older.id), summaries.map { it.id })
        assertEquals(2, summaries[0].pointCount)
        assertEquals(25.5, summaries[0].maxSpeedKmh, 1e-6)
        assertEquals(0.111, summaries[0].distanceKm, 0.002)
    }

    @Test
    fun `delete apaga o arquivo do passeio`() {
        val store = TourStore(tempDir())
        val tour = tour()
        store.save(tour)

        assertTrue(store.delete(tour.id))
        assertNull(store.load(tour.id))
        assertEquals(0, store.list().size)
    }

    @Test
    fun `exporta csv com cabecalho e uma linha por ponto`() {
        val store = TourStore(tempDir())
        val target = File(tempDir(), "passeio.csv")

        store.exportCsv(tour(), target)
        val lines = target.readLines()

        assertEquals(3, lines.size, "cabeçalho + 2 pontos")
        assertTrue(lines[0].startsWith("datetime,time,lat,lon,speed"))
        assertEquals(12, lines[1].split(',').size)
    }

    private fun tour() = Tour(
        id = "2026-10-18-120000",
        name = "Passeio de teste",
        startedAtMs = 1_000,
        endedAtMs = 61_000,
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
                altitudeM = 761.0,
                batteryPercent = 76,
                voltage = 75.4,
                current = 10.0,
                powerW = 754.0,
                temperatureC = 43.0,
                distanceKm = 0.111,
            ),
        ),
    )

    private fun tempDir(): File = Files.createTempDirectory("euc-tour-test").toFile()
}
