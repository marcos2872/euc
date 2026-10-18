# Protocolo Inmotion — app oficial 8.1.34 (fonte primária)

> **Proveniência:** APK oficial `com.inmotion.android.app` **8.1.34** (versionCode 9302),
> baixado do APKPure. APK base sha256 `aaece4f10a487e9eea40e2f645ea82b3ba5f8f07675be91e2904cc345abdb69c`
> (45.457.673 B). Análise estática em **2026-10-18**.
>
> **Atenção de método:** o APK base é um *split* de App Bundle
> (`android:requiredSplitTypes="base__abi,base__density"`) e **não contém bibliotecas
> nativas**. O app é Flutter e todo o protocolo está em Dart, compilado em
> `lib/armeabi-v7a/libapp.so` do split de ABI. Para esta análise foi usado o XAPK
> oficial (sha256 `9f4cebbc2838b89992a6dd3177699e78e7337d84d20726d2de65adb0511a1f8f`),
> split `config.armeabi_v7a.apk` (sha256 `ec7f34dd…ade9b`) e
> `libapp.so` sha256 `92a24d380f129750f4893e92e823b878db531dee5483936b1d66c07d015263f6`.
> Snapshot Dart `adb4292f3ec25074ca70abcd2d5c7251` = **Dart 2.19.2 / Flutter 3.7.x**.
>
> **Marcadores:** **[APK]** = observado no artefato (string/classe/método; `arquivo:linha`
> em `analysis/inmotion/`). **[COM]** = doc comunitário/`docs/03-protocolo-inmotion.md`
> (EUC World/eucplanet/WheelLog) — **os bytes de comando e offsets desta doc vêm daí**.
> **[INF]** = inferência. **Lacuna** = não recuperável estaticamente neste APK.
>
> Este documento **complementa** `docs/03-protocolo-inmotion.md` (não repete o que já
> está validado lá): foco em validar/corrigir com o app do fabricante e listar o que o
> app oficial tem além do EUC World.

---

## 1. Visão geral

O app 8.1.34 implementa **três famílias de protocolo** em Dart, sob
`package:inmotion_protocol/`:

| Família | Classes-âncora [APK] | Transporte | Equivalente no doc 03 | Modelos |
|---|---|---|---|---|
| **`lorin`** | `LorinProtocol`, `LorinComposer`, `LorinDecoder`, `LorinParser`, `LorinMessage{format: 0x…`, `LorinMessageFactory` | NUS (V2) | **V2** (V11+) | V6, V11/V11Y, V12, V13, V14, V18, X1, P6, E10, E20, E25 + scooters A1/C1/LE1/LR1/RS Pro/RS Velo/S1 |
| **`ezcan`** | `EzcanMessage{id: 0x…`, `_EzcanComposer`, `EzcanEncoder`, `EzcanProtocol`, `EzCANTotalStatistics`, `EzCANHistoryStatistics` | FFE0/FFE5 (CAN) | **V1** (pré-V11) | V5, V8, V10-like, L8, R1, E (SCV), e-bikes P1/P2 |
| **`elephant`** | `ElephantProtocol`, `ElephantEncoder`, `ElephantDecoder`, `ElephantConstant`, `ElephantMessage`, `isElephantModel`, `elephant/cryptor` | AE00/AE01/AE02 **[INF]** | — (não existe no doc 03) | modelo genérico (bean único `real_time_info`/`settings`/`statistics`), com **AES** |

Camadas comuns: `base/protocol`, `base/codec`, `base/base_message`, `base/vehicle_data`,
`codec_holder`, `handler/send_handler`, `handler/received_handler`, `protocol_worker`,
`worker/worker_context`, `common/product_model`, `common/fw_update_manager`,
`util/byte_util`, `util/int8_list_buffer`, `util/aes`.

[INF] Transporte por família: `lorin` → NUS `6e400002/3`; `ezcan` → `FFE0/FFE5`
(CAN); `elephant` → `AE00/AE01/AE02`. O app tem três conjuntos de UUID
(`_bleUuid1/2/3`), mas a associação com o modelo está em código.

**Deltas relevantes vs. doc 03 / doc 07**

1. O nome interno da família V2 é **`lorin`**; o app tem um **codec por modelo**
   (`lorin/bean/euc/<modelo>/...`), não um switch único.
2. Existe uma terceira família, **`elephant`**, com criptografia AES — não documentada
   em nenhuma fonte comunitária usada no doc 03.
3. O app **implementa autenticação** (`genRequestEncryptedKeyMsg`,
   `genVerifyCryptKeyMsg`, `genVerifyPwdMsg`, `authChallenge`,
   `getFeaturesNeedSecurityConnection`) — o oposto do EUC World 2.66.0.
4. Linha de modelos bem maior que a do doc 03: inclui **V18, X1, E25, V6, V10-like**
   e uma linha de scooters (A1, C1, L8, LE1, LR1, RS Pro, RS Velo, S1), SCV (E, R1) e
   e-bikes (P1, P2-like).
5. **V11Y é modelo de primeira classe** (`isV11Y`, `_V11YCmdDefault`,
   `_V11YSettingsDefault`, `_DefaultV11YRealTimeInfo`) — confirma o tratamento
   específico do doc 03 §9.1.
6. Há **variantes de conjunto de comandos por geração/firmware**:
   `_V11CmdV137`, `_V12CmdV1/_V12CmdV2`, `_V18CmdV1/_V18CmdV2`, `_X1CmdV1/_X1CmdV2`,
   `_P6CmdV1/_P6CmdV2`, `_E25CmdV1/_E25CmdV2` — [INF] correlaciona com a mudança de
   framing default↔estendido e com revisões de firmware.

---

## 2. Transporte BLE

### 2.1 UUIDs presentes no app [APK]

`analysis/inmotion/ble_uuids.txt`:

| Serviço | Característica | Papel | Linha em `libapp_strings.txt` |
|---|---|---|---|
| `0000ffe0-…` | `0000ffe4-…` | notify (V1) | 18930 / 13569 |
| `0000ffe5-…` | `0000ffe9-…` | write (V1) | 52952 / 44279 |
| `6e400001-…` | `6e400002-…` | write (V2, NUS) | 51415 / 25945 |
| `6e400001-…` | `6e400003-…` | notify (V2, NUS) | 51415 / 15808 |
| `0000ae00-…` | `0000ae01-…` | serviço/característica novos | 14225 / 20293 |
| — | `0000ae02-…` | terceira característica do conjunto AE00 | 42058 |

