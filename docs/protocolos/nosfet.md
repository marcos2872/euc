# Protocolo Nosfet — Referência técnica

Extraído por engenharia reversa do **APK oficial da Nosfet** (`com.nos.nosfet` 1.1.3, versionCode 4,
distribuído em nosfet.com fora da Play Store), sha256
`f4881479f3c40e2f1d54a8909223af79f5cb18eaf96d9a94f4116ce10e4a5099`, analisado em 2026-10-18.

> **Proveniência:** **[APK]** = extraído do APK descompilado (arquivo:linha em
> `analysis/nosfet/decompiled/sources/`); **[COM]** = fonte externa verificável; **[INF]** = inferência.
> Evidências e método em `analysis/nosfet/evidencias.md`.
>
> **Achado central:** o app da Nosfet é um **fork do app da LeaperKim** (`com.laoniao.leaperkim`,
> pacote mantido no código). Usa o mesmo BLE (`FFE0/FFE1`), o mesmo framing com CRC32 e os mesmos
> offsets de telemetria do protocolo Veteran/LeaperKim — com uma variante nova de comandos
> (`4C 64`, "Ld") para os settings avançados. **[APK]**
> (`utils/BtManager.java:65-77`; `BuildConfig.java:5`)

---

## 1. Visão geral

| Item | Valor |
|---|---|
| Transporte | BLE GATT, serviço `FFE0`, característica `FFE1` (write + notify) |
| Sentido da telemetria | **a roda envia continuamente**; o app não faz poll |
| Autenticação | nenhuma (sem PIN, senha, handshake ou bonding obrigatório) |
| Framing TX | `4C 6B/64 41 70 <len> <payload> <CRC32 BE>`, `len` = tamanho total |
| Framing RX | `DC 5A 5C … <CRC32 BE>` (CRC não validado pelo app) |
| Unidade de velocidade | km/h (a UI converte para mph) |
| BMS | 2 packs (esquerdo/direito) × 36 células + 6 temperaturas por pack |
| Modelos neste APK | APEX (`5010`, 151,2 V) e AERO (`5020`, 126 V), via config remota |
| Comandos de escrita | 23 frames distintos (18 IDs; `0x11`, `0x12`, `0x14`, `0x16` têm semântica diferente por prefixo) |
| Firmware | `.bin` enviado cru, em blocos de 20 B, após `AT+RINTOPRO` |

O protocolo tem duas "gerações" de header, ambas com o mesmo formato e CRC:

| Header | Nome interno | Uso no app Nosfet |
|---|---|---|
| `4C 6B 41 70` | "LkAp" (legado) | farol, buzina, ride mode, alarme de velocidade, ângulo, viagem, log, desligar |
| `4C 64 41 70` | "LdAp" (novo) | settings avançados (pedal, tiltback, PWM, backlight, gyro, transporte, unidade, etc.) e sync de hora |

**[APK]** No app LeaperKim 1.4.8 (`binario/leaperkim.apk`, analisado em paralelo) os mesmos comandos
existem nas duas variantes e são enviados **em sequência** (`sendBytesDataCombine`) para firmwares
antigos (`004*`) ou ambos; o app Nosfet 1.1.3 simplificou e envia só uma variante por comando.
**[INF]** Firmwares Nosfet aceitam as duas variantes; a escolha do app é só compatibilidade.

---

## 2. Transporte BLE

**[APK]** `utils/BtManager.java:65-68`:

| UUID | Papel |
|---|---|
| `0000ffe0-0000-1000-8000-00805f9b34fb` | serviço |
| `0000ffe1-0000-1000-8000-00805f9b34fb` | característica (write **e** notify) |
| `00002902-0000-1000-8000-00805f9b34fb` | CCCD (habilita notify) |
| `00002901-...` | descriptor declarado, não usado |

**[APK]** `service/BluetoothLeService.java`:
- Conexão: `connectGatt(..., transport = 2 /* LE */)` (`:273`).
- **MTU:** pede `120` logo após conectar (`:71`); só descobre serviços em `onMtuChanged` (`:105-111`).
- **Notify:** ao descobrir serviços, escreve `ENABLE_NOTIFICATION_VALUE` em **todos** os descritores de
  `FFE1` e chama `setCharacteristicNotification(true)` (`:114-126`).
