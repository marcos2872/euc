package world.euc.protocol.inmotion

/**
 * Comando pronto para envio à roda.
 *
 * Os payloads replicam byte a byte o que o app oficial Inmotion envia,
 * extraídos do EUC World 2.66.0 (`p364uh/C5937z.java`) e confirmados pela
 * documentação comunitária (eucplanet, MIT).
 */
class InmotionCommand(
    val frameType: InmotionFrameType,
    val payload: ByteArray,
) {
    /** Serializa o comando no quadro de bytes que vai pela característica BLE. */
    fun toFrame(): ByteArray = InmotionV2Framing.encode(frameType, payload)

    override fun equals(other: Any?): Boolean =
        other is InmotionCommand &&
            other.frameType == frameType &&
            other.payload.contentEquals(payload)

    override fun hashCode(): Int = 31 * frameType.hashCode() + payload.contentHashCode()

    override fun toString(): String =
        "InmotionCommand(type=$frameType, payload=${payload.joinToString(" ") { "%02X".format(it) }})"
}

/**
 * Construtores de comandos do protocolo Inmotion V2 (família V11+).
 *
 * Convenções do protocolo:
 * - Comandos de controle começam com `0x60` seguido de um subcódigo.
 * - Valores de velocidade/ângulo vão como `u16` little-endian multiplicados por 100.
 * - Percentuais vão como `u8` de 0 a 100.
 *
 * Diferenças por modelo/firmware são expostas como parâmetros explícitos
 * (ex.: [legacyHeadlightOpcode]); a escolha por modelo pertence à camada de
 * sessão, não a estes construtores.
 */
object InmotionV2Commands {

    private const val CONTROL = 0x60
    private const val ROUTING_0 = 0x02
    private const val ROUTING_1 = 0x21

    // ---------------------------------------------------------------- consultas

    /** Consulta o modelo (carType). Resposta: comando 0x81. */
    fun readModel(): InmotionCommand = command(InmotionFrameType.INITIAL, 0x02, 0x01)

    /** Consulta o número de série. Resposta: comando 0x82. */
    fun readSerialNumber(): InmotionCommand = command(InmotionFrameType.INITIAL, 0x02, 0x02)

    /** Consulta as versões de firmware. Resposta: comando 0x86. */
    fun readFirmwareVersions(): InmotionCommand = command(InmotionFrameType.INITIAL, 0x02, 0x06)

    /** Consulta as configurações atuais. Resposta: comando 0xA0 com sub 0x20. */
    fun readSettings(): InmotionCommand = command(InmotionFrameType.DEFAULT, 0x20, 0x20)

    /** Consulta a telemetria em tempo real. Resposta: comando 0x84. */
    fun readValues(): InmotionCommand = command(InmotionFrameType.DEFAULT, 0x04)

    /** Consulta a viagem atual. Resposta: comando 0x90. */
    fun readTrip(): InmotionCommand = command(InmotionFrameType.DEFAULT, 0x10, 0x00, 0x01)

    /** Consulta as estatísticas totais. Resposta: comando 0x91. */
    fun readTotalStatistics(): InmotionCommand = command(InmotionFrameType.DEFAULT, 0x11)

    /** Consulta os dados de bateria. Resposta: comando 0x85. */
    fun readBattery(): InmotionCommand = command(InmotionFrameType.DEFAULT, 0x05)

    // ---------------------------------------------------------------- controle

    /**
     * Bloqueia ou desbloqueia a roda.
     *
     * Em V12/V13/V14 (família da V11y) o firmware **ignora** o lock em framing
     * DEFAULT: é preciso [extended] = true, que adiciona o roteamento `02 21`
     * e usa o tipo de quadro EXTENDED. Modelos antigos (V11 e anteriores)
     * aceitam a forma simples.
     */
    fun lock(locked: Boolean, extended: Boolean = false): InmotionCommand {
        val value = if (locked) 1 else 0
        return if (extended) {
            command(InmotionFrameType.EXTENDED, ROUTING_0, ROUTING_1, CONTROL, 0x31, value)
        } else {
            control(0x31, value)
        }
    }

