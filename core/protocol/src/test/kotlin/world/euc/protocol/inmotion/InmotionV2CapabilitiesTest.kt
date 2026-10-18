package world.euc.protocol.inmotion

import world.euc.protocol.model.WheelSettingId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InmotionV2CapabilitiesTest {

    @Test
    fun `V11y suporta lock mas nao tilt lateral estendido`() {
        val capabilities = InmotionV2Capabilities.forModel(InmotionModel.V11Y)

        assertTrue(WheelSettingId.LOCK in capabilities)
        assertFalse(WheelSettingId.EXTENDED_LATERAL_TILT_LIMIT in capabilities)
    }

    @Test
    fun `V11y suporta DRL, ventilador, brilho e modo de conducao`() {
        val capabilities = InmotionV2Capabilities.forModel(InmotionModel.V11Y)

        assertTrue(WheelSettingId.DAYTIME_RUNNING_LIGHTS in capabilities)
        assertTrue(WheelSettingId.COOLING_FAN in capabilities)
        assertTrue(WheelSettingId.COOLING_FAN_QUIET_MODE in capabilities)
        assertTrue(WheelSettingId.HEADLIGHT_BRIGHTNESS in capabilities)
        assertTrue(WheelSettingId.RIDING_MODE in capabilities)
    }

    @Test
    fun `V13 e V14 suportam tilt lateral estendido e nao DRL`() {
        val v13 = InmotionV2Capabilities.forModel(InmotionModel.V13)
        val v14 = InmotionV2Capabilities.forModel(InmotionModel.V14)

        assertTrue(WheelSettingId.EXTENDED_LATERAL_TILT_LIMIT in v13)
        assertTrue(WheelSettingId.EXTENDED_LATERAL_TILT_LIMIT in v14)
        assertFalse(WheelSettingId.DAYTIME_RUNNING_LIGHTS in v13)
        assertFalse(WheelSettingId.COOLING_FAN in v14)
    }

    @Test
    fun `V12 suporta alarmes de velocidade e nao tilt lateral estendido`() {
        val capabilities = InmotionV2Capabilities.forModel(InmotionModel.V12)

        assertTrue(WheelSettingId.SPEED_ALARMS in capabilities)
        assertFalse(WheelSettingId.EXTENDED_LATERAL_TILT_LIMIT in capabilities)
        assertFalse(WheelSettingId.COOLING_FAN in capabilities)
    }

    @Test
    fun `sem modelo detectado assume o conjunto da V11y`() {
        val capabilities = InmotionV2Capabilities.forModel(null)

        assertTrue(WheelSettingId.COOLING_FAN in capabilities)
        assertFalse(WheelSettingId.EXTENDED_LATERAL_TILT_LIMIT in capabilities)
    }

    @Test
    fun `limite de velocidade maxima segue a especificacao de cada modelo`() {
        assertEquals(60.0, InmotionV2Capabilities.forModel(InmotionModel.V11Y).maxSpeedLimitKmh)
        assertEquals(50.0, InmotionV2Capabilities.forModel(InmotionModel.V11).maxSpeedLimitKmh)
        assertEquals(90.0, InmotionV2Capabilities.forModel(InmotionModel.V14).maxSpeedLimitKmh)
        assertEquals(60.0, InmotionV2Capabilities.forModel(InmotionModel.V12).maxSpeedLimitKmh)
    }

    @Test
    fun `familia V12 mais exige framing estendido nos ajustes protegidos`() {
        // Confirmado na V11y: limite de velocidade e bloqueio em framing padrão
        // são ignorados pelo firmware (sem erro, sem confirmação sonora).
        assertEquals(true, InmotionV2Capabilities.forModel(InmotionModel.V11Y).requiresExtendedFraming)
        assertEquals(true, InmotionV2Capabilities.forModel(InmotionModel.V12).requiresExtendedFraming)
        assertEquals(true, InmotionV2Capabilities.forModel(InmotionModel.V13).requiresExtendedFraming)
        assertEquals(true, InmotionV2Capabilities.forModel(InmotionModel.V14).requiresExtendedFraming)
        assertEquals(false, InmotionV2Capabilities.forModel(InmotionModel.V11).requiresExtendedFraming)
        assertEquals(false, InmotionV2Capabilities.forModel(InmotionModel.V9).requiresExtendedFraming)
    }

    @Test
    fun `todos os modelos suportam limite de velocidade e calibracao`() {
        val models = InmotionModel.entries

        models.forEach { model ->
            val capabilities = InmotionV2Capabilities.forModel(model)
            assertTrue(WheelSettingId.SPEED_LIMIT in capabilities, "falta SPEED_LIMIT em $model")
            assertTrue(WheelSettingId.CALIBRATION in capabilities, "falta CALIBRATION em $model")
        }
    }
}
