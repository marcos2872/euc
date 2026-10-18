# Plano — Suporte multi-protocolo (todas as marcas e modelos)

> Base: extração do EUC World 2.66.0 (adaptadores, tabela de detecção, telas por modelo) +
> documentação comunitária (eucplanet/MIT, WheelLog/GPL, eucWatch).
> Objetivo: arquitetura que permita suportar **qualquer** monociclo, com o Inmotion V11y como caso piloto.
>
> **Docs de protocolo por marca (extração dos apps oficiais, out/2026):**
> [inmotion](protocolos/inmotion.md) · [kingsong](protocolos/kingsong.md) · [begode](protocolos/begode.md) ·
> [leaperkim](protocolos/leaperkim.md) · [nosfet](protocolos/nosfet.md)

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
| Framing `55 AA <len> <src> <dst> <data> <crc16>` | Ninebot One E+/A1/S2 |
| Framing `5A A5 <len> <src> <dst> <cmd> <param> <data> <crc16>` + XOR de 16 B | Ninebot One Z |
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
| `inmotion_new_v3` | NUS + `AE00/AE01/AE02` | Inmotion V2 (modelos novos: V14/P6?) — ⚠️ interpretação alternativa: família `elephant` (AES) no app oficial; pendente de captura ([`protocolos/inmotion.md`](protocolos/inmotion.md)) |
| `inmotion_new_ee` | NUS + FFF2 | Inmotion EE |
| `ninebot` | FFE0/FFE1 | Ninebot (genérico) |
| `ninebot_new_v1` | NUS + `FEE7` (FEC7/FEC8/FEC9) | Ninebot One S2/A1 |
| `ninebot_new_v2` | NUS apenas | Ninebot One Z |

Nomes BLE confirmados nos apps oficiais: Begode contém `GotWay`/`BEGODE`; LeaperKim começa com `LK`;
Nosfet começa com `NF`; KingSong filtra `KS*`/`lamax`/`gt` (E1 identificada por `02 66 66` no advertising);
Inmotion usa NUS (`lorin`/V2) ou FFE5/FFE9 (V1/`ezcan`).

A tabela acima descreve as **BT definitions do EUC World** (serviços *esperados*); os apps oficiais usam
subconjuntos (Begode e LeaperKim usam só `FFE0/FFE1`; KingSong `FFE0/FFE1` + fallback `AD00/AD01/AD02`).

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
- **App oficial 8.1.34 (Flutter/Dart 2.19.2):** protocolo em `libapp.so` do XAPK — 231 geradores `gen*Msg`
  (69 de escrita), 3 famílias (`lorin`=V2, `ezcan`=V1, `elephant`=AES); o app oficial faz auth/PIN
  (`genRequestEncryptedKeyMsg`/`genVerifyPwdMsg`); BMS nativo até 4 packs; V11Y é classe própria e
  V9 não existe no app. Detalhes em [`protocolos/inmotion.md`](protocolos/inmotion.md).

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

### 3.3 KingSong — ✅ DOC COMPLETO
- **Transporte:** `FFE0/FFE1` (write type 1 + notify), fallback `AD00/AD01/AD02`, DFU `02f00000-…-ff02`;
  MTU 512, blocos de 20 B, fila com 50 ms entre pacotes, 3 retries/5 falhas, watchdog 1 s.
- **Framing:** quadro de 20 B `AA 55 <16B> <tail>`; comando em `[16]`; formato clássico `[17]=0x14` + `5A 5A`,
  formato novo com CRC-16/CCITT-FALSE (0x1021, init 0, BE) em `[18..19]`, ou soma 16-bit LE (NFC/luzes);
  quadros de nome/serial com payload anexado.
- **Comandos:** ~150 construtores em `defpackage.fx` (~90 ops nomeadas no EUC World): alarmes/tiltback
  (0x85/0xA4/0xB5), farol (0x53/0x54/0x73), luzes (0x59/0x6C/0x6D/0x7D/0x8A/0x96/0x97), lock/senha
  (0x5D/0x41/0xA5, algoritmo dos 6 dígitos extraído), lift sensor (0x7E/0x81), carga (0x8A sub 8/9),
  display (0x1B/0x8A sub 10/11), pedal/lateral tilt, riding mode, BMS update (família `[16]=0x74`).
- **Telemetria:** `0xA9` (tensão/velocidade/odômetro word-swapped u32/1000/corrente/temp/células), `0xB9`
  (viagem/tempo/média/temps), `0xB1`/`0xBE`/`0xB3`/`0xBB`; família alternativa `F1 EF` (0xC2/0xC5/0xC9).
- **BMS:** páginas `0xF1/0xF2` + `[17]` (BMS1_1..13, BMS2_1..13), 7 células u16 LE por página + resumo do pack.
- **Modelos:** protocolo único; capacidades/packs/limites vêm do servidor (`MachineConfigBean`); handshake
  extra em S20/S22.
