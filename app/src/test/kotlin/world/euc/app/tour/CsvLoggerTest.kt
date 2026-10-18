package world.euc.app.tour

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CsvLoggerTest {

    @Test
    fun `cabecalho tem as 46 colunas do EUC World`() {
        assertEquals(46, CsvLogger.COLUMNS.size)
        assertEquals("datetime", CsvLogger.COLUMNS.first())
        assertEquals("extra", CsvLogger.COLUMNS.last())
        assertEquals(
            listOf("gps_lat", "gps_lon", "gps_speed"),
            CsvLogger.COLUMNS.subList(35, 38),
        )
    }

    @Test
    fun `linha tem um campo por coluna e valores vazios nos ausentes`() {
        val row = CsvLogger.formatRow(
            LogSample(timeMs = 1_000, speedKmh = 25.456, batteryPercent = 77.0),
            startedAtMs = 1_000,
        )
        val fields = row.split(',')

        assertEquals(CsvLogger.COLUMNS.size, fields.size)
        assertEquals("25.46", fields[5], "velocidade com 2 casas")
        assertEquals("77", fields[17], "bateria sem casas")
        assertEquals("", fields[10], "aceleração não é medida")
        assertEquals("", fields[45], "extra vazio")
    }

    @Test
    fun `duracao e data sao calculadas a partir do inicio do log`() {
        val row = CsvLogger.formatRow(
            LogSample(timeMs = 61_500),
            startedAtMs = 1_000,
        )
        val fields = row.split(',')

        assertEquals("60.500", fields[1], "60,5 s desde o início")
        assertTrue(fields[0].startsWith("19"), "datetime preenchido: ${fields[0]}")
    }

    @Test
    fun `gps entra com 7 casas decimais`() {
        val fix = GpsFix(
            timeMs = 1_500,
            latitude = -23.5012345,
            longitude = -46.5012345,
            accuracyM = 4.5f,
            altitudeM = 760.4,
            speedKmh = 24.0,
            bearingDegrees = 91.5f,
        )
        val row = CsvLogger.formatRow(LogSample(timeMs = 2_000, gps = fix), startedAtMs = 1_000)
        val fields = row.split(',')

        assertEquals("-23.5012345", fields[35])
        assertEquals("-46.5012345", fields[36])
        assertEquals("760.4", fields[41])
        assertEquals("91.5", fields[42])
        assertEquals("4.5", fields[43])
        assertEquals("0.500", fields[32], "gps_duration = 0,5 s desde o fix")
    }

    @Test
    fun `escreve arquivo com cabecalho e uma linha por amostra`() {
        val dir = Files.createTempDirectory("euc-log-test").toFile()
        val logger = CsvLogger(dir)

        val file = logger.start()
        assertNotNull(file)
        logger.append(LogSample(timeMs = System.currentTimeMillis(), speedKmh = 10.0))
        logger.append(LogSample(timeMs = System.currentTimeMillis(), speedKmh = 20.0))
        logger.stop()

        val lines = file.readLines()
        assertEquals(3, lines.size, "cabeçalho + 2 amostras")
        assertEquals(CsvLogger.COLUMNS.joinToString(","), lines[0])
        assertEquals(CsvLogger.COLUMNS.size, lines[1].split(',').size)
        assertTrue(file.name.startsWith("euc-log-"))
        assertTrue(file.name.endsWith(".csv"))
    }

    @Test
    fun `listagem e remocao de logs`() {
        val dir = Files.createTempDirectory("euc-log-test").toFile()
        val logger = CsvLogger(dir)
        val file = logger.start()
        assertNotNull(file)
        logger.stop()

        assertEquals(1, logger.list().size)
        assertTrue(logger.delete(file))
        assertEquals(0, logger.list().size)
        assertTrue(!File(dir, "inexistente.csv").exists())
    }
}
