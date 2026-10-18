package world.euc.protocol.inmotion

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InmotionV2SessionTest {

    private val modelPayload = byteArrayOf(0x02, 0x01)
    private val serialPayload = byteArrayOf(0x02, 0x02)
    private val firmwarePayload = byteArrayOf(0x02, 0x06)
    private val settingsPayload = byteArrayOf(0x20, 0x20)
    private val valuesPayload = byteArrayOf(0x04)

    @Test
    fun `inicialização consulta modelo, serial, firmware e settings na ordem`() = runTest {
        val link = FakeWheelLink().apply { answerAuthentication = true }
        val session = InmotionV2Session(link, config())
        session.start(backgroundScope)
        repeat(4) { runCurrent() } // handshake + primeira consulta

        assertEquals(listOf(modelPayload.toList()), link.writtenProtocolPayloads().map { it.toList() })

        link.emit(TestFrames.carType(modelByte = 6, variant = 2))
        runCurrent()
        assertEquals(serialPayload.toList(), link.writtenProtocolPayloads()[1].toList())

        link.emit(TestFrames.serial("V11Y0000000000000001"))
        runCurrent()
        assertEquals(firmwarePayload.toList(), link.writtenProtocolPayloads()[2].toList())

        link.emit(TestFrames.firmware(1, 4, 0))
        runCurrent()
        assertEquals(settingsPayload.toList(), link.writtenProtocolPayloads()[3].toList())

        link.emit(TestFrames.settings())
        runCurrent()
        assertEquals(4, link.writtenProtocolPayloads().size)
    }

    @Test
    fun `publica eventos de identificação, configurações e telemetria`() = runTest {
        val link = FakeWheelLink()
        val session = InmotionV2Session(link, config())
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch { session.events.collect { events += it } }
        runCurrent()
        session.start(backgroundScope)
        runCurrent()

        link.emit(TestFrames.carType(modelByte = 6, variant = 2)); runCurrent()
        link.emit(TestFrames.serial("V11Y0000000000000001")); runCurrent()
        link.emit(TestFrames.firmware(1, 4, 0)); runCurrent()
        link.emit(TestFrames.settings()); runCurrent()
        link.emit(TestFrames.values()); runCurrent()

        val model = events.filterIsInstance<SessionEvent.ModelIdentified>().single()
        assertEquals("V11Y", model.model.displayName)
        assertEquals("V11Y0000000000000001", events.filterIsInstance<SessionEvent.SerialNumberReceived>().single().serialNumber)
        assertEquals("1.4.0", events.filterIsInstance<SessionEvent.FirmwareReceived>().single().versions.main)
        assertTrue(events.any { it is SessionEvent.SettingsReceived })

        val telemetry = events.filterIsInstance<SessionEvent.TelemetryReceived>().single().telemetry
        assertEquals(84.20, telemetry.voltageVolts!!, 0.001)
        assertEquals(25.00, telemetry.speedKmh!!, 0.001)
        assertEquals(78.00, telemetry.batteryPercent!!, 0.001)
        assertEquals(65.00, telemetry.safetyMarginPercent!!, 0.001)
    }

    @Test
    fun `consulta sem resposta é reenviada até o limite e emite timeout`() = runTest {
        val link = FakeWheelLink().apply { answerAuthentication = true }
        val session = InmotionV2Session(link, config())
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch { session.events.collect { events += it } }
        runCurrent()
        session.start(backgroundScope)
        repeat(4) { runCurrent() }

        assertEquals(1, link.writeCount(modelPayload))

        advanceTimeBy(1_001); runCurrent()
        assertEquals(2, link.writeCount(modelPayload))

        advanceTimeBy(1_001); runCurrent()
        assertEquals(3, link.writeCount(modelPayload))

        advanceTimeBy(1_001); runCurrent()
        assertEquals(3, link.writeCount(modelPayload), "não deve passar de maxAttempts")
        assertTrue(events.any { it is SessionEvent.CommandTimedOut })
    }

    @Test
    fun `após a inicialização faz polling de telemetria periodicamente`() = runTest {
        val link = FakeWheelLink().apply { answerAuthentication = true }
        val session = InmotionV2Session(link, config(pollIntervalMillis = 1_000))
        session.start(backgroundScope)
        repeat(4) { runCurrent() }

        answerInitialization(link)

        advanceTimeBy(1_001); runCurrent()
        val firstPolls = link.writeCount(valuesPayload)
        assertTrue(firstPolls >= 1, "polling deveria enviar readValues, enviou $firstPolls")

        advanceTimeBy(3_000); runCurrent()
        assertTrue(link.writeCount(valuesPayload) > firstPolls, "polling deveria continuar")
    }

    @Test
    fun `comando de controle é enviado em fila, sem esperar resposta`() = runTest {
        val link = FakeWheelLink().apply { answerAuthentication = true }
        val session = InmotionV2Session(link, config())
        session.start(backgroundScope)
        repeat(4) { runCurrent() }

        answerInitialization(link)

        session.send(InmotionV2Commands.horn())
        runCurrent()

        assertEquals(1, link.writeCount(byteArrayOf(0x60, 0x51, 0x18, 0x01)))
    }

    @Test
    fun `cancelar a sessão interrompe os envios`() = runTest {
        val link = FakeWheelLink()
        val session = InmotionV2Session(link, config(pollIntervalMillis = 1_000))
        val job = session.start(backgroundScope)
        runCurrent()

        job.cancel()
        runCurrent()
        val writesAfterCancel = link.writeTotal()

        advanceTimeBy(10_000); runCurrent()

        assertEquals(writesAfterCancel, link.writeTotal())
    }

    @Test
    fun `handshake de autenticação envia chave e confirmação`() = runTest {
        val link = FakeWheelLink()
        link.answerAuthentication = true
        val session = InmotionV2Session(link, config())
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch { session.events.collect { events += it } }
        runCurrent()
        session.start(backgroundScope)
        runCurrent()

        // Primeiro quadro: pedido de chave (vetor do app oficial).
        val request = link.writtenPayloads().first()
        assertContentEquals(byteArrayOf(0x02, 0x00, 0x00, 0x02), request)

        // Segundo quadro: verificação devolvendo o blob recebido.
        val verify = link.writtenPayloads()[1]
        assertEquals(20, verify.size)
        assertContentEquals(link.authKey, verify.copyOfRange(4, 20))

        assertTrue(events.any { it is SessionEvent.AuthenticationCompleted && it.success })
    }

    @Test
    fun `sem resposta de chave a sessão segue sem autenticar`() = runTest {
        val link = FakeWheelLink()
        val session = InmotionV2Session(link, config())
        val events = mutableListOf<SessionEvent>()
        backgroundScope.launch { session.events.collect { events += it } }
        runCurrent()
        session.start(backgroundScope)
        runCurrent()

        advanceTimeBy(2_001); runCurrent()

        assertTrue(events.any { it is SessionEvent.AuthenticationCompleted && !it.success })
        // A inicialização continua mesmo sem autenticação.
        assertTrue(link.writeCount(modelPayload) >= 1)
    }

    private suspend fun TestScope.answerInitialization(link: FakeWheelLink) {
        link.emit(TestFrames.carType(modelByte = 6, variant = 2)); runCurrent()
        link.emit(TestFrames.serial("V11Y0000000000000001")); runCurrent()
        link.emit(TestFrames.firmware(1, 4, 0)); runCurrent()
        link.emit(TestFrames.settings()); runCurrent()
    }

    private fun config(pollIntervalMillis: Long = 100_000) = SessionConfig(
        queryTimeoutMillis = 1_000,
        controlSettleMillis = 100,
        maxAttempts = 3,
        pollIntervalMillis = pollIntervalMillis,
        queueCapacity = 32,
    )
}
