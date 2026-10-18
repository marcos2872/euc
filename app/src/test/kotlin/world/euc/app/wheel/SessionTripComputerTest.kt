package world.euc.app.wheel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionTripComputerTest {

    @Test
    fun `integra a velocidade ao longo do tempo`() {
        val computer = SessionTripComputer()

        computer.integrate(speedKmh = 36.0, timeMs = 1_000) // primeira amostra só marca o tempo
        computer.integrate(speedKmh = 36.0, timeMs = 2_000) // 1 s a 36 km/h = 0,01 km

        assertEquals(0.01, computer.distanceKm, 1e-6)
        assertEquals(1, computer.sampleCount)
    }

    @Test
    fun `intervalo longo entre amostras nao soma distancia`() {
        val computer = SessionTripComputer()

        computer.integrate(speedKmh = 36.0, timeMs = 1_000)
        computer.integrate(speedKmh = 36.0, timeMs = 10_000) // 9 s: conexão perdida, não deslocamento

        assertEquals(0.0, computer.distanceKm)
    }

    @Test
    fun `odometro da roda tem prioridade sobre a integracao`() {
        val computer = SessionTripComputer()
        computer.updateWheelTrip(tripKm = 100.0)
        computer.updateWheelTrip(tripKm = 100.5)
        computer.integrate(speedKmh = 36.0, timeMs = 1_000)
        computer.integrate(speedKmh = 36.0, timeMs = 2_000)

        assertEquals(0.5, computer.distanceKm, 1e-6)
        assertTrue(computer.hasWheelTrip)
    }

    @Test
    fun `integracao cobre roda que reporta viagem congelada`() {
        val computer = SessionTripComputer()
        computer.updateWheelTrip(tripKm = 0.0)
        computer.updateWheelTrip(tripKm = 0.0)
        computer.integrate(speedKmh = 36.0, timeMs = 1_000)
        computer.integrate(speedKmh = 36.0, timeMs = 2_000)

        assertEquals(0.01, computer.distanceKm, 1e-6)
    }

    @Test
    fun `viagem da roda zerada no meio da sessao nao vira distancia negativa`() {
        val computer = SessionTripComputer()
        computer.updateWheelTrip(tripKm = 50.0)
        computer.updateWheelTrip(tripKm = 2.0) // usuário zerou o odômetro da roda

        assertEquals(0.0, computer.distanceKm)
    }

    @Test
    fun `sem velocidade nao acumula`() {
        val computer = SessionTripComputer()
        computer.integrate(speedKmh = null, timeMs = 1_000)
        computer.integrate(speedKmh = null, timeMs = 2_000)

        assertEquals(0.0, computer.distanceKm)
        assertEquals(0, computer.sampleCount)
        assertFalse(computer.hasWheelTrip)
    }

    @Test
    fun `reset zera viagem e referencias`() {
        val computer = SessionTripComputer()
        computer.updateWheelTrip(tripKm = 10.0)
        computer.integrate(speedKmh = 36.0, timeMs = 1_000)
        computer.integrate(speedKmh = 36.0, timeMs = 2_000)

        computer.reset()

        assertEquals(0.0, computer.distanceKm)
        assertFalse(computer.hasWheelTrip)
        assertEquals(0, computer.sampleCount)
    }
}