Configuração por modelo: o app tem **três conjuntos** de UUID —
`BleUuid` (9816), `_bleUuid1` (28776), `_bleUuid2` (54322), `_bleUuid3` (42165) —
e as chaves `ble_uuid_config` (30360), `write_service_uuid`, `notify_service_uuid`,
`write_characteristic_uuid`, `notify_characteristic_uuid`, `cccdUuid`.
**Lacuna:** o mapeamento `modelo → _bleUuidN` está em código (não no pool).
[INF] `_bleUuid1` = V1 (FFE0/FFE5), `_bleUuid2` = V2 (NUS), `_bleUuid3` = geração
AE00/AE01 (provável transporte do `elephant`).

### 2.2 Escrita, MTU e chunking

[APK] Existem `_mtuValues`, `fixedMtu`, `chunkSize`, `BmMtuChangeRequest`,
`BmMtuChangedResponse`, `BmWriteCharacteristicRequest`, `BmSetNotifyValueRequest`,
`BmConnectionStateResponse`, `BmScanResponse`, `BmBondStateResponse`.
**Lacuna:** os valores (20 B? MTU negociado?) e o write type (default × no-response)
não são recuperáveis sem disassembly; **validar em captura**.

[COM] doc 03 §1: V1 escreve em blocos de 20 B; V2 usa NUS.

### 2.3 Scan e detecção

[APK] `BleDeviceNameFilter`, `BleDeviceFilter`, `BleDeviceEmptyFilter`,
`ScanResult{device:`, `advertisementData`, `serviceUuids`, `manufacturerData`,
`bt_adv_protocol_version`.
[APK] Detecção é por **módulo por modelo**: `pages/vehicle/service/euc/<modelo>/<modelo>_detection.dart`
(94 URIs em `vehicle_service_uris.txt`), com classes `V11Detection`, `V12Detection`,
`V13Detection`, `V14Detection`, `P6Detection`, `X1Detection`, `A1Detection`,
`BMSDetection`, `VehicleDetectionBase`, e o combinador
`_V14Detection&VehicleDetectionBase&BMSDetection`.
[APK] `ProductModel.fromHexCode`, `ProductModel.fromMap`, `ProductModel.fromEzcan`,
`ProductModel.fromElephant`, `ProductModel{hexCode: ` — o modelo é derivado do
**hexCode** reportado pelo veículo e/ou do tipo de mensagem.
**Lacuna:** a tabela `hexCode → modelo` está em código.
[COM] doc 03 §3: carType reportado em `02 01` (payload[3]); doc 07 §2: tabela de
serviços esperados por definição BLE.

---

## 3. Conexão e init

### 3.1 Serviços/classes do app [APK]

`pages/vehicle/service/vehicle_connect_service`, `vehicle_state_service`,
`vehicle_data_service`, `vehicle_bms_service`, `package_plugin_service`;
`handler/send_handler` + `handler/received_handler` + `protocol_worker` (fila);
`MsgTimeoutException` (23900), `maxRetryTimes` (23530), `connectionTimeoutMillis`,
`autoSetupTimeoutMills`, `byModelTimeout`, `bySNTimeout`, `bySoftwareVersionTimeout`,
`byHardwareVersionTimeout`, `byDeviceInfoTimeout`, `byActivateTimeTimeout`.

### 3.2 Sequência V2 (`lorin`) — o que o app oficial acrescenta

[COM] doc 03 §3 (ordem validada em eucplanet e espelhada pelo app oficial):
`02 01` (carType) → `02 02` (serial) → `02 06` (firmware) → `20 20` (settings) →
`04` (poll de valores, ~1 Hz) → `10 00 01` (viagem) → `11` (totais) → `05` (bateria) →
packs 2/3/4 → tensões de célula → status de pack.

[APK] Comandos de identificação/init presentes: `genRequestModelMsg`, `genGetModelMsg`,
`genGetSnMsg`, `genGetHwVersionMsg`, `genGetFwVersionMsg`, `genGetMotorSnMsg`,
`genGetBatterySnMsg`, `genGetDeviceInfoMsg`, `genGetIdentifierMsg`,
`genGetActivateDateMsg`, `genActivateMsg`, `genQuickConnectAckMsg`.

**Delta do app oficial:** existe um **`genQuickConnectAckMsg`** (6933) e um bloco de
segurança — ver §3.3. O doc 03 não tem o quick-connect.

### 3.3 Autenticação / senha (prioridade desta análise)

[APK] Evidências de que o app oficial faz handshake e/ou valida senha:

| Token | Linha | Interpretação |
|---|---|---|
| `authChallenge` | 7186 | desafio de autenticação |
| `genRequestEncryptedKeyMsg` | 6519 | pede chave/crypto key à roda |
| `genVerifyCryptKeyMsg` | 31082 | envia/verifica a chave |
| `genVerifyPwdMsg` | 24779 | verifica PIN/senha |
| `genSetPasswordMsg` / `genSetPasswordTypeMsg` | 49084 / — | define senha e tipo |
| `genRequestPasswordTypeMsg` | 21764 | pergunta o tipo de senha |
| `getFeaturesNeedSecurityConnection` | 16032 | **features que exigem conexão segura** |
| `getVehicleFeatureList` | 14982 | lista de capacidades do modelo |
| `ElephantConstant` / `elephant/cryptor` / `util/aes` / `AesHeader` | 25005 | família `elephant` usa AES |

[COM] doc 03 §4 (bytes exatos, ainda não confirmados no app oficial):

```
1) phone → roda : AA AA 13 04 02 00 00 02 17          (requestAuthKey)
2) roda → phone : 02 80 02 <16 bytes>                  (blob)
3) phone → roda : AA AA 13 14 02 00 00 82 <blob> CK    (echo)
4) roda → phone : 02 80 82 01                          (sucesso)
```

[INF] O par `genRequestEncryptedKeyMsg`/`genVerifyCryptKeyMsg` é o mesmo handshake
(request/echo) do doc 03 §4; `genVerifyPwdMsg` é o PIN de 6 dígitos (família V1/CAN,
`0x0F550307`) e/ou senha de modelo novo. **Lacuna:** os bytes exatos do handshake do
app oficial (blob, tamanho, CK) exigem captura; o doc 03 §4 continua sendo a melhor
referência.

[APK] `getFeaturesNeedSecurityConnection` + `genLockMsg` sustentam a regra do doc 03:
**lock/comandos sensíveis só depois de autenticar** (V12+/P6).

### 3.4 V1 (`ezcan`)