- **Write type:** não chama `setWriteType` → `WRITE_TYPE_DEFAULT` (**com resposta**).
- **Chunk:** `writeData()` envia no máximo **20 bytes** por escrita (`:476-484`) e só envia o próximo
  bloco no `onCharacteristicWrite` OK (`:140-147`) — ou seja, sem `sleep`, o ritmo é o callback do GATT.
- Fila de bytes de 262.144 B (`:353`), o frame inteiro (com CRC) é enfileirado.
- Reconexão: laço de 2 s (10 s se `gatt.connect()` falhar), 3 tentativas (`:402-435`); auto-conexão ao
  último MAC salvo (`:385-391`).

**[APK]** Scan (`home/BlueToothFragment.java`):
- Filtro **apenas por nome**: `getName()` com trim/uppercase precisa começar com **`NF`**
  (`MainActivity.java:47`, `BlueToothFragment.java:385`). Não há filtro por service UUID.
- 5 s de `BluetoothLeScanner.startScan` e, em seguida, `startDiscovery()` clássico como fallback
  (`:296-304`).
- Lista mostra nome/MAC/RSSI; conexão por MAC (`:397-412`).

**[INF]** Nomes esperados: `NF…` (ex.: `NF-APEX`, `NF-AERO`). Não há detecção de modelo pelo nome —
o modelo é identificado pela versão reportada na telemetria (§9).

---

## 3. Conexão e init

**[APK]** Sequência observada (`BluetoothLeService.java`):

| # | Ação | Evidência |
|---|---|---|
| 1 | Conecta (LE) ao MAC | `:273` |
| 2 | `requestMtu(120)` | `:71` |
| 3 | `onMtuChanged` OK → `discoverServices()` | `:105-111` |
| 4 | `onServicesDiscovered` → obtém FFE0/FFE1 | `:114-126` |
| 5 | Habilita notify (CCCD `2902`) | `:121-125` |
| 6 | `onDescriptorWrite` OK → **pronto** (`startListenHeartBeat`) | `:128-137` |
| 7 | Primeiro frame recebido → **sync de hora** (uma vez por conexão) | `BtManager.java:753-759, 887-892` |

Não há comando de init, auth, senha, PIN, challenge ou leitura de serial. O app só escuta.

**[APK]** Watchdog: cada frame recebido reinicia um timer de **1,5 s**; se estourar,
`receivingData = false` (`BtManager.java:887-892`). Esse estado é usado para saber se a roda está
transmitindo e para o modo firmware.

**[APK]** Sync de hora (`utils/Util.java:250-253`), enviado no primeiro frame de cada conexão:

```
4C 64 41 70 12 00 05 <AA-2000> <MM> <DD> <hh> <mm> <ss> <TZ_horas> <CRC32 BE>
```

Exemplo (2026-10-18 12:30:42, UTC-3): `4C 64 41 70 12 00 05 1A 0A 12 0C 1E 2A FD C2 FE 0E 41`
(TZ `0xFD` = -3). **[INF]** O campo TZ é o offset em horas inteiras (`getRawOffset()/3600000`).

---

## 4. Framing

### 4.1 TX (app → roda)

```
4C <6B|64> 41 70 <LEN> <payload...> <CRC32 big-endian>
```

| Offset | Tam | Campo |
|---|---|---|
| 0 | 1 | `0x4C` ('L') |
| 1 | 1 | variante: `0x6B` ('k', legado) ou `0x64` ('d', novo) |
| 2-3 | 2 | `41 70` ('Ap') |
| 4 | 1 | **`LEN` = tamanho total do frame, incluindo o CRC** — é também o ID do comando |
| 5..N-4 | — | payload (layout fixo por comando; `0x80` = "sem alteração") |
| N-3..N | 4 | CRC32 padrão (`java.util.zip.CRC32`) **big-endian** |