- **Doc:** [`protocolos/kingsong.md`](protocolos/kingsong.md).

### 3.4 Gotway/Begode/Extreme Bull — ✅ DOC COMPLETO
- **Transporte:** só `FFE0/FFE1` (write+notify); `FFF6` declarada e não usada; **sem** `FFF0/FFF1`, **sem** DFU
  separado, **sem** auth/checksum. Chunks de `MTU−3` (20 B), 5 ms entre chunks, 10 ms entre requests.
- **Init:** habilitar notify → `N` → `NAME:<nome>`; `V` → 9 bytes ASCII de firmware (`GW`/`JL`/`JN`/`CF`;
  CF = dynamic tiltback). Scan aceita só nome contendo `GotWay`/`BEGODE`.
- **Protocolo:** comandos ASCII de 1 byte com sleeps (`N,V,g,m,s,f,h,b,Q/T/E/R,o/u/i,x/e,>/=/<,c+y,"`, `+/-`,
  `!`,`@`, `W B/M/C/X/R/U/P tt/Y tu`) → respostas de 24 B `55 AA <16B> <[18] tipo> <[19]=0x18|página> 5A5A5A5A`,
  **big-endian**, sem CRC.
- **Telemetria:** 5 tipos de quadro; tensão ×0,01 V (tipo 0) e ×0,1 V (tipo 1 — escalas divergentes),
  velocidade ×0,036 km/h, trip m, corrente ×0,01 A, temp = v/340+36,53, odômetro u32 m, PWM; % de bateria por
  tabela fixa no app (6550 = 100 % … 5320 = 10 %).
- **BMS:** detalhado — tipos 2/3/5/6 = packs 1..4 (célula `i = página*8+n` em mV) e tipo 1 páginas 0..6 = info por board.
- **Firmware:** `!`/`@` alternados → YMODEM (1024 B, CRC16, MD5) na mesma `FFE1`.
- **Modelos:** o app não tem lista (usa `devideType` + firmware); 93 modelos com flags de Smart BMS na referência EUC World.
- **Doc:** [`protocolos/begode.md`](protocolos/begode.md).

### 3.5 Veteran/LeaperKim — ✅ DOC COMPLETO
- **Transporte:** **somente `FFE0/FFE1`** (sem `FFE5/FFE9`); MTU 120, chunks de 20 B com resposta; scan por nome `LK`.
- **Framing:** TX `4C <6B|64> 41 70 <len> <op> <alvo> <corpo> CRC32 BE` (sem byte de nº de comando; `0x80` =
  não alterar; HW `004x` recebe só `6B`); RX header `DC 5A 5C` com tipo no offset 46, i16/u16 big-endian,
  distâncias u32 palavra-baixa-primeiro em metros; o app não valida CRC. Sem auth, sem polling.
- **Comandos:** 26 mapeados (alarme, tiltback, ride mode contínuo/discreto, farol, buzzer, zerar viagem,
  desligar 10 s, corte lateral, transporte, ângulo, gyro, pedal, PWM, backlight, unidade, correção de tensão,
  low/high speed, volume, tensão de carga, assist, redução do acelerômetro, freio, hora, senha op 0/1/2/3/11).
- **BMS:** 2 packs × 3 frames (até 120 células, 6 temps, corrente por pack).
- **Modelos:** 8 rodas por hardware code (Sherman `0010` → Patton-S `0070`) + tabela SOC de 100 pontos; firmware
  via `AT+RINTOPRO` + `.bin`; event log por frames 32/33.
- **Doc:** [`protocolos/leaperkim.md`](protocolos/leaperkim.md).

### 3.6 Ninebot/Segway — ❌ fora de escopo
- A Ninebot/Segway descontinuou a linha de monociclos e o app oficial atual (`com.ninebot.segway`,
  "Segway Mobility") não suporta EUC — marca removida do escopo do projeto.

### 3.7 Outras marcas (oportunidades)
| Marca | Situação | Caminho |
|---|---|---|
| **Rockwheel** | Suportado via protocolo KingSong (nome "RW") | Reusar KingSongAdapter |
| **Solowheel** | Inmotion V1 | Reusar InmotionV1Adapter |
| **Nosfet** (Apex/Aero/Aeon/Xeno) | ✅ Doc completo — o app é **fork do LeaperKim** (protocolo em `com.laoniao.leaperkim`) | Reusar `VeteranAdapter` com ajustes — [`protocolos/nosfet.md`](protocolos/nosfet.md) |
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
| ~~6~~ | ~~Ninebot~~ | ❌ fora de escopo (ver §3.6) | — |
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
3. **Médio prazo:** Fases 4-5 (Gotway, Veteran) — paridade com o EUC World.
4. **Longo prazo:** marcas novas (Nosfet etc.) + emulador + firmware update.