[APK] `genRequestFastInfoMsg` / `genRequestSlowInfoMsg` (fast/slow info),
`genLocalMsg`/`genGeneralMsg` (envelope genérico) e as classes `V5Detection`,
`V8Detection`, `V10LikeDetection`, `L8Settings`, `R1Settings`, `V5RealTimeState`,
`V8RealTimeState`.
[COM] doc 07 §3.2 / doc 03 §1: CAN IDs `0x0F550113` (fast), `0x0F550114` (slow),
`0x0F55010D` (farol), `0x0F550115` (ride mode/velocidade/tilt), `0x0F550116`
(LED/lock/power off), `0x0F550119` (calibração), `0x0F55012E` (handle),
`0x0F550307` (PIN), `0x0F550609` (som), `0x0F55060A` (volume); framing
`AA AA <CAN 16B> <sum> 55 55` + escape. **O app oficial ainda suporta V1** (V5/V8/
V10-like/L8/R1), o que valida manter os dois adaptadores.

---

## 4. Framing

### 4.1 Formato (V2/`lorin`)

[COM] doc 03 §2 — formato de quadro (payload não escapado):

```
AA AA <tipo> <len> <payload…> <XOR>
```

| Offset | Tam | Campo |
|---|---|---|
| 0–1 | 2 | `AA AA` (head) |
| 2 | 1 | tipo: `0x11` INITIAL, `0x13` AUTH, `0x14` DEFAULT, `0x16` EXTENDED |
| 3 | 1 | `len` do payload |
| 4..N | N | payload |
| N+1 | 1 | XOR de todos os bytes anteriores |

[APK] Nomes que confirmam a estrutura no app oficial: `findPacketHead` (21126),
`isStartWithPacketHead` (10862), `findValidPacketEndIndex` (23380),
`lastEscapeIndex`, `validPacketEndAt`, `unescape` (24191), getter `_checkSum`
(54357), `chunkStringEscape`, `LorinMessage{format: 0x…` (toString imprime o tipo em
hex), `LorinComposer` (6158), `LorinDecoder` (5890), `LorinParser` (4892).
→ **[INF]** `format` = o byte de tipo; o parser localiza head, valida fim, remove
escape e calcula checksum — compatível com o doc 03.

### 4.2 "Standard" × "Extra" (default × estendido)

[APK] O app nomeia os dois modos de escrita/leitura:

| Gerador | Linha | Leitura |
|---|---|---|
| `genLocalStandardReadMsg` | 50947 | leitura em framing **standard** (default) |
| `genLocalStandardWriteMsg` | 16065 | escrita em framing **standard** |
| `genLocalExtraWriteMsg` | 25494 | escrita em framing **extra** (estendido) |
| `genLocalMsg` | 6700 | despacho genérico |

[INF] "standard" = `0x14`; "extra" = `0x16` com routing `02 21`/`02 24…27`. Isso
explica o bug do doc 03 §6/§9.1 (tiltback e lock ignorados em default na V11y/V12+):
o app oficial usa o caminho *extra*.

### 4.3 Exemplos hex

[COM] doc 03 §2/§9.1 (payload não escapado; `CK` = XOR):

```
tiltback V11y (estendido) : AA AA 16 08 02 21 60 21 30 00 00 00 CK   (30 km/h)
lock (V12+)               : AA AA 16 05 02 21 60 31 01 CK
horn V11Y/V13/V14         : AA AA 16 06 02 21 60 51 02 64 CK
horn V9/V11/V12/V12S      : AA AA 16 06 02 21 60 51 18 01 CK
```

> ⚠️ **Correção de notação do doc 03:** a tabela do §6 lista vários payloads com
> prefixo `96` (ex.: `96 21 <u16>`), enquanto as capturas validadas (§9.1) e o
> eucplanet usam `60`. `0x96 = 0x80 | 0x16` é o **opcode de resposta** (o mesmo padrão
> de `0x90`/`0x91` = `0x80|0x10`/`0x80|0x11`); `0x60` é o opcode de **comando**.
> Ler `96` como resposta e `60` como escrita é consistente com todos os quadros
> `02 21 60 xx …` — **[INF]**, confirmar em captura.

### 4.4 V1 (`ezcan`)

[COM] doc 07 §3.2: `AA AA <CAN 16 B> <soma mod 256> 55 55`, escape `AA/55/A5`,
chunks de 20 B. [APK] `EzcanMessage{id: 0x…` imprime o **CAN id** em hex; há
`BaseCrc`, `_checkSum`, `calCrc32`, `checkCrc32`, `crcOk`, `crc16_x25` (a lib `crclib`
é usada em arquivos/OTA; **não** confirma CRC de quadro).

---

## 5. Telemetria (leitura)

### 5.1 Classes por modelo [APK]

O app tem **um bean de telemetria por modelo** (`package:inmotion_protocol/lorin/bean/…`):

| Modelo | Classes observadas |
|---|---|
| V6 | `V6RealTimeData`, `V6RealTimeInfo`, `V6RealTimeState`, `V6RealTimeError`, `V6Settings`, `V6TotalStatistics`, `V6HistoryStatistics`, `V6Parser`, `V6Composer`, `V6MessageFactory`, `_V6Cmd`/`V6Cmd`, `_V6CmdDefault` |
| V11 / V11Y | `V11RealTimeInfo`, `_DefaultV11YRealTimeInfo`, `_DefaultV11YRealTimeState`, `_DefaultV11YRealTimeError`, `V11RealTimeError`, `V11Settings`, `V11TotalStatistics{mileage:`, `V11HistoryStatistics{index:` |
| V12 | `V12RealTimeInfo`, `V12RealTimeInfoV2`, `V12RealTimeData`, `V12State`, `V12RealTimeError`, `V12Settings`, `V12SettingsV2`, `AllV12RealTimeData`, `V12VehicleSynthesizeRealTimeInfo`, `V12TotalStatistics`, `V12HistoryStatistics{index:` |
| V13 | `V13RealTimeError`, `V13Settings`, `V13TotalStatistics`, `V13HistoryStatistics{index:` |
| V14 | `AllV14RealTimeData`, `V14VehicleSynthesizeRealTimeInfo`, `V14RealTimeError`, `V14Settings` |
| V18 | `V18HistoryStatistics{index:`, `V18Settings{passwordType:`, `V18BMSWarning` |
| X1 | `X1Settings{passwordType:`, `X1BMSError{insulation:`, `X1BMSWarning`, `X1SingleBatteryRealTimeInfo{voltage:` |
| P6 | `P6Settings{passwordType:`, `P6BMSProtection{…}`, `P6BMSGeneralInfo`, `_DefaultP6BMSErrorCompact` |
| E10/E20/E25 | `E10Settings{driveMode:`, `E20Settings{driveMode:`, `E25RealTimeState{pcMode:`, `E25TotalStatistics{mileage:` |
| V5/V8/V10-like | `V5RealTimeState{pcMode:`, `V8RealTimeState{pcMode:`, `V10LikeTotalStatistics{mileage:` |
| Genéricos | `ERealTimeInfo{speed:`, `ERealTimeState{pcMode:`, `ERealTimeError{originalValue:`, `VehicleRealTimeInfo{busVoltage:`, `EucSettings{driveMode:`, `EucStatistics`, `EucTotalStatistics` |

