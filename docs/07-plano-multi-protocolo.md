# Plano — Suporte multi-protocolo (todas as marcas e modelos)

> Base: extração do EUC World 2.66.0 (adaptadores, tabela de detecção, telas por modelo) +
> documentação comunitária (eucplanet/MIT, WheelLog/GPL, eucWatch).
> Objetivo: arquitetura que permita suportar **qualquer** monociclo, com o Inmotion V11y como caso piloto.

## 1. Arquitetura multi-protocolo

```
┌─────────────────────────────────────────────────────────────┐
│  UI (Compose) — dashboard, settings da roda, alarmes, tours  │
├─────────────────────────────────────────────────────────────┤
│  WheelRepository — valores ao vivo (catálogo de 223 slots)   │
├─────────────────────────────────────────────────────────────┤
│  WheelSession — ciclo de vida: connect → init → poll → cmd   │
├─────────────────────────────────────────────────────────────┤
│  WheelAdapter (interface)                                    │
│    ├─ InmotionV2Adapter   (V11/V11y/V12/V13/V14/V9/E20/P6)   │
│    ├─ InmotionV1Adapter   (V5/V8/V10/L6/Solowheel)           │
│    ├─ KingSongAdapter     (KS-14…S22, Rockwheel, GT16)       │
│    ├─ GotwayAdapter       (Gotway/Begode/Extreme Bull)       │
│    ├─ VeteranAdapter      (Sherman/Patton/Lynx/Abrams)       │
│    ├─ NinebotAdapter      (One S2/A1)                        │
│    └─ NinebotZAdapter     (One Z)                            │
├─────────────────────────────────────────────────────────────┤
│  Protocol primitives: framing, checksum/CRC, escape,         │
│  command queue (timeout/retry), buffer de reassembly          │
├─────────────────────────────────────────────────────────────┤
│  BleTransport — scan, GATT, notify, write, reconexão, MTU    │
└─────────────────────────────────────────────────────────────┘
```

### 1.1 Interface `WheelAdapter`

```kotlin
interface WheelAdapter {
    val protocolId: ProtocolId            // enum: INMOTION, INMOTION_NEW, KINGSONG, GOTWAY, VETERAN, NINEBOT, NINEBOT_NEW
    val capabilities: WheelCapabilities   // o que este modelo suporta (ver §5)

    fun onConnected(characteristics: GattChannels)  // inicia sequência de init
    fun onNotification(data: ByteArray)              // reassembly + parse
    suspend fun send(command: WheelCommand)          // fila com timeout/retry
    fun currentTelemetry(): TelemetrySnapshot
}

data class GattChannels(
    val write: BluetoothGattCharacteristic,
    val notify: BluetoothGattCharacteristic,
    val writeType: Int,           // default ou no-response
    val chunkSize: Int = 20,      // alguns protocolos exigem chunks de 20 B
)
```

### 1.2 Primitivas de protocolo (reutilizáveis)

| Primitiva | Usada por |
|---|---|
| Framing `AA AA <type> <len> <payload> <XOR>` + escape `A5` | Inmotion V2 |
| Framing CAN `AA AA <16B prefix> <payload> <sum> 55 55` + escape `A5` | Inmotion V1 |
| Framing `AA 55 … 5A 5A` (20 bytes) | KingSong |
| Comandos de 1 byte ASCII + resposta `55 AA` | Gotway/Begode |
| Framing `4C <cmd> 41 70 <len> <payload> <CRC32 BE>` | Veteran |
| Framing `55 AA <len> <payload> <checksum>` | Ninebot (S2/A1/Z) |
| Reassembly de notificações BLE (frames partidos) | **todos** |

## 2. Detecção automática (tabela extraída do APK)

O EUC World usa "BT definitions" (nome + serviços GATT esperados) e cacheia o protocolo por dispositivo.
Tabela completa em `analysis/07-bt-definitions.txt`. Resumo:

| Definição | Serviços esperados | Protocolo |
|---|---|---|
| `gotway_v1` | 1800, 1801, 180A, FFE0/FFE1 | Gotway (+ Rockwheel se nome "RW" → KingSong) |
| `gotway_v2/v3` | + FFF0/FFF1 + DFU `1d14d6ee…` | Gotway (com DFU) |
| `gotway_ee` | FFE0/FFE1, FFF0 | Gotway (extreme edition?) |
| `kingsong_v1` | 1800, 1801, 180A, FFE0/FFE1, FFF0-FFF5 | KingSong |
| `kingsong_v2` | FFE0 com FFE1+FFE2 | KingSong (2 canais) |
| `kingsong_v3` | + `0783b03e…` (proprietário) + `02f00000…` (DFU) | KingSong novo (S-series) |
| `kingsong_ee` | FFE0/FFE1 + FFF3 | KingSong EE |
| `inmotion` | FFE0/FFE4 (notify) + FFE5/FFE9 (write) + legados | Inmotion V1 |
| `inmotion_new_v1` | NUS `6e400001/2/3` | Inmotion V2 (V11+) |
| `inmotion_new_v2` | NUS + FFE0/FFE5 (transporte duplo) | Inmotion V2 (firmwares com fallback) |
| `inmotion_new_v3` | NUS + `AE00/AE01/AE02` | Inmotion V2 (modelos novos: V14/P6?) |
| `inmotion_new_ee` | NUS + FFF2 | Inmotion EE |
| `ninebot` | FFE0/FFE1 | Ninebot (genérico) |
| `ninebot_new_v1` | NUS + `FEE7` (FEC7/FEC8/FEC9) | Ninebot One S2/A1 |
| `ninebot_new_v2` | NUS apenas | Ninebot One Z |

**Estratégia:** reproduzir a tabela como dados (JSON no app), com fallback: se nenhum match, tentar
NUS → Inmotion V2; FFE0/FFE1 → perguntar marca; permitir **seleção manual** de protocolo/modelo.

## 3. Inventário por marca

### 3.1 Inmotion (V2 — V11/V11y/V12/V13/V14/V9/E20/P6) — ✅ COMPLETO
- **Transporte:** NUS `6e400002` (write) / `6e400003` (notify).
- **Framing:** `AA AA <tipo> <len> <payload> <XOR>`; escape `A5`; tipos `11` INITIAL, `14` DEFAULT, `16` EXTENDED, `13` AUTH.
- **Init:** carType `02 01` → serial `02 02` → firmware `02 06` → settings `20 20` → poll `04` (1 Hz).
- **Telemetria:** offsets por modelo (V11Y: `m11168M`; V14-like), temperaturas `byte−176`.
- **Comandos:** 37 mapeados (`0x60` + sub), incluindo lock `31`, farol `50`, tiltback `21`, calibração `52`.
- **Auth:** handshake `0x13` (obrigatório para lock em V12+).
- **Modelos:** V11, V11Y, V12, V12S, V12 HS/HT/Pro, V13, V13 Pro, V14, V9, E20, P6.
- **Fontes:** nosso `docs/03-protocolo-inmotion.md` + eucplanet (MIT) + WheelLog.

### 3.2 Inmotion (V1 — V5/V8/V10/L6/Solowheel) — ✅ EXTRAÍDO
- **Transporte:** write `FFE5/FFE9` (chunks 20 B), notify `FFE0/FFE4`.
- **Framing:** CAN-over-BLE `AA AA <CAN 16B> <sum mod 256> 55 55`; escape `AA/55/A5`.
- **Ops (22):** INIT_COMM/PASSWORD/STATUS, READ_STATUS/VALUES, WRITE_* (handle button, headlight, horn, leds,
  lock, pedal sensitivity/tilt, riding mode, sound, speaker volume, speed tiltback, transportation mode, turn off).
- **Comandos CAN:** IDs `0x0F550113` (fast info), `0x0F550114` (slow/settings), `0x0F55010D` (farol),
  `0x0F550115` (ride mode/speed/tilt), `0x0F550116` (LED/lock/power off), `0x0F550119` (calibração),
  `0x0F55012E` (handle), `0x0F550307` (PIN), `0x0F550609` (som), `0x0F55060A` (volume).
- **Modelos:** V5/V5F/V5D, V8/V8F/V8S, V10/V10F/V10S/V10T, L6, Glide 3, R-series.
- **PIN:** alguns firmwares exigem PIN de 6 dígitos (CAN `0x0F550307`).
- **Fontes:** nosso APK + eucplanet V1 + WheelLog (`InMotionAdapter.java`).