**[APK]** `utils/Util.java:212-224` (CRC32 → 4 bytes BE) e `BtManager.java:231-241` (anexa e enfileira).
O byte `LEN` coincide com o tamanho total em **todos** os comandos (ex.: `0x0D` = 13 B, `0x1E` = 30 B).
Isso permite ao firmware remontar frames partidos em writes de 20 B.

**[APK]** `0x80` é o valor sentinela `DEFAULT_EMPTY_VALUE` (`BtManager.java:44`); os frames de settings
são truncados no último campo usado (o restante não é enviado).

Não há escape, byte stuffing nem campo de sequência.

Exemplos completos (CRC calculado):

| Comando | Frame |
|---|---|
| Farol ON | `4C 6B 41 70 0D 01 80 80 01 57 ED 3B D5` |
| Farol OFF | `4C 6B 41 70 0D 01 80 80 00 20 EA 0B 43` |
| Buzina | `4C 6B 41 70 0E 00 80 80 80 01 CA 87 E6 6F` |
| Ride mode Soft | `4C 6B 41 70 0C 01 80 01 A8 E7 54 80` |
| Zerar viagem | `4C 6B 41 70 0B 00 01 09 0A 31 F8` |
| Desligar em 10 s | `4C 6B 41 70 16 01 80 80 80 80 80 80 80 80 80 80 01 80 D9 6E 11 22` |
| Tiltback 50 km/h | `4C 64 41 70 11 01 02 80 80 80 80 80 80 80 32 AC F2 EF 37` |
| PWM 50% | `4C 64 41 70 12 01 02 80 80 80 80 80 80 80 32 35 10 89 36` |
| Transport mode ON | `4C 64 41 70 16 01 02 80 80 80 80 80 80 80 80 80 80 80 01 A6 02 B5 6D` |
| Unidade milhas | `4C 64 41 70 17 01 02 80 80 80 80 80 80 80 80 80 80 80 01 1F F9 6E 85` |

### 4.2 RX (roda → app)

**[APK]** `BtManager.java:79-81, 250-269`:

```
DC 5A 5C <dados...> <CRC32 BE>
```

- Header fixo `DC 5A 5C` (3 bytes).
- O app **não valida o CRC** e **não usa nenhum campo de tamanho** no RX.
- `parseData()` processa o buffer anterior quando chega um novo header — ou seja, assume
  **1 frame completo por notificação BLE**.
- O CRC32 de 4 bytes no fim é inferido pelos checks `size()-4` usados nos sub-frames. **[INF]**

**[INF]** O byte `[3]` do RX provavelmente é o tamanho/tipo do frame (a mesma filosofia do TX), mas o
app não o lê; ver §11.

---

## 5. Telemetria (frame principal)

**[APK]** `BtManager.java:271-345`. Todos os inteiros são **big-endian**; `s16` = complemento de dois.

| Offset | Tipo | Escala | Unidade | Campo |
|---|---|---|---|---|
| 4-5 | u16 | /100 | V | Tensão do pack |
| 6-7 | s16 | /10 | km/h | Velocidade (app usa `abs`) |
| 8-9 + 10-11 | u16 ×2 | — | m | Odômetro de viagem: `(u16@10 << 16) + u16@8` |
| 12-13 + 14-15 | u16 ×2 | — | m | Odômetro total: `(u16@14 << 16) + u16@12` |
| 16-17 | s16 | `abs`/10 | A | Corrente do motor (fase) |
| 18-19 | s16 | /100 | °C | Temperatura do controlador |
| 20-21 | u16 | — | s | `shutDownTime` ("Sleep", exibido em segundos) |
| 23 | u8 | — | — | `chargeMode` (>0 = carregando) |
| 24-25 | u16 | /10 | km/h | Alarme de velocidade (`dangerSpeed`) |
| 26-27 | u16 | /10 | km/h | Tiltback (`stopSpeed`) |
| 28-30 | u8 ×3 | — | — | Versão: ver §9 |
| 31 | u8 | — | — | Ride mode (1..3 discreto; 100..200 contínuo) |
| 32-33 | s16 | /100 | ° | Atitude (`carPose`, 0,01°) |
| 34-35 | u16 | /10000 | — | PWM/duty (`outPut`; 10000 = 100%) **[INF]** |
| 36-37 | u16 | — | — | Estado térmico da bateria: 111=normal; 100/101/110=quente |
| 46 | u8 | — | — | Tipo do sub-frame (0..8, 32, 33) — §6 |