### 5.2 Vocabulário de campos [APK]

Nomes de campos (~todos os que o app exibe/parseia), observados no pool:
`speed`, `voltage`, `busVoltage`, `current`, `power`, `torque`, `PWM`, `temperature`,
`mosTemp`, `motorTemp`, `imuTemp`, `batteryTemp`, `batteryLevel`, `soc`,
`pitchAngle`, `pitchAimAngle`, `rollAngle`, `mileage`, `totalMileage`, `Trip`,
`rideTime`, `totalRideTime`, `averageSpeed`, `maxSpeed`, `speedLimit`,
`powerOffCountdown`, `spinKillState`, `pcMode`, `driveMode`,
`highBeamState`, `tailLightState`, `autoLightBrightnessState`, `bermAngleModeState`,
`communicationState`, `chargingState`, `batteryError`, `realTimeError`,
`estimatedTotalMileage`, `tirePressure`, `batterySn`, `bmsSn`.

### 5.3 Offsets e escalas

[COM] Os offsets completos da família V2 estão em
[`docs/03-protocolo-inmotion.md`](../03-protocolo-inmotion.md) — **§5** (V12/V14/V11 e variantes) e
**§6.1** (odômetros). **Não foram recuperados offsets novos do app oficial**
nesta análise (lacuna de método, ver §11). O que o app oficial **confirma**:

- cada modelo tem parser próprio (não há layout único) — coerente com o doc 03 §5;
- V11Y tem parser/defaults próprios — coerente com o doc 03 §9.1;
- existem `*RealTimeError`, `*RealTimeState`, `*Settings` e `*Statistics` separados
  por modelo — a telemetria é lida em **quatro blocos** (info, state, error, settings)
  além de `Total`/`History`.

[APK] Comandos de leitura: `genGetAllRealTimeInfoMsg`, `genGetRealTimeDataMsg`,
`genRequestRealTimeInfoMsg`, `genRequestRealTimeStateMsg`,
`genRequestRealTimeErrorMsg`, `genRequestVehicleSynthesizeRealTimeInfoMsg`,
`genGetSettingsMsg`, `genRequestSettingsMsg`, `genRequestFastInfoMsg`,
`genRequestSlowInfoMsg`.

[INF] `genRequestFastInfoMsg`/`genRequestSlowInfoMsg` são o equivalente V1 de
"fast info" (telemetria rápida) e "slow info" (settings/estatísticas) do CAN
`0x0F550113`/`0x0F550114` — ver doc 07 §3.2.

---

## 6. BMS

### 6.1 Estrutura [APK]

| Item | Evidência |
|---|---|
| Até **4 packs** | `bms1Board`…`bms4Board`, `bmsCellsVoltage1`…`4`, `bmsFixedInfo1`…`4`, `bmsRealTimeInfo1`…`4` |
| Tensões de célula | `BMSCellsVoltage`, `_BMSCellsVoltage`, `BMSCellsVoltageValue`, `bmsCellsVoltageCmd` |
| Info geral | `BMSGeneralInfo{voltage:`, `BMSFixedInfo`, `BMSInfo`, `BMSState` |
| Serial/Data | `bmsSn`, `bmsDateTime`, `bmsDateCmd` |
| Logs | `BMSLog`, `AbsBmsLogManager`, `AbsSingleBmsLogManager`, `bms_log_manager`, `BMSLogResponse` |
| Capacidade | `FullBMSFeatureCmdTrait` (15300), `SimpleBMSFeatureCmdTrait`, `_checkFullBMSFeatureTrait` |
| Modelos com BMS próprio | `v11_bms_info`, `v13_bms_info`, `v14_bms_info`, `v18_bms_info`, `x1_bms_info`, `p6_bms_info`, `e25_bms_info`, `le1_bms_info`, `lr1_bms_info` |

### 6.2 Comandos [APK]

`genRequestBMSRealTimeInfoMsg` (6080), `genRequestBMSCellsVoltageInfoMsg`,
`genRequestBMSFixedInfoMsg`, `genRequestBMSDateMsg`, `genRequestBMSLogCountMsg`,
`genRequestBMSLogMsg`, `genRequestBatteryRealTimeInfoMsg`, `genGetBatterySnMsg`.

[COM] [`docs/03-protocolo-inmotion.md`](../03-protocolo-inmotion.md) **§7** (bytes): pedidos em `0x16` com routing
`02 24 01/02`, `02 25 …`, `02 26 …`, `02 27 …`; resposta de tensões = **32 × u16 LE
em mV**; status por pack: byte 0 bit0 valid / bit1 enabled; V14 usa média dos packs 1–2
para o %.
[INF] O 4º pack (`bms4Board`) cobre V13/V14/V18; `FullBMSFeature` vs
`SimpleBMSFeature` é o flag de capacidade que decide quantos packs/rotas consultar.

### 6.3 Proteções/erros (chaves de UI) [APK]

O app tem uma tabela completa de proteções e avisos por pack; usar como enum de
decodificação/UI (não são bytes, são chaves de localização):

`bms_protection_cell_over_charge`, `…_cell_over_discharge`, `…_pack_over_charge`,
`…_pack_over_discharge`, `…_charge_over_current`, `…_discharge_over_current`,
`…_charge_bms_over_temp`, `…_discharge_bms_over_temp`, `…_charge_mosfet_over_temperature`,
`…_discharge_mosfet_over_temperature`, `…_charge_cell_low_temperature`,
`…_discharge_cell_low_temperature`, `…_charge_cell_over_temperature`,
`…_discharge_cell_over_temperature`, `…_excessive_voltage_diff`, `…_short_circuit`,
`…_pre_charge_fail`, `…_charge_lock`, `…_discharge_hold`, `…_insulation_fault`,
`…_over_humidity`, `…_associated_charge`, `…_associated_discharge`.