### 3.3 KingSong — 🟡 EXTRAÍDO (ops), comandos a detalhar
- **Transporte:** `FFE0/FFE1` (v2 usa FFE1+FFE2), v3 com serviço proprietário `0783b03e…`.
- **Framing:** `AA 55 …` 20 bytes com trailer `5A 5A` (RX valida `[0]==0xAA && [1]==0x55`).
- **Ops (~90):** INIT_* (auto-off, battery names, bluetooth audio, charging limit, display content/mode,
  headlight brightness/flashing, language, lateral tilt limit, LED colors, lift sensor (+mode), lock,
  magic light (+mode), name, password, pedal tilt, serial, spectra light (+mode), speed alarms+tiltback),
  READ_* correspondentes, WRITE_* (calibração, etc.), UPDATE_* (firmware + BMS).
- **Modelos:** KS-14D/M/S, KS-16X, KS-18L/XL, KS-F1/F2/F18/F22/F22P, KS-N12P, KS-S2/S9/S16/S18/S19/S20/S22, GT16, Rockwheel (via KS).
- **Fontes:** nosso APK + WheelLog (`KingSongAdapter.java`) + eucplanet.
- **Nota:** o EUC World também atualiza firmware KingSong (ops UPDATE_*).

### 3.4 Gotway/Begode/Extreme Bull — 🟡 EXTRAÍDO (estrutura)
- **Transporte:** `FFE0/FFE1` (v2/v3 com `FFF0/FFF1` + DFU).
- **Protocolo:** comandos de **1 byte ASCII** (`W`, `B`, `g`, `m`, …) → respostas binárias.
- **Ops (4):** DEFAULT, REQUEST_WHEEL_NAME, REQUEST_FIRMWARE_NAME, BOOTLOADER (firmware).
- **Settings:** tela própria (`gw_`): tiltback speed, safety margin alarm, pedal tilt/dip, beeper volume,
  field weakening, safety margin scaling, correção/offset de tensão, LEDs, farol, riding mode.
- **Modelos:** MSuper, Nikola, RS, EX, Monster, Master, Extreme, T4, etc. (+ variante CF com dynamic tiltback).
- **Fontes:** nosso APK + WheelLog (`GotwayAdapter.java`) + eucplanet.

### 3.5 Veteran/LeaperKim — 🟡 EXTRAÍDO (framing)
- **Transporte:** `FFE0/FFE1` + `FFE5/FFE9` (chunks).
- **Framing:** `4C <tipo> 41 70 <len> <payload> <CRC32 big-endian>` (RX valida `[0]==0x4C`).
- **Ops (2):** DEFAULT, BOOTLOADER.
- **Settings (`vn_`):** alarm speed, speed limit, safety margin limit, display backlight, dynamic assist,
  charging voltage limit, correção de tensão, download event log, firmware.
- **Modelos:** Sherman, Sherman S, Abrams, Patton, Lynx.
- **Fontes:** nosso APK + WheelLog (`VeteranAdapter.java`).

### 3.6 Ninebot/Segway — 🟡 EXTRAÍDO
- **One S2/A1** (`C5911d0`): framing `55 AA`, ops (29): INIT_CONFIG/FIRMWARE/LEDS/PASSWORD/PEDAL_SENSITIVITY/
  SERIAL_1..3/SPEED_TILTBACK/SPORTS_MODE/STATUS, READ_* (batt/speed, config, dist, leds, pedal sensitivity,
  speed limit range, speed tiltback, sports mode, status, temperature, tilt/roll, voltage/current), WRITE_* (config, leds, lock, pedal, tiltback, sports).
- **One Z** (`C5915f0`): NUS; callbacks: assistedBraking, battery1/2 Charging/Overheating/Overvoltage,
  brakelight, headlight, ledsMode, liftSensor, lock, pedalSensitivity, speakerVolume, speedAlarm1/2/3(+Enabled), speedTiltback.
- **Modelos:** One S2, One A1, One Z.
- **Fontes:** nosso APK + WheelLog (`NinebotAdapter.java`) + eucplanet.