Campos derivados (`:333-340`):

```
corrente_bateria = corrente_motor × outPut / 10000        [A]
potência         = corrente_bateria × tensão              [W]
bateria_%        = tabela volToSoc do modelo (ou curva 84 V de fallback)
```

**[APK]** `carPose` é usado no upgrade: exige `|carPose| >= 4000` (40,00°) e a tela mostra
`carPose/100` em graus (`UpgradeActivity.java:496-497`).

**[INF]** `outPut` é o duty/PWM em passos de 0,01% (0..10000), coerente com o nome "PWM value setting"
do alarme de PWM (§8). A energia é calculada como corrente de fase × duty × tensão.

Pegadinhas do frame principal:
- `speed` e correntes usam `abs()` → **sem sinal** (marcha à ré/regeneração não é distinguível).
- O teste de sinal é `> 32768` (não `>=`), então `0x8000` escapa como positivo.
- Odômetros usam as palavras de 16 bits **invertidas** (alta em 10-11, baixa em 8-9).
- O frame principal tem pelo menos 51 B (dados até 46 + CRC). **[INF]**

---

## 6. BMS

**[APK]** `BtManager.java:346-718` e `bean/BatteryGroupBean.java`. O byte 46 seleciona o sub-frame; os
campos de dados começam no 47 e variam por sub-frame. O app mantém **dois packs**: esquerdo (`cmd` 1/2/3)
e direito (5/6/7).

| cmd | Conteúdo | Offsets | Escala |
|---|---|---|---|
| 0 ou 4 | Status/ângulo/correntes dos packs | ângulo `s16@67`, corrente L `s16@69`, corrente R `s16@71` | /100 (0,01°) e /100 A |
| 1 ou 5 | Células 1..15 (L/R) | 15 × u16 em `53..82` | mV (`/1000` → V) |
| 2 ou 6 | Células 16..30 (L/R) | 15 × u16 em `53..82` | mV |
| 2 | Ângulo de corte lateral | `u8@47` | graus |
| 3 ou 7 | Temperaturas + células 31..36 | 6 × s16 em `47..58`; 6 × u16 em `59..70` | /100 °C; mV |
| 8 | Settings (leitura) | `50..65` | ver §8 |
| 32 | Entradas de log (3 por frame) | `47..80` | ver abaixo |
| 33 | Detalhe de log | `47..` | ver abaixo |

Estrutura por pack: **36 células** (15 + 15 + 6), **6 temperaturas**, **1 corrente**. O total do pack é
a soma das 36 tensões; a UI mostra mín/máx/delta/média e potência (`BatteryGroupBean.culAfterFilled`).

**[APK]** O pack só é publicado quando chegam os **4 sub-frames** (3 blocos de tensão + corrente) —
`isFilled()` em `BatteryGroupBean.java`.

**[APK]** Correntes de pack usam `abs()` (`BtManager.java:365, 375`); o sinal de carga/descarga é perdido.

**[APK]** `batteryTempMode` (offset 36-37): a UI trata `111` como "normal" e `100/101/110` como "quente";
outros valores mostram "-" (`SecondPageFragment.java:126-138`). **[INF]** Parece um campo de estado com
3 bits (por pack/sensor), não uma temperatura.

Log de erros (`cmd 32/33`, `BtManager.java:580-690`):
- `cmd 32`: 3 entradas de 11 bytes a partir de 47: índice (1 B), conteúdo (2 B), data/hora (4 B),
  5 bytes extras. Só é parseado se `receivingLog` (após enviar `0x14`).
- `cmd 33`: total de logs (`@47-49`), índice, data/hora (4 B), conteúdo (2 B), quantidade de extras (1 B),
  lista de `u32` e string terminada em NUL em **GBK**.

---

## 7. Odômetros e contadores