Avisos: `bms_warning_low_soc`, `…_large_voltage_diff`, `…_cell_voltage_difference_high`,
`…_cell_temp_difference_high`, `…_charger_voltage_error`, `…_charge_diode_over_temp`,
`…_battery_communication`, `…_charge_over_current`, `…_discharge_over_current`.
Erros de hardware: `bms_error_afe`, `bms_error_cell`, `bms_error_mcu`,
`bms_error_voltage_sample`, `bms_error_charge_current_sample`,
`bms_error_discharge_current_sample`, `bms_error_charge_mosfet`,
`bms_error_discharge_mosfet`, `bms_error_charge_relay_cannot_close/open`,
`bms_error_discharge_relay_cannot_close/open`, `bms_error_temperature_sensor`,
`bms_error_rh_sensor`, `bms_error_insulation_fault`.

---

## 7. Odômetros e contadores

[APK] O app separa **Total** (odômetro permanente) de **History** (sessão/registro) e
tem ainda `EUCRidingStatisticsHelper` (31568) e `euc_riding_statistics_helper`:

| Comando | Efeito |
|---|---|
| `genGetTotalStatisticsMsg` (32910) | totais (odômetro, horas) |
| `genGetHistoryStatisticsMsg` (33658) | histórico/estatísticas de sessão |
| `genRequestStatisticsInfoMsg` | info de estatísticas |
| `genRequestHistoryStatisticsMsg` | idem (variante) |

Classes: `V11TotalStatistics`, `V12TotalStatistics`, `V13TotalStatistics`,
`V13HistoryStatistics`, `V14*`, `V18HistoryStatistics`, `E10/E20/E25TotalStatistics`,
`L8TotalStatistics`, `V10LikeTotalStatistics`, `EzCANTotalStatistics`,
`EzCANHistoryStatistics`, `A1/C1/LE1/LR1/RsPro/RsVelo/S1TotalStatistics`.
UI: `dashboard_mileage`, `dashboard_mileage_odo`, `dashboard_mileage_trip`,
`estimatedTotalMileage`; o app também exibe o texto "The manual odometer can be reset
to accumulate mileage from a specific location or to measure distance even among
rides. Double click to reset the Manual ODO." (odômetro manual resetável).

[COM] [`docs/03-protocolo-inmotion.md`](../03-protocolo-inmotion.md) **§6.1** — bytes validados na V11y (framing **default 0x14**):

| Quadro | Pedido | Resposta | Campo | Offset | Escala |
|---|---|---|---|---|---|
| Viagem | `10 00 01` | `0x90` | distância da viagem | 23 | u32 LE /100 km |
| Viagem | `10 00 01` | `0x90` | segundos ligada | 39 | u32 LE s |
| Totais | `11` | `0x91` | distância total | 1 | u32 LE /100 km |
| Totais | `11` | `0x91` | tempo total ligada | 5 | u32 LE s |

[APK] `V11TotalStatistics{mileage:` e `V13TotalStatistics{mileage:` mostram que o
primeiro campo é **mileage** nos beans de total — consistente com o offset 1 do
`0x91`. [INF] `V11TotalStatistics{mileage: <x>, …}` = mapeamento direto de
`0x91[1..4]`; `V11HistoryStatistics{index:` é o registro por sessão.

---

## 8. Configuração (escrita) — inventário completo

> **Como ler:** a coluna *Payload* traz o que está **validado/documentado** no
> `docs/03-protocolo-inmotion.md` (§6) — proveniência **[COM]**. Onde está “—”, o
> comando existe no app oficial **[APK]** mas os bytes **não** foram recuperados
> (lacuna §11). Prefixo de escrita = `0x60` (`60 xx …`), em framing `0x14`
> (standard) ou `0x16`/`02 21` (extra) — ver §4.
>
> ⚠️ **Aviso:** o prefixo `0x60` dos payloads de escrita é **`[INF]`** — inferido do
> padrão dos opcodes de resposta (`0x90/0x91/0x96`) e explicitado em §4.3 (o
> `docs/03-protocolo-inmotion.md` §6 grafa os mesmos comandos com `96`). Nenhuma escrita
> daqui deve ser considerada validada: **confirmar em captura antes de implementar** (§11).
> Restrições por modelo vêm do doc 03 §9.2 e das variantes `*Cmd*` observadas.
> Inventário bruto: **231** geradores `gen*` no app (210 terminam em `Msg`; lista
> completa em `analysis/inmotion/gen_msgs.txt`), dos quais **69** são de escrita
> (`genSet*` / `*OnOff*`).

### 8.1 Velocidade, tiltback e alarmes

| Comando oficial [APK] | Payload [COM] | Observações |
|---|---|---|
| `genSetSpeedLimitMsg` (12387) | `60 21 <u16 LE ×100>` | V11y/V12+: **extra** `02 21 60 21 … 00 00`; default é ignorado |
| `genSetWarningLimitSpeedMsg` | `60 3E <u16> <u16>` | alarmes 1/2 (V12/V9/V12S/V11y) |
| `genSetSpeedLimitOfAssistModeMsg` | — | limite do modo assistência |
| `genSafeSpeedLimitOnOffMsg` / `allowDisableSafeSpeedLimit` | — | limite seguro on/off |
| `genSetAutoLowHighBeamSwitchSpeedThrMsg` | `60 30 <u16 LE>` | limiar low→high (V12) |
| `genSetOutputThresholdMsg` | — | limite de corrente/saída (V14: `50` /100) |
| `genSetSpeedUnitMsg` (44068) | — | km/h × mph |
| `genSetPitchAngleMsg` / `genSetPitchAngleZeroMsg` | `60 22 <u16 LE>` (tilt) | tilt dos pedais / zero |
| `genSetPedalSensitivityMsg` | `60 25 <v> <v>` | sensibilidade comfort/classic |
| `genSetPedalSettingInfoMsg` / `genGetPedalSettingInfoMsg` | — | bloco completo de pedal |
| `genBermAngleModeOnOffMsg` / `genSetBermAngleMsg` | — | ângulo de curva (V14+) |
| `genAccForceMsg` / `genBrakeForceMsg` | — | força de aceleração/frenagem |
| `genSetSpeedingBrakingSensitivityMsg` / `genSpeedingBrakingOnOffMsg` | — | frenagem em excesso |
| `genBrakeFirstModeOnOffMsg` | — | freio primeiro |
| `genOnePedalModeOnOffMsg` / `genZeroSpeedupOnOffMsg` | — | one-pedal / zero speed-up |
| `genCruiseOnOffMsg` | — | cruise |
| `genSpinKillOnOffMsg` | — | corte por giro (spin kill) |
| `genAssistBalanceOnOffMsg` | — | assist balance |
| `genHacOnOffMsg` / `genHdcOnOffMsg` | — | hill assist / hill descent |
| `genSetRidingModeMsg` / `genSetModeMsg` / `genSetDriveModeMsg` | `60 23 <0/1>` | 0=Comfort, 1=Sport (validado V11y) |
| `genAutoScreenOffOnOffMsg` | `60 3D <0/1>` | auto-off da tela (V12) |
| `genSetStandbyTimeMsg` / `genSetParkTimeMsg` | `60 28 <u16 s>` | standby / park |

