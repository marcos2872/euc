package world.euc.app.wheel

import kotlin.math.max

/**
 * Viagem da sessão: distância percorrida desde que o app conectou na roda.
 *
 * Duas fontes, combinadas para dar o melhor dos dois mundos:
 *
 * - **Odômetro de viagem da roda** (quando o firmware reporta): a distância
 *   desde a conexão é o delta do valor reportado, sem erro de integração.
 * - **Integração da velocidade** da telemetria: funciona sempre, inclusive em
 *   modelos que não reportam viagem, e cobre o caso do firmware responder um
 *   valor congelado (delta zero).
 *
 * O resultado é o **maior** dos dois: quando a roda reporta corretamente os
 * dois concordam; quando não, a integração sustenta o número.
 */
class SessionTripComputer {

    private var referenceTripKm: Double? = null
    private var latestTripKm: Double? = null
    private var integratedKm = 0.0
    private var lastSampleTimeMs = 0L

    /** Distância da sessão em km. */
    val distanceKm: Double
        get() {
            val reference = referenceTripKm
            val latest = latestTripKm
            val wheelDelta = if (reference != null && latest != null) {
                (latest - reference).coerceAtLeast(0.0)
            } else {
                0.0
            }
            return max(wheelDelta, integratedKm)
        }

    /** Quantidade de amostras de telemetria usadas na integração. */
    var sampleCount: Int = 0
        private set

    /** A roda reportou odômetro de viagem? */
    val hasWheelTrip: Boolean get() = referenceTripKm != null

    /** Atualiza com o odômetro de viagem reportado pela roda (km). */
    fun updateWheelTrip(tripKm: Double?) {
        if (tripKm == null) return
        if (referenceTripKm == null) referenceTripKm = tripKm
        latestTripKm = tripKm
    }

    /**
     * Integra uma leitura de velocidade. Intervalos muito longos entre
     * amostras (conexão perdida) não somam distância — evitam inventar
     * quilômetros com a velocidade da última leitura.
     */
    fun integrate(speedKmh: Double?, timeMs: Long) {
        val previous = lastSampleTimeMs
        lastSampleTimeMs = timeMs
        if (speedKmh == null || previous <= 0L) return
        val deltaMs = timeMs - previous
        if (deltaMs < 1L || deltaMs > MAX_GAP_MS) return
        integratedKm += speedKmh * deltaMs / 3_600_000.0
        sampleCount += 1
    }

    fun reset() {
        referenceTripKm = null
        latestTripKm = null
        integratedKm = 0.0
        lastSampleTimeMs = 0L
        sampleCount = 0
    }

    private companion object {
        /** Acima disso a amostra é considerada desconexão, não deslocamento. */
        const val MAX_GAP_MS = 5_000L
    }
}