| Valor | Origem | Observação |
|---|---|---|
| Viagem | u32 "invertido" (8-11) em metros | zera no comando `0x0B`; a roda zera ao desligar |
| Total | u32 "invertido" (12-15) em metros | persistente |
| Horas ligada | **calculado pelo app** | incrementa 1 s/s enquanto conectado; salvo por MAC; **não vem da roda** |
| Tempo até desligar | u16 @20-21 (s) | exibido como "shutDownTime" |
| Velocidade máxima | **calculado pelo app** | máximo desde a conexão; salvo por MAC |
| Corrente máxima | **calculado pelo app** | idem |

**[APK]** `SecondPageFragment.java:113-130` divide os odômetros por 1000 para exibir km.
`BtManager.tik()` (`:155-158`) é quem incrementa os contadores de tempo — não há campo de horas totais
no protocolo (diferente da Inmotion).

---

## 8. Configuração (escrita)

**[APK]** Todos os comandos abaixo são `sendBytesData()` → CRC32 BE anexado. O app **não espera ACK**:
a confirmação é o próximo sub-frame `8` (leitura dos settings). Valores `0x80` = "não alterar".

### 8.1 Ações

| Comando | Prefixo | Frame (sem CRC) | Parâmetro |
|---|---|---|---|
| Buzina | 6B | `4C 6B 41 70 0E 00 80 80 80 01` | — |
| Farol ON / OFF | 6B | `4C 6B 41 70 0D 01 80 80 01` / `… 00` | 0/1 |
| Ride mode Soft/Medium/Strong | 6B | `4C 6B 41 70 0C 01 80 <1|2|3>` | 1..3 |
| Ride mode contínuo | 6B | `4C 6B 41 70 0C 01 80 <10..110>` | progresso 0..100 + 10 |
| Alarme de velocidade | 6B | `4C 6B 41 70 11 01 80×7 <km/h>` | 10..120 |
| Ângulo lateral ("angel") | 6B | `4C 6B 41 70 10 01 80×6 <0,1°>` | -80..+80 (0,1°) |
| Ângulo de corte lateral | 6B | `4C 6B 41 70 16 01 80×11 <graus>` | 35..75 |
| Desligar em 10 s | 6B | `4C 6B 41 70 16 01 80×10 01 80` | — |
| Zerar viagem | 6B | `4C 6B 41 70 0B 00 01` | — |
| Ler log de erros | 6B | `4C 6B 41 70 14 01 80×9 01` | — |
| Sync de hora | 64 | `4C 64 41 70 12 00 05 AA MM DD hh mm ss TZ` | ver §3 |

### 8.2 Settings (prefixo `64`)

| # | Comando | Frame (sem CRC) | Valor | Faixa / unidade | Leitura (sub-frame 8) |
|---|---|---|---|---|---|
| 1 | Pedal hardness | `4C 64 41 70 0F 01 02 80 80 80 <v>` | 0..100 | — | @50 |
| 2 | Tiltback speed | `4C 64 41 70 11 01 02 80×7 <v>` | 10..120 | km/h | @52 |
| 3 | Alarme de PWM | `4C 64 41 70 12 01 02 80×7 <v>` | 30..100 | % | @53 |
| 4 | Backlight | `4C 64 41 70 14 01 02 80×9 <v>` | 0..100 | % | @55 |
| 5 | Calibração do giroscópio | `4C 64 41 70 15 01 02 80×9 01` | — | start/stop | @56 |
| 6 | Transport mode | `4C 64 41 70 16 01 02 80×11 <v>` | 0/1 | — | @57 |
| 7 | Unidade | `4C 64 41 70 17 01 02 80×12 <v>` | 0=km / 1=mi | — | @58 |
| 8 | Correção de tensão | `4C 64 41 70 18 01 02 80×12 <v>` | -15..+15 | 0,1% | @59 |
| 9 | Low battery mode | `4C 64 41 70 19 01 02 80×13 <v>` | 0/1 | — | @60 |
| 10 | High speed mode | `4C 64 41 70 1A 01 02 80×14 <v>` | 0/1 | — | @61 |
| 11 | Key tone (volume) | `4C 64 41 70 1C 01 02 80×16 <v>` | 0..100 | % | @63 |
| 12 | Tensão máx. de carga | `4C 64 41 70 1D 01 02 80×17 <v>` | 0..70 | 145,0 + v/10 V | @64 |
| 13 | Alarme de sobrepressão do freio | `4C 64 41 70 1E 01 02 80×18 <v>` | 90..125 | % | @65 |