### 8.2 Luzes, som e efeitos

| Comando oficial [APK] | Payload [COM] | Observações |
|---|---|---|
| `genHeadlightOnOffMsg` | `60 50 <0/1>` (V14) / `<low> <high>` (V12) | 1 byte ignorado em V12 HS/HT/Pro |
| `genSetHeadlightBrightnessMsg` | `60 2B <v>` ou `<low> <high>` | brilho |
| `genAutoHeadlightOnOffMsg` | `60 2F <0/1>` | farol automático (V12/V13/V14) |
| `genSetAutoHeadlightBrightnessThrMsg` | `60 2A <a> <b>` | limiares (V12) |
| `genDaytimeRunningLightMsg` / `genSetDaytimeRunningLightMsg` | `60 2D <0/1>` (V14) / `60 44` | DRL; V12 não tem |
| `genSetTailLightModeMsg` / `genSetTurnSignalModeMsg` | — | lanterna/seta (modelos novos) |
| `genSetLogoLightBrightnessMsg` | — | logo luminoso |
| `genLightEffectOnOffMsg` / `genSetLightEffectModeMsg` / `genSetLightEffectIdMsg` / `genSetLightEffectColorMsg` / `genRequestLightEffectIdListMsg` | — | efeitos de LED/música |
| `genSoundWaveOnOffMsg` / `genSetSoundWaveSensitivityMsg` | — | LEDs por som |
| `genSetVolumeMsg` / `genRequestVolumeMsg` | `60 26 <0–100>` | volume |
| `genPlaySoundItemMsg` | `60 51 18 01` / `60 51 02 64` | buzina: `18 01` (V9/V11/V12/V12S) × `02 64` playBeep (V11Y/V13/V14/E20) — doc 03 §9.1 |
| `genSoundPackOnOffMsg` / `genSetSoundPackIdMsg` / `genRequestSoundPackIdMsg` | — | pacotes de som |

### 8.3 Segurança, lock e PIN

| Comando oficial [APK] | Payload [COM] | Observações |
|---|---|---|
| `genLockMsg` (3606) | `60 31 <0/1>` **em extra** | exige auth em V12+ |
| `genSetAutoLockOnOffMsg` | — | auto-lock |
| `genSetPasswordMsg` / `genSetPasswordTypeMsg` / `genVerifyPwdMsg` | — (V1: CAN `0x0F550307`) | PIN/senha |
| `genRequestPasswordTypeMsg` | — | tipo de senha (X1/V18/P6 expõem `passwordType`) |
| `genEnableLostModeMsg` / `genDisableLostModeMsg` | — | lost mode |
| `genEraseDataMsg` / `genFactoryResetMsg` | — | apagar dados / reset de fábrica |
| `genActivateMsg` / `genGetActivateDateMsg` | — | ativação do veículo |
| `genRequestEncryptedKeyMsg` / `genVerifyCryptKeyMsg` | ver §3.3 | auth |

### 8.4 Bateria, carga e TPMS

| Comando oficial [APK] | Payload [COM] | Observações |
|---|---|---|
| `genSetChargeLimitMsg` | — | limite de carga (%) |
| `genSetMaxChargeCurrentMsg` | — | corrente máxima de carga |
| `genSetTwoBatteryModeMsg` / `genTwoBatteryModeMsg` | — | modo duas baterias |
| `genLowBatteryProtectionModeOnOffMsg` | `60 37 <0/1>` | low-battery riding |
| `genDisableChargeDangerAlertMsg` | — | alerta de carga perigosa |
| `genStartBatteryCalibrationMsg` / `genQueryBatteryCalibrationResultMsg` | — | calibração de bateria |
| `genSearchTirePressureSensorMsg` / `genBindTirePressureSensor` | — | TPMS: buscar/vincular |
| `genSetLowTirePressureAlarmThresholdMsg` / `genSetLowTirePressureWarningThresholdMsg` / `genTirePressureWarningOnOffMsg` | — | TPMS: limiares |
| `genRemainderRangeEstimateOnOffMsg` | — | estimativa de autonomia |

### 8.5 Motor, ventilador e calibração

| Comando oficial [APK] | Payload [COM] | Observações |
|---|---|---|
| `genFanOnOffMsg` | `60 53 <0/1>` (fw≥1.4) / `60 43` | ventilador |
| `genFanQuietModeOnOffMsg` | `60 38 <0/1>` | ventilador silencioso |
| `genNoLoadDetectOnOffMsg` / `genLoadDetectOnOffMsg` | `60 36 <0/1>` | detecção sem carga |
| `genCalibrateMsg` / `genCalibrateIMUMsg` / `genStartIMUCalibrationMsg` / `genQueryIMUCalibrationMsg` / `genQueryIMUCalibrationResultMsg` | `60 52 01 00 01` (yaw) / `60 52 01 01 00` (pedal) | calibração |
| `genCalibrateHandleMsg` / `genQueryHandleCalibrationResultMsg` | — | calibração do handle |
| `genStartMotorAutoCalibrationMsg` / `genStopMotorAutoCalibrationMsg` / `genQueryMotorAutoCalibrationResultMsg` / `genStartMotorSelfCheckMsg` / `genQueryMotorSelfCheckResultMsg` / `genResetMotorAutoCalibrationEffectMsg` | — | auto-calibração/self-check do motor |
| `genStartScooterCalibrationMsg` / `genQueryScooterCalibrationResultMsg` | — | scooters |
| `genSetPitchAngleMsg` / `genSetPitchAngleZeroMsg` | `60 22 <u16 LE>` | tilt/zero |
| `genGpsOnOffMsg` | — | GPS (TBox) |

### 8.6 Sistema, arquivos, OTA e acessórios

