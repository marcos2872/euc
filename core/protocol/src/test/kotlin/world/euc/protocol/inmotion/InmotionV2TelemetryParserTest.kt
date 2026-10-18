package world.euc.protocol.inmotion

import world.euc.protocol.model.RidingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InmotionV2TelemetryParserTest {

    private val parser = InmotionV2TelemetryParser()

    @Test
    fun `extrai os valores principais de um quadro de telemetria da V11y`() {
        val frame = valuesFrame(
            voltageRaw = 8420,        // 84.20 V
            currentRaw = 525,         // 5.25 A
            speedRaw = 2500,          // 25.00 km/h
            loadRaw = 3500,           // 35.00 %
            pitchRaw = 150,           // 1.50°
            rollRaw = -200,           // -2.00°
            batteryRaw = 7800,        // 78.00 %
            speedLimitRaw = 3000,     // 30.00 km/h
            currentLimitRaw = 3000,   // 30.00 A
            temperaturesRaw = mapOf(
                58 to 211,            // MOS 35 °C
                59 to 216,            // motor 40 °C
                60 to 206,            // bateria 30 °C
                61 to 209,            // placa 33 °C
                62 to 221,            // CPU 45 °C
                63 to 214,            // IMU 38 °C
            ),
            statusRaw = 1,            // DRIVE
        )

        val telemetry = parser.parse(frame)

        assertNotNull(telemetry)
        assertEquals(84.20, telemetry.voltageVolts!!, 0.001)
        assertEquals(5.25, telemetry.currentAmps!!, 0.001)
        assertEquals(25.00, telemetry.speedKmh!!, 0.001)
        assertEquals(35.00, telemetry.loadPercent!!, 0.001)
        assertEquals(65.00, telemetry.safetyMarginPercent!!, 0.001)
        assertEquals(1.50, telemetry.pitchDegrees!!, 0.001)
        assertEquals(-2.00, telemetry.rollDegrees!!, 0.001)
        assertEquals(78.00, telemetry.batteryPercent!!, 0.001)
        assertEquals(30.00, telemetry.speedLimitKmh!!, 0.001)
        assertEquals(30.00, telemetry.currentLimitAmps!!, 0.001)
        assertEquals(RidingMode.DRIVE, telemetry.ridingMode)

        val temperatures = telemetry.temperatures
        assertNotNull(temperatures)
        assertEquals(35.0, temperatures.mosfet!!, 0.001)
        assertEquals(40.0, temperatures.motor!!, 0.001)
        assertEquals(30.0, temperatures.battery!!, 0.001)
        assertEquals(33.0, temperatures.board!!, 0.001)
        assertEquals(45.0, temperatures.cpu!!, 0.001)
        assertEquals(38.0, temperatures.imu!!, 0.001)
    }

    @Test
    fun `velocidade e corrente negativas são normalizadas quando faz sentido`() {
        val frame = valuesFrame(speedRaw = -2500, batteryRaw = -7800, loadRaw = -3500)

        val telemetry = parser.parse(frame)!!

        assertEquals(25.00, telemetry.speedKmh!!, 0.001)
        assertEquals(78.00, telemetry.batteryPercent!!, 0.001)
        assertEquals(35.00, telemetry.loadPercent!!, 0.001)
        assertEquals(65.00, telemetry.safetyMarginPercent!!, 0.001)
    }

    @Test
    fun `corrente mantém o sinal`() {
        val frame = valuesFrame(currentRaw = -1200)

        assertEquals(-12.00, parser.parse(frame)!!.currentAmps!!, 0.001)
    }

    @Test
    fun `quadro curto devolve campos ausentes em vez de valores errados`() {
        // Resposta antiga/curta: só tensão e corrente presentes.
        val payload = byteArrayOf(0x84.toByte(), 0xE4.toByte(), 0x20, 0x0D, 0x02)
        val frame = InmotionV2Framing.decode(InmotionV2Framing.encode(InmotionFrameType.DEFAULT, payload))!!

        val telemetry = parser.parse(frame)!!

        assertEquals(84.20, telemetry.voltageVolts!!, 0.001)
        assertNull(telemetry.speedKmh)
        assertNull(telemetry.batteryPercent)
        assertNull(telemetry.temperatures)
    }

    @Test
    fun `ignora quadros que não são telemetria`() {
        val frame = InmotionV2Framing.decode(
            InmotionV2Framing.encode(InmotionFrameType.DEFAULT, byteArrayOf(0x60, 0x31, 0x01)),
        )!!

        assertNull(parser.parse(frame))
    }

    @Test
    fun `sensor de temperatura ausente (byte fora da faixa) vira nulo`() {
        val frame = valuesFrame(temperaturesRaw = mapOf(58 to 0x20, 59 to 216))

        val temperatures = parser.parse(frame)!!.temperatures!!

        assertNull(temperatures.mosfet)
        assertEquals(40.0, temperatures.motor!!, 0.001)
    }

    // ------------------------------------------------------------------ helper

    /** Monta um quadro de telemetria (comando 0x84) com os campos informados. */
    private fun valuesFrame(
        voltageRaw: Int = 0,
        currentRaw: Int = 0,
        speedRaw: Int = 0,
        loadRaw: Int = 0,
        pitchRaw: Int = 0,
        rollRaw: Int = 0,
        batteryRaw: Int = 0,
        speedLimitRaw: Int = 0,
        currentLimitRaw: Int = 0,
        temperaturesRaw: Map<Int, Int> = emptyMap(),
        statusRaw: Int = 0,
    ): InmotionFrame {
        val data = ByteArray(80)
        putU16(data, 0, voltageRaw)
        putU16(data, 2, currentRaw)
        putU16(data, 8, speedRaw)
        putU16(data, 14, loadRaw)
        putU16(data, 20, pitchRaw)
        putU16(data, 22, rollRaw)
        putU16(data, 34, batteryRaw)
        putU16(data, 40, speedLimitRaw)
        putU16(data, 48, currentLimitRaw)
        temperaturesRaw.forEach { (offset, value) -> data[offset] = value.toByte() }
        data[74] = statusRaw.toByte()

        val payload = byteArrayOf(0x84.toByte()) + data
        val encoded = InmotionV2Framing.encode(InmotionFrameType.DEFAULT, payload)
        return InmotionV2Framing.decode(encoded)!!
    }

    private fun putU16(data: ByteArray, offset: Int, value: Int) {
        val unsigned = value and 0xFFFF
        data[offset] = (unsigned and 0xFF).toByte()
        data[offset + 1] = ((unsigned shr 8) and 0xFF).toByte()
    }
}