**[APK]** Line refs: `SetAlarmSpeedActivity.java:66`, `SetAngelActivity.java:69`,
`SetFallProtectionAngleActivity.java:64`, `SetRideModeActivity.java:119`, `ControlActivity.java:399-411`,
`GyroscopeSettingActivity.java:122`, `UnitSwitchActivity.java:76`, `PedalSoftnessSettingActivity.java:37`,
`StopSpeedSettingActivity.java:42`, `StopPowerSettingActivity.java:30`,
`ScreenBacklightSettingActivity.java:30`, `KeyToneSettingActivity.java:30`,
`MaxChargePowerSettingActivity.java:31`, `BrakeSettingActivity.java:30`, `VolLightSettingActivity.java:31`.

Observações de implementação:

- **`0x11` é ambíguo pelo prefixo**: `6B` = alarme de velocidade (buzzer); `64` = tiltback. O mesmo vale
  para `0x12` (sync de hora × PWM), `0x14` (log × backlight) e `0x16` (desligar/ângulo × transporte).
- **Ride mode:** no modo discreto a roda reporta 1/2/3. No modo contínuo (`continuousSoftHardSet=true`
  na config), o app envia `10..110` e a roda reporta `100..200`; a UI mostra `rideMode-100`
  (`SetRideModeActivity.java:86-97, 119`). **[INF]** O firmware soma 90 ao valor enviado.
- **Settings não suportados:** a roda sinaliza com `0x80` na leitura; o app esconde a linha
  (`ControlActivity.java:365-393`). Implementar o mesmo (capabilities por modelo).
- **Sem ACK:** a resposta é o sub-frame 8; não há eco do comando.
- **Lock:** o botão existe na home, mas mostra "developing" — **não implementado**
  (`HomepageFragment.java:236-238`). O app LeaperKim tem as constantes `CMD_LOCK_SUCCESS`
  (`4C 64 41 70 0A 10`) e `CMD_UNLOCK_SUCCESS` (`4C 64 41 70 0A 24`), mas nenhuma referência de uso.
- **Sem** comando de farol em modos, DRL, handle, seat, LED, buzina com duração ou desligamento normal —
  só o desligar em 10 s.
- **Correção de tensão (`0x18`)**: leitura @59 é 0..30; o app envia `progresso-15` (-15..+15) e a tela
  mostra `raw/10 %` (0..3%) em vez de ±1,5% — inconsistência do app (`ControlActivity.java:390`).
  **[INF]** Implementar como `raw-15` para o valor com sinal.
- **Tensão máx. de carga (`0x1D`)**: a UI assume 145,0 V como base (APEX); para AERO (126 V) o valor
  exibido não corresponde à tensão real do pack (`ControlActivity.java:388`).

---

## 9. Modelos e especificidades

**[APK]** O modelo **não é detectado por nome BLE nem por comando de identificação**. Ele é derivado do
campo de versão da telemetria (bytes 28-30, §5) e comparado com a configuração remota
`http://www.baat22.com:800/nfapp/appconfig.txt` (`CarDataManager.java:23`).

**[APK]** Formato da versão (`BtManager.java:726-751`): `bytesToInt({b30,b28,b29})` → inteiro de 6 dígitos
`"HHHHSS"`; os 3 primeiros + o 4º formam o **hardwareVersion** (4 dígitos) e os 2 últimos o
**softwareVersion**. A UI mostra `"HHH.S.SS"`.

Config remota (snapshot v11, 2026-10-18):

| Modelo | hardwareVersion | Tensão do sistema | `continuousSoftHardSet` | `volToSoc` |
|---|---|---|---|---|
| APEX | `5010` | 151,2 V | true | 100 limiares (113,40 → 148,50 V) |
| AERO | `5020` | 126 V | true | 100 limiares (94,50 → 123,75 V) |