| Comando oficial [APK] | Observações |
|---|---|
| `genPowerCtrlMsg` / `genPowerCtrlConfirmMsg` / `genPowerOffMsg` | desligar (2 estágios, doc 03 §6) |
| `genRebootMsg` / `genRebootConfirmMsg` / `genRebootRequestMsg` / `genRebootTBoxMsg` | reboot |
| `genOpenFileMsg` / `genGetFileMsg` / `genWriteFileMsg` / `genCheckFileMsg` / `genCloseFileMsg` / `genDeleteFileMsg` / `genRenameFileMsg` / `genEraseDataMsg` / `genGetFileListMsg` / `genStorageCapacityMsg` / `genSetFileOffsetMsg` | sistema de arquivos (log, som, luz, firmware) |
| `genStartTransferFirmwareMsg` / `genStartUpgradingFirmwareMsg` / `genForceUpgradeMsg` / `genGetForceUpgradeStateMsg` | OTA |
| `genGetLogFileInfoMsg` / `genOpenLogFileInfoMsg` / `genCloseLogFileInfoMsg` / `genGetLogFileContentMsg` | logs |
| `genRequestTBoxInfoMsg` / `genRequestTBoxRealTimeInfoMsg` / `genRequestTBoxDeviceIdMsg` / `genRequestTBoxEsimListMsg` / `genRequestTBoxUserInfoMsg` / `genWakeUpMsg` / `genTboxLowBatLevelWakeUpVehicleModeOnOffMsg` / `genShowTboxInfoOnVehicleOnOffMsg` | TBox (telemetria/eSIM) |
| `genSyncRTCMsg` | relógio |
| `genSetDeviceNameMsg` / `genRequestDeviceNameMsg` | nome BLE |
| `genLocal…` (`genGeneralMsg`, `genGeneralMany2ManyMsg`, `genSpecialMany2ManyRead/WriteMsg`, `genSpecialOne2OneRead/WriteMsg`) | **envelope genérico** (rotas many-to-many / one-to-one) — [INF] é o que carrega `02 24/25/26/27` e comandos por rota |
| `genPatchL8LockBugMsg` (45691) | **workaround de bug** do L8 no lock (modelo-específico) |

### 8.7 Settings: leitura/espelhamento

`genGetSettingsMsg` / `genRequestSettingsMsg` (resposta `A0 20` no doc 03) +
`genSetSettingsMsg` (escrita em bloco). O app mantém `*Settings` por modelo
(`V11Settings`, `V12Settings`, `V13Settings`, `EucSettings{driveMode:`,
`P6Settings{passwordType:`, `X1Settings{passwordType:`, `V18Settings{passwordType:`)
e sincroniza a UI ao conectar — mesmo padrão do doc 03 §10.

---

## 9. Modelos e especificidades

Lista derivada dos módulos `pages/vehicle/service/...` (`vehicle_service_uris.txt`) e
das classes de protocolo:

| Modelo | Família | Variantes de comando | BMS próprio | Notas |
|---|---|---|---|---|
| V5 | `ezcan` | — | não | V1/CAN (FFE5) |
| V6 | `lorin` | `_V6Cmd`, `_V6CmdDefault` | não | modelo de entrada da geração nova |
| V8 | `ezcan` | — | não | V1/CAN |
| V10-like (V10/F/S/T) | `ezcan` | — | não | `V10LikeTotalStatistics` |
| V11 / **V11Y** | `lorin` | `_V11Cmd`, `_V11CmdDefault`, **`_V11CmdV137`**, **`_V11YCmdDefault`**, `_V11YSettingsDefault` | `v11_bms_info` | V11Y é classe própria; [INF] `V137` = revisão de firmware 1.37 com cmd set próprio |
| V12 (HS/HT/Pro) | `lorin` | `_V12Cmd`, `_V12CmdV1`, `_V12CmdV2` | sim (4 packs) | dois feixes de farol |
| V13 / V13 Pro | `lorin` | `_V13Cmd`, `_V13CmdDefault`, `V13CmdDelegate` | `v13_bms_info` | 4 packs |
| V14 | `lorin` | `_V14CmdDefault` | `v14_bms_info` | 4 packs; DRL/fan/auto-headlight |
| **V18** | `lorin` | `_V18Cmd`, `_V18CmdV1`, `_V18CmdV2` | `v18_bms_info` | `passwordType` nas settings |
| **X1** | `lorin` | `_X1Cmd`, `_X1CmdV1`, `_X1CmdV2` | `x1_bms_info` | `X1SingleBatteryRealTimeInfo`, `X1BMSError` |
| **E25** | `lorin` | `_E25Cmd`, `_E25CmdV1`, `_E25CmdV2` | `e25_bms_info` | `E25RealTimeState{pcMode:` |
| E10 | `lorin` | `_E10Cmd`, `_E10CmdDefault` | não | `E10Settings{driveMode:` |
| E20 | `lorin` | `_E20Cmd`, `_E20CmdDefault` | não | `E20Settings{driveMode:` |
| P6 | `lorin` | `_P6Cmd`, `_P6CmdV1`, `_P6CmdV2` | `p6_bms_info` | auth em toda conexão (doc 03) |
| A1 / C1 / LE1 / LR1 / RS Pro / RS Velo / S1 | `lorin` (scooter) | `_A1Cmd`…`_S1Cmd` | le1/lr1 | mesma família de framing do EUC |
| L8 / R1 / E (SCV) | `ezcan` | — | não | `L8Settings{cruiseState:` |
| P1 / P2-like (e-bike) | `ezcan` | — | não | `ebike_settings`/`ebike_statistics` |

> Na coluna *BMS próprio*, os nomes são módulos
> `lorin/bean/euc/<modelo>/<modelo>_bms_info.dart` (ex.: `v14_bms_info`).

**Não encontrado:** `V9` (nenhuma string no pool). O doc 03 lista V9 (carType 12) a
partir do EUC World; o app 8.1.34 não tem módulo nem string `V9`.
[INF] Ou o V9 é tratado como "V10-like", ou o EUC World tem suporte que o app oficial
não expõe. **Confirmar com uma V9 real.**

**Model ids (`carType`):** [COM] doc 03 §3 (6=V11, 7=V12, 8=V13, 9=V14, 10=E20,
11=V12S, 12=V9, 13=P6). O app oficial não usa um switch único: usa
`ProductModel.fromHexCode` + `detection.dart` por modelo. **Lacuna:** a tabela
`hexCode → modelo` está em código; não foi extraída.

### 9.1 O que muda por modelo (resumo prático)

| Capacidade | Modelos (evidência) |
|---|---|
| BMS completo (multi-pack, células, log) | V11, V13, V14, V18, X1, P6, E25, LE1, LR1 (`FullBMSFeatureCmdTrait`) |
| BMS simples | V5/V8/V10-like, E10/E20, scooters (`SimpleBMSFeatureCmdTrait`) |
| Dois feixes (low/high) | V12 (`Low beam and high beam state should be set in the same time in V12.`) |
| DRL | V11Y/V9/V12S (doc 03 §9.2) e V14 (`60 2D`) |
| Farol automático | V12/V13/V14 (`60 2F`, `60 2A`, `60 30`) |
| Alarmes de velocidade | V12/V9/V12S (doc 03 §9.2) |
| Tilt lateral estendido | V13/V14 (`60 45`) |
| Efeitos de luz/som (light effect, sound pack) | V18/X1/E25 e scooters novas (`genSetLightEffect*`, `genSoundPack*`) |
| TPMS | modelos com `genSearchTirePressureSensorMsg` (V14+/X1/V18) [INF] |
| TBox/eSIM | modelos com `genRequestTBox*` (V14+/V18/X1) [INF] |
| Senha/PIN | V1 (`0x0F550307`) e V18/X1/P6 (`passwordType`) |