    /**
     * Liga/desliga o farol.
     *
     * Firmwares anteriores ao 1.4.0 usam o subcódigo antigo `0x40`
     * ([legacyHeadlightOpcode] = true); do 1.4.0 em diante, `0x50`.
     */
    fun headlight(on: Boolean, legacyHeadlightOpcode: Boolean = false): InmotionCommand =
        control(if (legacyHeadlightOpcode) 0x40 else 0x50, if (on) 1 else 0)

    /**
     * Aciona a buzina com som e volume explícitos.
     *
     * Prefira [hornFor], que escolhe o opcode correto por modelo.
     */
    fun horn(soundId: Int = 0x18, volume: Int = 1): InmotionCommand =
        control(0x51, soundId, volume)

    /**
     * Buzina com o opcode correto para o modelo.
     *
     * A família V13/V14 (incluindo a **V11y**) usa `playBeep` `(0x02, 100)`;
     * V9/V11/V12/V12S usam `playSound` `(0x18, 1)`. O som 0x18 na V11y é um
     * tom contínuo e não deve ser usado (bug existente no EUC World 2.66.0).
     */
    fun hornFor(model: InmotionModel?): InmotionCommand = when (model) {
        InmotionModel.V9,
        InmotionModel.V11,
        InmotionModel.V12,
        InmotionModel.V12S,
        InmotionModel.P6,
        -> horn(soundId = 0x18, volume = 1)

        // V11Y, V13, V14, E20 e desconhecidos: forma moderna.
        else -> horn(soundId = 0x02, volume = 100)
    }

    /** Define o limite de velocidade (tiltback), em km/h. */
    fun speedTiltback(kmh: Double): InmotionCommand =
        controlWithU16(0x21, kmh * 100)

    /** Define a inclinação dos pedais (tiltback horizontal), em graus. */
    fun pedalTilt(degrees: Double): InmotionCommand =
        controlWithU16(0x22, degrees * 100)

    /** Define o modo de condução (0 = comfort, 1 = classic, conforme a roda). */
    fun ridingMode(mode: Int): InmotionCommand = control(0x23, mode)

    /** Liga/desliga o modo performance. */
    fun performanceMode(on: Boolean): InmotionCommand = control(0x24, if (on) 1 else 0)

    /**
     * Sensibilidade dos pedais (0-100).
     *
     * O app oficial envia o valor seguido do teto 100 (e o inverso na V9).
     */
    fun pedalSensitivity(percent: Int, reversed: Boolean = false): InmotionCommand {
        val value = percent.coerceIn(0, 100)
        return if (reversed) control(0x25, 100, value) else control(0x25, value, 100)
    }

    /** Volume do alto-falante (0-100). */
    fun speakerVolume(percent: Int): InmotionCommand = control(0x26, percent.coerceIn(0, 100))

    /** Temporizador de standby, em segundos. */
    fun standbyTimer(seconds: Int): InmotionCommand = controlWithU16(0x28, seconds.toDouble())

    /** Brilho do farol (0-100). Não disponível na V12. */
    fun headlightBrightness(percent: Int): InmotionCommand =
        control(0x2B, percent.coerceIn(0, 100))

    /** Liga/desliga os sons da roda. */
    fun sounds(on: Boolean): InmotionCommand = control(0x2C, if (on) 1 else 0)

    /** Liga/desliga as luzes diurnas (DRL). Disponível em V11Y/V13/V14. */
    fun daytimeRunningLights(on: Boolean): InmotionCommand = control(0x2D, if (on) 1 else 0)

    /** Habilita/desabilita o botão da alça (lift sensor). */
    fun handleButton(enabled: Boolean): InmotionCommand = control(0x2E, if (enabled) 1 else 0)

    /** Liga/desliga o modo transporte. */
    fun transportationMode(on: Boolean): InmotionCommand = control(0x32, if (on) 1 else 0)