Campos da config (`CarDataManager.parseResponse`): `name`, `sNum` (ordem na UI), `imgUrl`, `gifUrl`,
`hardwareVersion`, `SysVol` (só exibição), `continuousSoftHardSet` e `volToSoc` (curva tensão→%).

O que muda por modelo:
- **Nome/imagem** na tela de informações (`CarInfoActivity`).
- **Curva de SOC**: `volToSoc` do modelo; se ausente, o app usa uma curva fixa de 84 V
  (`Util.java:28-57`) — inadequada para 126/151 V.
- **UI de ride mode**: `continuousSoftHardSet=true` → slider 0..100; `false` → Soft/Medium/Strong.
- **Settings disponíveis**: definidos pelo `0x80` na leitura, não pelo modelo.
- **Firmware**: caminho e binário variam pelo `hardwareVersion` (§10).

**AEON e XENO:** o site oficial lista os 4 modelos, mas a config v11 servida em 2026-10-18 só tem
**APEX e AERO**. **[INF]** O APK 1.1.3 é anterior a AEON/XENO; o protocolo deve ser o mesmo (mesma
família/fork), mas os `hardwareVersion`, curvas de SOC e nomes só estarão disponíveis em config/app
mais novos. Ver §11.

**[COM]** O EUC World 2.54.0 declara suporte à NOSFET AERO e descreve a APEX como usando "tecnologias
comprovadas em rodas LeaperKim" — coerente com o fork e com o protocolo compartilhado.
https://euc.world/blog/euc-world-2-54-0-has-been-released

---

## 10. Firmware (essencial)

**[APK]** `setting/UpgradeActivity.java`:

| Item | Valor |
|---|---|
| Base | `http://www.baat22.com:800/nfapp/firm/1/` |
| Índice de versão | `<hardwareVersion>xx.bin` (`:302`) |
| Notas da versão | `<versão com "." → "_">.txt` (`:328`) |
| Imagem | `_main<hardwareVersion><suffix>.bin` (`:400`), `suffix` = `aa` ou `xx` |
| Pré-requisito | roda inclinada 40°..90° (`|carPose| >= 4000`) (`:212, 496-500`) |
| Entrada no bootloader | envia a string ASCII/GBK `AT+RINTOPRO` (`:233`) |
| Espera | aguarda `receivingData == false` (sem telemetria) antes de enviar o binário (`:243-250`) |
| Envio | binário **cru**, sem CRC, em blocos de 20 B (`BtManager.sendBinData` → `sendToBuf(..., true)`) |
| Progresso | a cada 20 pacotes: `(pacotes×20×100)/tamanho` (`BluetoothLeService.java:489-497`) |
| Confirmação | após reboot, compara a versão reportada com a alvo (`:122-140`) |

Há um atualizador manual escondido: 5 toques na imagem da tela de informações abre
`EditUpgradeActivity`, com base `http://home.baat22.com:800/McuBin/Stm32F103c8/LKApp/1/2/` — indica
MCU **STM32F103** no controlador.

**[INF]** O `.bin` não tem CRC/assinatura visível no app; a integridade é responsabilidade do
bootloader da roda. Não implementar atualização sem captura do processo real.

---

## 11. Limitações e lacunas

1. **Sem captura BLE real.** Todos os offsets vêm do parser do app; não há frame de roda para validar
   escalas, ordem de sub-frames, taxa de envio nem o byte `[3]` do RX.
2. **Byte `[3]` do RX** (após `DC 5A 5C`) não é lido pelo app. **[INF]** Provavelmente tamanho/tipo do
   frame, mas precisa de captura.
3. **Sub-frame 0/4**: o layout completo (além de ângulo em 67-68 e correntes em 69-72) não é parseado;
   o tamanho mínimo do frame não é claro.
4. **Valores de `chargeMode`** (offset 23): o app só testa `>0`. Valores exatos (carregando/standby)
   desconhecidos.