### 3.7 Outras marcas (oportunidades)
| Marca | Situação | Caminho |
|---|---|---|
| **Rockwheel** | Suportado via protocolo KingSong (nome "RW") | Reusar KingSongAdapter |
| **Solowheel** | Inmotion V1 | Reusar InmotionV1Adapter |
| **Nosfet** (Aero/Apex) | Não suportado pelo EUC World 2.66.0 | Protocolo próprio? Pesquisar/capturar |
| **Inmotion E20/P6 (scooters)** | Suportados (routing estendido) | InmotionV2Adapter com variante |
| Outras marcas novas | — | Arquitetura permite adicionar adapter |

## 4. Modelo de capacidades (por modelo)

```kotlin
data class WheelCapabilities(
    val commands: Set<CommandId>,   // HEADLIGHT, DRL, HORN, LOCK, TILTBACK_SPEED, PEDAL_TILT,
                                    // PEDAL_SENSITIVITY, RIDING_MODE, SPLIT_MODES, VOLUME, FAN,
                                    // TRANSPORT_MODE, HANDLE_BUTTON, CALIBRATION, LEDS, BRAKELIGHT…
    val telemetry: Set<ValueId>,    // subconjunto do catálogo de 223 slots
    val auth: AuthRequirement,      // NONE, INMOTION_V2_HANDSHAKE, PIN_6DIGITS
    val firmwareUpdate: Boolean,
    val battery: BatteryModel,      // packs, células, curva de tensão→%
)
```

- Tabela `model → capabilities` como dados (JSON), gerada a partir das telas `preferences_wheel_*.xml`
  do EUC World + capacidades por protocolo.
- UI de settings da roda é **gerada** a partir das capabilities (não telas hard-coded por modelo).

## 5. Fases de implementação

| Fase | Escopo | Protocolos | Entrega |
|---|---|---|---|
| **1** | Núcleo + Inmotion V2 | Inmotion V2 | Telemetria ao vivo V11y, settings, comandos |
| **2** | Inmotion V1 + auth/extended | Inmotion V1/V2 | Suporte V8/V10 + lock/controles V12+ |
| **3** | KingSong | KingSong (+Rockwheel) | Telemetria + settings + firmware |
| **4** | Gotway/Begode | Gotway | Telemetria + settings |
| **5** | Veteran | Veteran | Telemetria + settings + event log |
| **6** | Ninebot | S2/A1/Z | Telemetria + settings |
| **7** | Extras | — | Seleção manual de protocolo, import/export de captura, emulador de roda para testes |

**Critério de conclusão por protocolo:** telemetria validada contra a roda real (ou captura),
comandos testados um a um, capabilities preenchidas, testes unitários dos parsers com frames reais.

## 6. Testes e validação

1. **Testes unitários** de framing/parsers com frames sintéticos e reais (capturas).
2. **Emulador de roda**: dispositivo BLE (ou app emulador) que reproduz um protocolo — permite testar sem a roda.
3. **Captura BLE**: "Bluetooth HCI snoop log" (Android) → `btsnoop_hci.log`; análise com Wireshark.
   Para cada marca não documentada: capturar sessão do app oficial e extrair frames.
4. **Validação cruzada**: comparar leituras com a tela da própria roda e com o EUC World/WheelLog.
5. **Modo debug no app**: log hex TX/RX, dump de frames desconhecidos (como o EUC World faz).

## 7. Riscos e lacunas

| Risco | Mitigação |
|---|---|
| Firmwares variam dentro do mesmo modelo | Detecção de versão no init; tabelas de offsets por versão; fallback |
| Escalas divergentes entre fontes (ex.: potência V14) | Validar contra a roda; manter tabela de correções |
| Protocolos não documentados (Nosfet etc.) | Captura BLE + modo debug; arquitetura aberta |
| GPL do WheelLog | Usar apenas fatos (offsets/bytes), nunca código; referência de código só do eucplanet (MIT) |
| Regressões ao adicionar protocolos | Suíte de testes por protocolo + emulador |

## 8. Roadmap resumido

1. **Agora:** Fase 1 (Inmotion V2/V11y) — base do app.
2. **Curto prazo:** Fases 2-3 (Inmotion V1 + KingSong) — cobre a maioria dos usuários.
3. **Médio prazo:** Fases 4-6 (Gotway, Veteran, Ninebot) — paridade com o EUC World.
4. **Longo prazo:** marcas novas (Nosfet etc.) + emulador + firmware update.