    /** Habilita/desabilita a detecção de motor sem carga. */
    fun motorNoLoadDetection(on: Boolean): InmotionCommand = control(0x36, if (on) 1 else 0)

    /** Modo "go home": permite rodar com bateria baixa. */
    fun lowBatteryRiding(on: Boolean): InmotionCommand = control(0x37, if (on) 1 else 0)

    /**
     * Liga/desliga o ventilador.
     *
     * [quietMode] usa o subcódigo `0x38` (V11/V11Y); [modernFanOpcode] seleciona
     * `0x53` (firmware 1.4.0+) em vez de `0x43`.
     */
    fun coolingFan(
        on: Boolean,
        quietMode: Boolean = false,
        modernFanOpcode: Boolean = true,
    ): InmotionCommand {
        val value = if (on) 1 else 0
        return when {
            quietMode -> control(0x38, value)
            modernFanOpcode -> control(0x53, value)
            else -> control(0x43, value)
        }
    }

    /** Liga/desliga os sons do motor. */
    fun motorSound(on: Boolean): InmotionCommand = control(0x39, if (on) 1 else 0)

    /** Liga/desliga o desligamento automático da tela (V12). */
    fun screenAutoOff(on: Boolean): InmotionCommand = control(0x3D, if (on) 1 else 0)

    /**
     * Liga/desliga os modos de condução separados (split riding modes).
     *
     * V11Y usa `0x3E`; V9/V12 usam `0x42`.
     */
    fun splitRidingModes(enabled: Boolean, legacyOpcode: Boolean = false): InmotionCommand =
        control(if (legacyOpcode) 0x42 else 0x3E, if (enabled) 1 else 0)

    /**
     * Ajusta aceleração/frenagem dos modos separados (0-100 cada).
     *
     * V11Y usa `0x3F`; V9/V12 usam `0x40`.
     */
    fun splitRidingModesSettings(
        acceleration: Int,
        braking: Int,
        legacyOpcode: Boolean = false,
    ): InmotionCommand = control(
        if (legacyOpcode) 0x40 else 0x3F,
        acceleration.coerceIn(0, 100),
        braking.coerceIn(0, 100),
    )

    /** Limite de inclinação lateral estendido (V12+). */
    fun extendedLateralTiltLimit(on: Boolean): InmotionCommand = control(0x45, if (on) 1 else 0)

    /**
     * Alarmes de velocidade (apenas V9/V12): dois limites em km/h.
     *
     * Na V11Y os alarmes de velocidade são configurados apenas no app.
     */
    fun speedAlarms(firstKmh: Double, secondKmh: Double): InmotionCommand =
        controlWithU16Pair(0x3E, firstKmh * 100, secondKmh * 100)

    /**
     * Calibração: `yaw` (giroscópio) ou `pedals` (ponto zero dos pedais).
     */
    fun calibration(yaw: Boolean): InmotionCommand =
        if (yaw) control(0x52, 0x01, 0x00, 0x01) else control(0x52, 0x01, 0x01, 0x00)

    // ---------------------------------------------------------------- internos

    private fun command(type: InmotionFrameType, vararg bytes: Int): InmotionCommand =
        InmotionCommand(type, ByteArray(bytes.size) { bytes[it].toByte() })

    private fun control(vararg bytes: Int): InmotionCommand =
        command(InmotionFrameType.DEFAULT, CONTROL, *bytes)

    private fun controlWithU16(subCode: Int, value: Double): InmotionCommand {
        val scaled = value.toInt() and 0xFFFF
        return control(subCode, scaled and 0xFF, (scaled shr 8) and 0xFF)
    }

    private fun controlWithU16Pair(subCode: Int, first: Double, second: Double): InmotionCommand {
        val a = first.toInt() and 0xFFFF
        val b = second.toInt() and 0xFFFF
        return control(
            subCode,
            a and 0xFF, (a shr 8) and 0xFF,
            b and 0xFF, (b shr 8) and 0xFF,
        )
    }
}