5. **`batteryTempMode`** (36-37): só 100/101/110/111 são tratados; semântica exata desconhecida.
6. **Log de erros (32/33)**: parser parcial (alguns offsets do `cmd 33` parecem ter bugs — ex.: conteúdo
   da 3ª entrada lê `get(51)` em vez de `get(72)`).
7. **AEON/XENO**: ausentes da config v11 deste APK; faltam `hardwareVersion` e curvas de SOC.
8. **Firmware**: formato interno do `.bin`, assinatura/CRC e sequência exata do bootloader não visíveis.
9. **Lock**: não implementado no app; as constantes de sucesso existem no fork LeaperKim, mas não há
   sequência de comando conhecida.
10. **Modo texto (`strCmdMode`)**: para firmwares antigos (frames < 47 B), o app envia strings
    (`AT+RINTOPRO`, `CLEARMETER`, `SETs`…) em vez de binário (`BtManager.java:278-291`). Não é o caso
    das rodas Nosfet atuais, mas o adapter deve detectar e recusar/tratar.
11. **`outPut`/PWM** e **`carPose`** são inferidos do uso; confirmar com a roda (PWM em % e ângulo em
    graus).
12. **Divergência com o LeaperKim** (`protocolos/leaperkim.md` §5.4): aqui o alarme de sobrepressão do
    freio está no offset **65** (`len` **30**); na doc do LeaperKim ele fica no offset **69** (`len`
    **34**), que lá também documenta os campos `@65` (base da tensão de carga), `@66` (assistência
    acel./desacel.) e `@68` (redução do acelerômetro) ausentes nesta doc → ⚠️ pendente de
    reconciliação via captura (layout pode variar por firmware/modelo, ou um dos lados está
    deslocado).

**Capturas recomendadas:** uma sessão parada (telemetria + sub-frames 0..8), uma em movimento
(velocidade/corrente/PWM), carga (chargeMode/correntes), e uma escrita de cada setting com leitura de
volta. Salvar `btsnoop_hci.log` e anotar modelo/firmware.

---

## 12. Evidências e referências

**Artefato:** `binario/nosfet.apk` — sha256 `f4881479f3c40e2f1d54a8909223af79f5cb18eaf96d9a94f4116ce10e4a5099`.

**Evidências locais** (`analysis/nosfet/`):

| Arquivo | Conteúdo |
|---|---|
| `evidencias.md` | método, achados brutos, referências arquivo:linha |
| `decompiled/sources/` | 3.725 `.java` descompilados (JADX) |
| `comandos_templates.txt` | extração automática dos arrays de comando (template + tamanho) |
| `comandos_hex.txt` | frames com CRC calculado (exemplos) |
| `appconfig.txt` | snapshot da config remota de modelos (v11) |
| `jadx.log` | log da descompilação |

**Principais referências no código:**

| Fato | Arquivo:linha |
|---|---|
| UUIDs BLE | `utils/BtManager.java:65-68` |
| Comandos fixos | `utils/BtManager.java:69-77` |
| CRC32 BE | `utils/Util.java:212-224` |
| Sync de hora | `utils/Util.java:250-253` |
| Parse da telemetria | `utils/BtManager.java:271-345` |
| Sub-frames/BMS/settings | `utils/BtManager.java:346-690` |
| Versão/modelo | `utils/BtManager.java:726-751` |
| MTU/notify/chunks | `service/BluetoothLeService.java:71, 105-147, 464-498` |
| Filtro de scan "NF" | `home/MainActivity.java:47`, `home/BlueToothFragment.java:385` |
| Config remota | `utils/CarDataManager.java:23` |
| Firmware | `setting/UpgradeActivity.java:42, 212, 233, 256, 302, 400` |

**Referências externas ([COM]):**

- EUC World 2.54.0 — suporte a NOSFET AERO e relação com LeaperKim:
  https://euc.world/blog/euc-world-2-54-0-has-been-released
- Site oficial (APEX, AERO, AEON, XENO): https://www.nosfet.com
- EUC World 2.66.0 (suporte ao Xeno em 2026): citado no plano multi-protocolo
  (`docs/07-plano-multi-protocolo.md` §3.7), não verificado em nota oficial nesta análise.