---

## 10. Firmware e extras

- **OTA do veículo:** `lorin/fw_update_manager`, `common/fw_update_manager`,
  comandos `genStartTransferFirmwareMsg` / `genStartUpgradingFirmwareMsg` /
  `genForceUpgradeMsg` / `genGetForceUpgradeStateMsg`, transferência por arquivos
  (`genOpenFileMsg`/`genWriteFileMsg`/`genCheckFileMsg`/`genCloseFileMsg`,
  `genStorageCapacityMsg`, `genSetFileOffsetMsg`, `genEraseDataMsg`).
- **Checagem de arquivo:** `calCrc32`, `checkCrc32`, `Crc32IsoHdlc`, `crclib`
  (CRC32 de arquivo/OTA — não confundir com o checksum de quadro).
- **OTA do chip de áudio (Jieli):** plugin Java `com.inmotion.flutter.plugin.jieli_ble_ota`
  (`JieliBleOtaManager`, `JieliBleOtaPlugin`) + `libjl_ota_auth.so` — é o firmware do
  **módulo Bluetooth/áudio**, não da roda.
- **Logs:** `genGetLogFileInfoMsg`, `genGetLogFileContentMsg`, `lorin/log_file_manager`,
  `ezcan/ezcan_log_file_manager`, `ElephantLogManager`.
- **TBox:** ver §8.6 (info, eSIM, wake-up, reboot).
- **Ativação:** `genActivateMsg`, `genGetActivateDateMsg`, `ActivationTbox*`.
- **Workarounds:** `genPatchL8LockBugMsg` (L8), `authentication`/`authChallenge`.

---

## 11. Limitações e lacunas (o que exige captura/teste)

1. **Bytes e offsets do app oficial não recuperados.** O código é Dart AOT (ARM32)
   sem tabela de símbolos; `dartaotdump` não existe no SDK 2.19.2 e o `blutter` não foi
   executado. Tudo que é **valor** nesta doc vem do doc 03 **[COM]**. Para fechar:
   - rodar `blutter` com Dart 2.19.2 sobre `libapp.so` (gera `pp.txt` + disassembly); ou
   - capturar BLE do app oficial (HCI snoop) numa sessão por modelo.
2. **Handshake de auth:** confirmar no app oficial os bytes do blob, o `len`, o CK e se
   o echo é idêntico (doc 03 §4). Testar em V12/V13/V14/P6.
3. **Framing por modelo:** confirmar quais comandos saem em `0x14` e quais em `0x16`
   (`genLocalStandard*` × `genLocalExtra*`) para V11Y, V12, V13, V14, V18, X1, P6.
4. **Buzina:** validar `playBeep` (`02 64`) × `playSound` (`18 01`) por modelo **e por
   firmware** (V11 tem `_V11CmdV137`).
5. **Offsets de telemetria** por modelo/firmware (V11Y, V12, V13, V14, V18, X1, P6,
   E25) — doc 03 §5 cobre V12/V14/V11; falta o resto.
6. **Tabela `hexCode`/`carType` → modelo** do app oficial (não extraída).
7. **UUID por modelo** (`_bleUuid1/2/3`): confirmar qual conjunto cada geração usa,
   sobretudo AE00/AE01/AE02 (provável `elephant`).
8. **`elephant`:** protocolo com AES — não há documentação comunitária; capturar.
9. **MTU/chunk/write type/delays** por transporte.
10. **V9:** não existe no app 8.1.34; confirmar se é "V10-like" ou se só o EUC World
    suporta.
11. **Potência/torque/escalas divergentes** (doc 03 §5): validar contra a roda.
12. **Modelos novos (V18/X1/E25)** não têm nenhum offset documentado — prioridade de
    captura se forem alvo.

---

## 12. Evidências e referências

**Evidências locais** (`analysis/inmotion/`, gitignored):

| Arquivo | Conteúdo |
|---|---|
| `evidencias.md` | método, artefatos, digests, achados brutos, limitações |
| `sha256.txt` | digests (APK base, XAPK, split, libapp.so) |
| `java_plugin_stub.txt` | plugins Java (stubs, sem protocolo) |
| `libapp_strings.txt` | `strings -n 6` do `libapp.so` (54.681 linhas; citações `:linha`) |
| `tokens_all.txt` | 39.395 tokens únicos (citações `:linha`) |
| `gen_msgs.txt` | **210** geradores `gen*Msg` (inventário de comandos) |
| `ble_uuids.txt` | UUIDs BLE + linha |
| `vehicle_service_uris.txt` | 94 módulos de modelo (`pages/vehicle/service/...`) |
| `citations.txt` | token → linha nos dois dumps |

**Referências cruzadas:**

- `docs/03-protocolo-inmotion.md` — V1/V2, framing, init, auth, telemetria,
  odômetros, 37 comandos e mapa de settings da V11y (**base [COM]** desta doc).
- `docs/07-plano-multi-protocolo.md` — arquitetura multi-protocolo, tabela de
  detecção BLE, V1/CAN.
- [eried/eucplanet](https://github.com/eried/eucplanet) — MIT; V1+V2, confirma
  `02 21` estendido, auth e playBeep.
- [Wheellog/wheellog.android](https://github.com/Wheellog/wheellog.android) — GPL-3.0;
  usar só fatos (offsets/CAN IDs), **não copiar código**.
- [enaon/eucWatch](https://github.com/enaon/eucWatch) — bytes em decimal (auditoria).
- Snapshot Dart `adb4292f3ec25074ca70abcd2d5c7251` = Dart 2.19.2 (tabela de versões do
  projeto `darter`; mesmo hash citado em análises públicas de apps Flutter 3.7.x).

**Reprodução rápida:**

```bash
sha256sum binario/inmotion.apk
unzip -l binario/inmotion.apk | grep -c '\.so$'      # 0  -> split base sem libs
# XAPK oficial -> config.armeabi_v7a.apk -> lib/armeabi-v7a/libapp.so
strings -n 6 libapp.so | grep -E '6e40000[123]|0000ae0[0-9]'
strings -n 6 libapp.so | grep -E '^gen[A-Z].*Msg$' | sort -u | wc -l   # 210
```
