# Protocolo LeaperKim / Veteran — engenharia reversa do app oficial

> **Proveniência:** APK oficial `com.laoniao.leaperkim` **1.4.8** (versionCode 59, minSdk 26,
> targetSdk 35), arquivo `binario/leaperkim.apk`, SHA-256
> `40f5fb14fefbb00e24dcaa986657ace78d1a91875c2fc0f93c943795ee7ee3cf`.
> Análise estática em 2026-10-18 (JADX 0.7.1 + apktool). Nada foi executado do APK; nenhuma
> captura BLE da roda real foi usada.
>
> **Convenções de evidência:** `[APK arquivo:linha]` = fato lido no código descompilado
> (arquivos em `analysis/leaperkim/exports/`, cópia integral em `analysis/leaperkim/src/`);
> `[COM]` = corroborado por implementação comunitária (EUC World/WheelLog, somente fatos, sem
> código); `[INF]` = inferência nossa, não confirmada.
>
> **Escopo:** transporte BLE, init, framing, telemetria, BMS, odômetros, configurações,
> modelos, firmware e event log. Lacunas na §11.

---

## 1. Visão geral

O app é o cliente **oficial da LeaperKim** (marca **Veteran**) e fala apenas com rodas cujo nome
BLE começa com `LK` `[APK MainActivity.java:49]` `[APK BlueToothFragment.java:379-396]`.
Não há autenticação, handshake nem polling: a roda **empurra** telemetria continuamente e o app
só escreve quando o usuário muda uma configuração ou sincroniza a hora `[APK BtManager.java:1060-1068]`.

Características gerais:

| Item | Valor |
|---|---|
| Transporte | BLE GATT, serviço `FFE0`, característica `FFE1` (write + notify) `[APK BtManager.java:69-72]` |
| MTU | pede **120** `[APK BluetoothLeService.java:71]` |
| Escrita | `WRITE_TYPE_DEFAULT` (com resposta), **chunks de 20 B** `[APK BluetoothLeService.java:476-484]` |
| Sentido RX | frames iniciados por `DC 5A 5C`, **sem** validação de CRC pelo app `[APK BtManager.java:304]` |
| Sentido TX | `4C <variante> 41 70 <len> <op> <alvo> <payload…> <CRC32 BE>` `[APK BtManager.java:73-92]` `[APK Util.java:197-209]` |
| Variantes do header | `0x6B` ("k") e `0x64` ("d") — o app chama de OLD/NEW `[APK BtManager.java:73-90]` |
| Init | só habilitar notificações + sincronizar hora; **sem auth** `[APK BluetoothLeService.java:114-137]` |
| Taxa | telemetria ~1 Hz; watchdog de 1500 ms `[APK BtManager.java:1060-1068]` `[INF]` |
| Modelos no app | 8 rodas (Sherman → Patton-S) + 2 pseudo-modelos de firmware `[APK Util.java:27]` |

> **Correção ao plano multi-protocolo (§3.5):** o app oficial usa **somente** `FFE0/FFE1`.
> Não há uso de `FFE5/FFE9` em nenhum ponto do código (`grep` por `ffe5/fff0/fff1` = 0 resultados).
> O header não é `4C <cmd> 41 70` e sim `4C <variante> 41 70 <len> <op> <alvo> …`, com **dois bytes
> de controle** antes do payload.

---

## 2. Transporte BLE

### 2.1 UUIDs

| Papel | UUID | Observação |
|---|---|---|
| Serviço | `0000ffe0-0000-1000-8000-00805f9b34fb` | `[APK BtManager.java:69]` |
| Característica (write + notify) | `0000ffe1-0000-1000-8000-00805f9b34fb` | `[APK BtManager.java:70]` |
| CCCD (notify) | `00002902-…` | `[APK BtManager.java:71]` |
| "User description" | `00002901-…` | `[APK BtManager.java:72]` |

O app pega a característica por UUID exato (`getService(FFE0).getCharacteristic(FFE1)`) e
**escreve `ENABLE_NOTIFICATION_VALUE` em todos os descritores** da característica, inclusive no
`0x2901`, antes de `setCharacteristicNotification(true)` `[APK BluetoothLeService.java:114-126]`.
Isso funciona na prática, mas é frágil (o `0x2901` é read-only em muitos stacks). Nossa
implementação deve escrever **somente** o CCCD `0x2902`.

### 2.2 Scan e seleção

| Item | Valor |
|---|---|
| API | `BluetoothLeScanner.startScan(callback)` **sem** `ScanFilter` (todos os dispositivos) `[APK BtManager.java:970-975]` |
| Fallback | após 5 s, `BluetoothAdapter.startDiscovery()` (BR/EDR) `[APK BlueToothFragment.java:44,64-72]` |
| Filtro | nome (trim + maiúsculas) **começa com `LK`** `[APK BlueToothFragment.java:379-396]` |
| Dedup | por MAC, guardando o RSSI `[APK BlueToothFragment.java:383-395]` |
| Permissões | `BLUETOOTH_SCAN` (com `neverForLocation`), `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`; em API < 31, localização `[APK AndroidManifest.xml]` |
| Rádio | `connectGatt(..., TRANSPORT_LE)` `[APK BluetoothLeService.java:273]` |

O app guarda o último MAC conectado e tenta reconectar sozinho ao subir o serviço
`[APK BluetoothLeService.java:385-391]`.

### 2.3 Escrita

- Fila própria (`DataQueue`, 256 KiB) → `writeData()` pega **no máximo 20 bytes**, faz
  `setValue()` + `writeCharacteristic()` e só então espera `onCharacteristicWrite` para o próximo
  chunk `[APK BluetoothLeService.java:464-498]`.
- Não há `setWriteType(WRITE_TYPE_NO_RESPONSE)`: o tipo usado é o default da característica
  (`WRITE_TYPE_DEFAULT`, com resposta) `[APK BluetoothLeService.java:483-484]`.
- Não há `sleep` entre chunks: o ritmo é dado pelo callback de confirmação `[APK BluetoothLeService.java:140-147]`.
- Um comando "combinado" (variantes `6B` + `64`) é enfileirado como um único array e por isso
  atravessa o chunking como stream contínuo `[APK BtManager.java:263-267]`
  `[APK BluetoothLeService.java:450-462]`.

### 2.4 Reconexão

| Item | Valor |
|---|---|
| Período do laço | `2000 ms` `[APK BluetoothLeService.java:165]` |
| Tolerância ao "conectando" | 3 ciclos (~6 s) antes de resetar `[APK BluetoothLeService.java:167,404-416]` |
| Falha de `connect()` | fecha o GATT e espera `5 × 2000 ms` `[APK BluetoothLeService.java:421-425]` |
| Queda com app em primeiro plano | reabre a `MainActivity` `[APK BluetoothLeService.java:76-84]` |

---

## 3. Conexão e init

Sequência completa (não há troca de chaves, senha nem "hello"):

```
app                                   roda
 |-- connectGatt(TRANSPORT_LE) ------->|
 |<- onConnectionStateChange(2) -------|
 |-- requestMtu(120) ----------------->|
 |<- onMtuChanged(120) ----------------|
 |-- discoverServices() -------------->|
 |<- onServicesDiscovered(FFE0/FFE1) --|
 |-- writeDescriptor(0x2902 = 01 00) ->|   (o app escreve em TODOS os descritores)
 |-- setCharacteristicNotification --->|
 |<- onDescriptorWrite(0) -------------|
 |       (conexão considerada OK)      |
 |                                     |-- frame DC 5A 5C … (telemetria, ~1 Hz)
 |-- time sync (2x, 2 s de intervalo) >|
 |-- comandos sob demanda ------------>|
```

Detalhes:

1. `onMtuChanged` só chama `discoverServices()` quando o status é 0; se o pedido de MTU falhar,
   o app **não** descobre serviços e a conexão fica inútil `[APK BluetoothLeService.java:105-111]`.
2. A conexão é dada como pronta em `onDescriptorWrite` (não em `onServicesDiscovered`)
   `[APK BluetoothLeService.java:129-137]`.
3. Não existe comando de "start streaming": ao habilitar notificações a roda já envia frames
   `[INF, derivado da ausência de qualquer envio antes do primeiro frame]`.
4. **Watchdog de 1500 ms**: cada frame recebido marca `receivingData = true` e agenda
   `MSG_HEARTBEAT` para 1500 ms depois, que volta a `false` `[APK BtManager.java:1060-1068]`.
   **Todo envio é descartado silenciosamente quando `receivingData == false`**
   `[APK BtManager.java:215-216,251-252,271-281]`. Numa implementação própria isso deve virar
   erro explícito ("roda não está enviando dados").
5. **Sincronização de hora**: no primeiro frame válido, envia o frame de hora e repete 2 s depois;
   só uma vez por conexão `[APK BtManager.java:913-930]`.
6. **Sem auth.** O "lock" (§8) é um comando com senha, não um handshake de sessão
   `[APK Util.java:258-275]`.

---

## 4. Framing

### 4.1 TX — comandos para a roda

```
 0    1    2    3    4     5     6     7 .. n-5      n-4 .. n-1
4C  var  41   70  len    op   alvo   payload…       CRC32 (big-endian)
```

| Campo | Tam. | Descrição |
|---|---|---|
| `4C 3? 41 70` | 4 | Literal `"L" + var + "Ap"` |
| `var` | 1 | `0x6B` ("LkAp") ou `0x64` ("LdAp") — duas variantes do mesmo comando `[APK BtManager.java:73-90]` |
| `len` | 1 | **Comprimento total do frame, incluindo o CRC** `[APK BtManager.java:73-90]` |
| `op` | 1 | `0x01` = escrita/gravação; `0x00` = ação pontual (buzzer, zerar viagem, hora, senha) `[APK BtManager.java:73-92]` `[INF, agrupamento nosso]` |
| — | — | **Não existe byte de número de comando**: a operação é identificada por `(len, alvo)` + posição do valor (§8.1) `[INF]` |
| `alvo` | 1 | `0x00` = domínio "frame principal"; `0x02` = bloco de settings (e "zerar viagem", com `op=00`); `0x05` = hora/senha. Na variante `6B` este byte é filler `0x80` `[APK setting/*.java]` `[INF, agrupamento nosso]` |
| `payload` | n−9 | Campos; **`0x80` = "não alterar"** (o filler é sempre `0x80`; só o byte `alvo` vira `0x00` na variante `64`) `[APK setting/control/*.java]` |
| `CRC32` | 4 | CRC-32 IEEE (polinômio refletido `0xEDB88320`, init/final `0xFFFFFFFF`), **big-endian** `[APK Util.java:197-209]` |

Regra do CRC (do código): `crc = CRC32(bytes[0 .. len-5])`, escrito nos 4 bytes finais em ordem
`MSB..LSB` `[APK Util.java:205-209]`.

**Exemplo mínimo (completo, com CRC real):**

```
farol ON, variante 6B : 4C 6B 41 70 0D 01 80 80 01 57 ED 3B D5
farol ON, variante 64 : 4C 64 41 70 0D 01 00 80 01 6F F8 32 F9
```

Mais exemplos em `analysis/leaperkim/exemplos-frames.txt`.

#### Regra do "bloco de settings" (`alvo = 0x02`)

Para os comandos com `alvo = 0x02`, o corpo é uma **imagem do bloco de settings** que a roda
devolve no frame de RX tipo 8 (§5.4): o byte `i` do corpo (contando `op` e `alvo`) corresponde ao
offset `45 + i` do frame de RX, e todos os campos não usados vão como `0x80`.

Verificado campo a campo (15/15) `[APK BtManager.java:506-641]` × `[APK setting/control/*.java]`:

| Campo | Índice no corpo (após `len`) | Offset no RX |
|---|---|---|
| dureza do pedal | 5 | 50 |
| tiltback (stop speed) | 7 | 52 |
| limite de PWM | 8 | 53 |
| backlight | 10 | 55 |
| giroscópio/calibração | 11 | 56 |
| modo transporte | 12 | 57 |
| unidade | 13 | 58 |
| correção de tensão | 14 | 59 |
| modo bateria fraca | 15 | 60 |
| modo alta velocidade | 16 | 61 |
| volume do buzzer | 18 | 63 |
| tensão máx. de carga | 19 | 64 |
| assistência acel./desacel. | 21 | 66 |
| redução do acelerômetro | 23 | 68 |
| alarme de sobrepressão do freio | 24 | 69 |

Os índices 0 e 1 do corpo são `op` e `alvo`; o índice 1 casa com o **offset 46** do RX,
que é justamente o "tipo do frame" (`0x08` nos frames de settings — o número difere entre os
sentidos, portanto `alvo` não é cópia do tipo) `[INF]`.

#### Regra das variantes `6B` × `64`

- Em quase todo comando o app envia **as duas variantes concatenadas** no mesmo stream
  (`crc(frame6B) || crc(frame64)`) `[APK BtManager.java:263-267]`.
- Exceção: se `fullVersionCode` começa com `"004"` (ou seja, hardware `0040` = Patton), envia
  **somente** a variante `6B` `[APK BtManager.java:254-261]`.
- A variante `64` difere da `6B` por trocar `0x80` por `0x00` no byte `alvo` (índice 1 do corpo)
  e, no caso de "zerar viagem", por usar 4 bytes em vez de 2 (`[APK BtManager.java:82-90]`).
- Qual geração de roda aceita qual variante **não** é determinável estaticamente → §11.
- `[COM]` O header de 4 bytes `4C 3? 41 70` é o mesmo esquema descrito como
  `4C <cmd> 41 70` no plano multi-protocolo e nas implementações comunitárias.

### 4.2 RX — frames da roda

```
 0    1    2    3  …  45   46      47 …            n-4 .. n-1
DC   5A   5C  …  (bloco principal)  tipo   sub-bloco     (4 bytes finais)
```

- **Header fixo `DC 5A 5C`** `[APK BtManager.java:94-96,304]` `[COM]` (a mesma constante existe no
  APK do EUC World 2.66.0).
- O app **não valida CRC nem `len`**: ele apenas acumula bytes e, quando um chunk novo começa com
  `DC 5A 5C`, processa o acumulado como um frame completo `[APK BtManager.java:290-309]`.
- O byte **46** é o **tipo do frame** (o código o chama de início do "sub data", com a constante
  morta `SUB_DATA_START_INDEX = 46`) `[APK BtManager.java:51,394]`.
- Os **4 últimos bytes** são ignorados nos testes de tamanho (`size - 4 > offset`), o que sugere
  CRC32 no fim, como no TX `[APK BtManager.java:397-495]` `[INF]`.
- Frame principal válido: **≥ 47 bytes**; frames de 36..46 bytes são tratados como "modo ASCII"
  (§4.3) `[APK BtManager.java:319-335]`.

**Tipos de frame observados** `[APK BtManager.java:392-845]`:

| Tipo | Conteúdo |
|---|---|
| `0`, `4` | frame principal + dados extras (ângulo lateral, correntes dos packs, log) |
| `1`, `2`, `3` | BMS do pack **esquerdo** (células 1-15 / 16-30 / 31-36 + 37-120) |
| `5`, `6`, `7` | BMS do pack **direito** (idem) |
| `8` | bloco de **settings** (todos os campos de configuração) |
| `32` (`0x20`) | event log, 2 entradas por frame (índice de 8 bits) |
| `33` (`0x21`) | event log, 1 entrada por frame (índice de 12 bits + extras + string GBK) |
| `35` (`0x23`) | nome/URL do modelo (string GBK `nome,?,url`) |

### 4.3 "Modo ASCII" (frames curtos, 36..46 bytes)

Se o primeiro frame recebido tiver menos de 47 bytes, o app conclui que a roda usa o modo antigo
e passa a enviar **strings ASCII/GBK** em vez de frames binários para as ações
`[APK BtManager.java:322-335]`:

| Ação | String enviada |
|---|---|
| Buzzer/alarme | `OLDCMDb` `[APK BtManager.java:41]` |
| Zerar viagem | `CLEARMETER` `[APK BtManager.java:40]` |
| Ride mode soft/medium/hard | `SETs` / `SETm` / `SETh` `[APK BtManager.java:42-44]` |
| Farol | `SetLightON` / `SetLightOFF` `[APK BtManager.java:45-46]` |
| Entry em modo bootloader (firmware) | `AT+RINTOPRO` `[APK BtManager.java:39]` |

As configurações continuam sendo enviadas em binário nesse modo (só as ações viram string)
`[APK setting/control/*.java]`. O formato dos frames de RX nesse modo é o mesmo, apenas truncado.

---

## 5. Telemetria (RX)

### 5.1 Bloco principal (offsets 0..45) — presente em todo frame (o app exige ≥ 36 B)

Todos os campos de 16 bits são **big-endian**; campos de 32 bits são duas palavras BE com a
**palavra baixa primeiro** (equivale a little-endian de 16 bits) `[APK BtManager.java:932-934]`
`[APK Util.java:1071-1085]`.

| Offset | Tipo | Campo | Escala | Unidade | Notas |
|---|---|---|---|---|---|
| 0-2 | — | header `DC 5A 5C` | — | — | `[APK BtManager.java:94-96]` |
| 3 | u8 | desconhecido | — | — | não lido; provável contador/echo `[INF]` |
| 4-5 | u16 BE | **tensão** | ÷100 | V | `[APK BtManager.java:338-339]` |
| 6-7 | i16 BE | **velocidade** | ÷10 | km/h | sinal = sentido; o app exibe `abs()` `:340-345` |
| 8-11 | u32 (low word primeiro) | **viagem** | ÷1 | **m** | `:350`; UI divide por 1000 `[APK SecondPageFragment.java:115]` |
| 12-15 | u32 idem | **odômetro total** | ÷1 | **m** | `:351`; `[APK SecondPageFragment.java:116]` |
| 16-17 | i16 BE | corrente de fase ("machine current") | ÷10 | A | `:352-356`, `abs()` |
| 18-19 | i16 BE | **temperatura do controlador** | ÷100 | °C | `:357-361`; UI rotula "Control Board Temperature" |
| 20-21 | u16 BE | **timer de sleep** | ÷1 | s | `:362`; UI rotula "Sleep" `[APK res/layout/frag_second.xml]` |
| 22 | u8 | desconhecido | — | — | não lido |
| 23 | u8 | **chargeMode** | — | — | 0 = não carregando; >0 = carregando `:363`, `[APK CarDataPackageInfo.java:165-167]` |
| 24-25 | u16 BE | **alarme de velocidade** (danger) | ÷10 | km/h | `:364` |
| 26-27 | u16 BE | **tiltback** (stop speed) | ÷10 | km/h | `:365` |
| 28-30 | u24 BE (`[30][28][29]`) | **versão** | decimal | — | `HHHHSS` → HW `HHHH`, SW `SS` `:366,886-911` |
| 31 | u8 | **rideMode** | — | — | 1/2/3 = soft/medium/hard; ≥100 = percentual `:371` |
| 32-33 | i16 BE | **carPose** | ? | ? | usado só no upgrade: exige módulo ≥ 4000 `:372-376`, `[APK UpgradeActivity.java:228]` |
| 34-35 | u16 BE | **powerRate / "output"** (duty) | ÷10000 | fração 0..1 | `:377-378` |
| 36-37 | u16 BE | **batteryTempMode** | — | — | só 100/101/110/111 têm semântica `:387-391`, `[APK SecondPageFragment.java:128-138]` |
| 38-45 | — | desconhecido | — | — | não lido |
| 46 | u8 | **tipo do frame** | — | — | ver §4.2 |

**Campos derivados** (o app calcula, a roda não envia) `[APK BtManager.java:379-386]`:

```
corrente_bateria = corrente_fase × powerRate / 10000        [A]
potência         = corrente_bateria × tensão                [W]
```

> Interpretação física `[INF]`: `corrente_fase` é a corrente do motor e `powerRate` é o duty/PWM
> em passos de 0,01 % (0..10000); o produto é a corrente de bateria equivalente. Bate com o
> comportamento da UI ("Power Rate" `[APK res/values/strings.xml:240]`) e com o limite de PWM configurável
> em % (§8).

### 5.2 Sub-bloco de frames tipo `0`/`4`

| Offset | Tipo | Campo | Escala | Unidade | Notas |
|---|---|---|---|---|---|
| 50-54 | — | event log (quando `receivingLog`) | — | — | 2 entradas de 8 bits `:427-445` |
| 67-68 | i16 BE | **ângulo lateral** (`lrAngle`) | ÷100 | ° | `:397-406`; exibido em "Angle adjustment" |
| 69-70 | i16 BE | **corrente do pack esquerdo** | ÷100 | A | `abs()` `:407-416` |
| 71-72 | i16 BE | **corrente do pack direito** | ÷100 | A | `abs()` `:417-426` |

Para esses campos o frame precisa de `size ≥ 73` `[APK BtManager.java:397]`.

### 5.3 Bloco de BMS — ver §6.

### 5.4 Bloco de settings (tipo `8`) — offsets 47..69

Lido pelo app em `[APK BtManager.java:506-641]`:

| Offset | Tipo | Campo | Sentinela | UI |
|---|---|---|---|---|
| 47-49 | — | desconhecido (não lido) | — | — |
| 50 | u8 | **dureza do pedal / ride mode %** | 0x80 = não suportado | "Ride mode setting" |
| 51 | u8 | desconhecido neste frame (no frame tipo 5 o 51 é o `lockState`) | — | — |
| 52 | u8 | **tiltback (stop speed)** | 0x80 | "Tiltback speed setting" |
| 53 | u8 | **limite de PWM** | 0x80 | "PWM value setting" |
| 54 | u8 | desconhecido | — | — |
| 55 | u8 | **backlight** (%) | 0x80 | "Backlight adjustment" |
| 56 | u8 | **giroscópio/calibração** | 0x80 | "Calibration" |
| 57 | u8 | **modo transporte** (0/1) | 0x80 | "Transportation mode" |
| 58 | u8 | **unidade** (0 = km, 1 = mi) | 0x80 | switch de unidade |
| 59 | u8 | **correção de tensão** (byte assinado, 0,1 %) | 0x80 | "Voltage adjustment" |
| 60 | u8 | **modo bateria fraca** (0/1) | 0x80 | "Low battery mode" |
| 61 | u8 | **modo alta velocidade** (0/1) | 0x80 | "High speed mode" |
| 62 | u8 | desconhecido | — | — |
| 63 | u8 | **volume do buzzer** (%) | 0x80 | "Button volume adjustment" |
| 64 | u8 | **tensão máx. de carga** (0,1 V) | 0x80 | "Max charging voltage setting" |
| 65 | u8 | **base da tensão de carga** (V inteiros; default 145) | 0x80 → 145 | usado no display |
| 66 | u8 | **assistência acel./desacel.** (%) | 0x80 | "Acceleration and deceleration assist" |
| 67 | u8 | desconhecido | — | — |
| 68 | u8 | **redução do acelerômetro** (%) | 0x80 | "Accelerometer reduction" |
| 69 | u8 | **alarme de sobrepressão do freio** (%) | 0x80 | "Brake overpressure alarm" |

**Regra de capabilities**: campo = `0x80` significa "a roda/firmware não tem essa função"; a UI
esconde a opção correspondente `[APK setting/ControlActivity.java:392-436]`. Isso resolve
"o que muda por modelo" sem tabela fixa: **pergunte ao bloco de settings**.

### 5.5 Pegadinhas de parsing

| # | Armadilha | Detalhe |
|---|---|---|
| 1 | Campos de 32 bits | duas palavras BE com a **baixa primeiro** (`(hi*65536)+lo`, hi em 10-11 e lo em 8-9) `[APK BtManager.java:350-351]` |
| 2 | Sinal | o app corrige com `if (v > 32768) v -= 65536` — **`0x8000` (32768) não é corrigido** (off-by-one) `[APK BtManager.java:353-355]` |
| 3 | CRC de RX | não é validado; um frame corrompido entra como telemetria válida `[APK BtManager.java:304]` |
| 4 | Reassembly | acumula até aparecer um chunk começando com `DC 5A 5C`; se um frame for perdido, o anterior é **concatenado** ao seguinte `[APK BtManager.java:304-308]` |
| 5 | Janela de tamanho | os testes usam `size - 4 > offset` (assume 4 bytes de CRC no fim) `[APK BtManager.java:397-495]` |
| 6 | Sentinela | `0x80` = "não alterar"; um campo valendo 128 é indistinguível de "ausente" — por isso a UI usa 128 como "sem função" |
| 7 | Envio bloqueado | qualquer comando é **silenciosamente descartado** se não chegou frame nos últimos 1500 ms `[APK BtManager.java:215-216]` |
| 8 | Comando duplo | as variantes `6B`+`64` vão concatenadas: um comando = 2 frames = 2+ writes de 20 B `[APK BtManager.java:263-267]` |

---

## 6. BMS

Rodas com BMS detalhado enviam **3 frames por pack** (2 packs = 6 frames por ciclo) mais a
corrente de cada pack, que vem no frame principal (§5.2). Estrutura
`[APK BtManager.java:447-504,786-845]` `[APK BatteryGroupBean.java]`:

### 6.1 Tipos e conteúdo

| Tipo | Pack | Conteúdo | Offsets |
|---|---|---|---|
| `1` | esquerdo | **células 1..15** + nº de células | 53..82 (15 × u16 BE ÷1000 V); nº em 52 (2..120, fora da faixa → 36) |
| `2` | esquerdo | **células 16..30** + ângulo de corte + % bateria | 53..82; ângulo em 47; **% em 50** (0..100) |
| `3` | esquerdo | **temperaturas + células 31..36 + 37..120** | temps 47..58 (6 × i16 BE ÷100 °C); 59..70 (6 × u16 ÷1000); 71..238 (84 × u16 ÷1000) |
| `5` | direito | células 1..15 + **lockState** | 53..82; nº em 52; **lockState em 51** |
| `6` | direito | células 16..30 | 53..82 |
| `7` | direito | temperaturas + 31..36 + 37..120 | idem tipo 3 |

Observações:

- Todas as tensões de célula são **u16 BE ÷ 1000** (mV → V) `[APK BtManager.java:481,491]`.
- Temperaturas: **i16 BE ÷ 100** `[APK BtManager.java:471]`.
- O frame tipo 3/7 é **grande**: precisa de 239 bytes + 4 de CRC. Com MTU 120 isso exige
  reassembly (notificações de até 117 B) `[INF]`.
- O app só publica o pack quando recebeu as **4 partes**: células 1-15, 16-30, 31-36 e a
  corrente (frame principal) `[APK BatteryGroupBean.java:85-87]`.
- Agregação feita pelo app: mín/máx/Δ de célula, tensão total (soma), média, potência
  (`corrente_pack × tensão_total`) `[APK BatteryGroupBean.java:38-60]`
  `[APK BatteryGroupFragment.java:99-109]`.
- **% de bateria**: se o frame tipo 2 trouxer 0..100 no byte 50, o app usa esse valor
  ("new battery CUL model"); senão usa a tabela tensão→% por modelo (§9)
  `[APK BtManager.java:799-819]`.

### 6.2 `lockState` (byte 51 do frame tipo 5)

| Bit | Significado | Evidência |
|---|---|---|
| 0 | senha aplicada com sucesso (resposta ao comando de senha) | `[APK LockSettingActivity.java:149-151]` |
| 4 | **travada** (1) / destravada (0) | `[APK HomepageFragment.java:306-313]` |
| 5 | auto-lock ao desligar habilitado | `[APK LockSettingActivity.java:171-175]` |
| 6 | senha definida | `[APK LockSettingActivity.java:188]` |
| 7 | **lock não suportado** (esconde a UI) | `[APK HomepageFragment.java:306-313]` |

---

## 7. Odômetros e contadores

| Grandeza | Origem | Offsets | Unidade | Escala |
|---|---|---|---|---|
| Viagem | roda | 8-11 | m | u32, palavra baixa primeiro |
| Total | roda | 12-15 | m | idem |
| Timer de sleep | roda | 20-21 | s | u16 |
| Tempo de sessão / tempo total de uso | **app** | — | — | contador local de 1 s, persistido por MAC `[APK BtManager.java:175-178]` `[APK SecondPageFragment.java:175-197]` |

- **Zerar viagem**: comando dedicado (§8, #7). A UI exige que a roda esteja parada e não
  carregando **antes** de enviar? Não — ela só avisa "não pode com velocidade > 0 ou carregando"
  na tela de desligar; para zerar a viagem há só confirmação `[APK HomepageFragment.java:201-213]`.
- **Não há horímetro** vindo da roda (o "tempo total" é local do app) → lacuna §11.
- Conversão para milhas é **do app** (×0,6214) `[APK UnitManager.java:105-112]`.

---

## 8. Configuração (escrita)

### 8.1 Tabela completa de comandos

`F` = `0x80` (não alterar); `v` = 1 byte de valor. A coluna **corpo** é tudo o que vem **depois
do byte `len`** (portanto `op`, `alvo` e o payload). `len` = 4 (header) + 1 (`len`) + corpo + 4 (CRC).
Todos os frames levam CRC32 BE no fim.

| 1 | Alarme de velocidade | 17 | `01 00 F ×5 v` | `01 F ×6 v` | 10..120 km/h (passo 1) | SetAlarmSpeed |
| 2 | Tiltback (stop speed) | 17 | `01 02 F ×5 v` | — | 10..120 km/h | StopSpeed |
| 3 | Ride mode contínuo | 12 | `01 00 v` | `01 F v` | `v = 10 + %` (10..110) | SetRideMode/Home |
| 4 | Ride mode discreto | 12 | `01 00 v` | `01 F v` | 1 = soft, 2 = medium, 3 = hard | SetRideMode/Home |
| 5 | Farol | 13 | `01 00 F v` | `01 F F v` | 0 = off, 1 = on | Home |
| 6 | Buzzer / alarme sonoro | 14 | `00 00 F F 01` | `00 F F F 01` | — | Home ("Alarm") |
| 7 | Zerar viagem | 13 | `00 02 F 01` | `00 01` (len **11**) | — | Home |
| 8 | Desligar em 10 s | 22 | `01 00 F ×9 01 F` | `01 F ×10 01 F` | — | Home |
| 9 | Corte lateral (queda) | 22 | `01 00 F ×10 v` | `01 F ×11 v` | 35..75° | SetFallProtectionAngle |
| 10 | Modo transporte | 22 | `01 02 F ×10 v` | — | 0/1 | Control |
| 11 | Ajuste de ângulo (offset) | 16 | `01 00 F ×4 v` | `01 F ×5 v` | `v = 80 + 10×ângulo`, −8,0..+8,0° | SetAngel |
| 12 | Calibração do giroscópio | 21 | `01 02 F ×9 01` | — | 1 = inicia/para (toggle) `[INF]` | Gyroscope |
| 13 | Dureza do pedal | 15 | `01 02 F ×3 v` | — | 0..100 | PedalSoftness |
| 14 | Limite de PWM | 18 | `01 02 F ×6 v` | — | 30..100 % | StopPower |
| 15 | Backlight | 20 | `01 02 F ×8 v` | — | 0..100 % | ScreenBacklight |
| 16 | Unidade | 23 | `01 02 F ×11 v` | — | 0 = km, 1 = mi | UnitSwitch/Control |
| 17 | Correção de tensão | 24 | `01 02 F ×12 v` | — | byte assinado −15..+15 (passo 0,1 %; UI −1,5..+1,5 %) | VolLight |
| 18 | Modo bateria fraca | 25 | `01 02 F ×13 v` | — | 0/1 | Control |
| 19 | Modo alta velocidade | 26 | `01 02 F ×14 v` | — | 0/1 | Control |
| 20 | Volume do buzzer | 28 | `01 02 F ×16 v` | — | 0..100 % | KeyTone |
| 21 | Tensão máx. de carga | 29 | `01 02 F ×17 v` | — | 0..120 → `base + v/10` V | MaxChargePower |
| 22 | Assistência acel./desacel. | 31 | `01 02 F ×19 v` | — | 0..100 % | SetUpDownSpwwdHelp |
| 23 | Redução do acelerômetro | 33 | `01 02 F ×21 v` | — | 0..100 % | SetUpSpeedCul |
| 24 | Alarme de sobrepressão do freio | 34 | `01 02 F ×22 v` | — | 80..125 % | Brake |
| 25 | Sincronizar hora | 18 | `00 05 AA MM DD hh mm ss tz` | — | ano−2000, mês 1..12, tz em horas | init |
| 26 | Senha / lock | 25 | `00 05 AA MM DD hh mm ss tz p1 p2 p3 op n1 n2 n3` | — | ver §8.3 | Lock/Home |
Observações:

- **Não existe byte de número de comando**: a operação é identificada pela combinação
  `(len, byte 1 do corpo)` e pela **posição do valor** dentro do bloco (o resto vai como `0x80`).
  Por isso dois comandos diferentes podem ter o mesmo `len` (ex.: #1 e #2, #8 e #9) — o que os
  distingue é o byte 1 (`0x00` × `0x02`).
- O byte 1 do corpo é `0x00`, `0x02` ou `0x05` na variante `64`; na variante `6B` ele é sempre
  filler `0x80` (ou `0x80`→`0x00` conforme a variante).
- Os corpos exatos, token a token, estão em `analysis/leaperkim/tx_frames.json`; exemplos com CRC
  em `analysis/leaperkim/exemplos-frames.txt`.

### 8.2 Valores, respostas e restrições

- **Leitura de volta**: não há ACK. O app reenvia/atualiza a UI com o próximo frame de settings
  (tipo 8) ou com o frame principal `[APK setting/ControlActivity.java:454-462]`.
- **Validação do app**: limites são só os do SeekBar (tabela acima) — **o app não valida nada
  antes de enviar** `[APK setting/control/BaseSetProgressActivity.java:48-62]`.
- **Restrições de firmware** (pelo próprio app):
  - Calibração do giroscópio só com velocidade 0 `[APK GyroscopeSettingActivity.java:70-75]`.
  - Firmware só com a roda "deitada" (§10) `[APK UpgradeActivity.java:225-232]`.
  - Desligar em 10 s só com velocidade 0 e fora de carga `[APK HomepageFragment.java:191-198]`.
  - No "modo ASCII" (frames curtos) o app **oculta** o "desligar em 10 s" e passa a mostrar o
    botão de ride mode (que vira `SETs/SETm/SETh`); "zerar viagem" continua disponível como
    string `CLEARMETER` `[APK HomepageFragment.java:342-348,215-249]`.
- **Campos ausentes** (`0x80` no settings) devem ser escondidos na UI, não enviados.
- **Modo ride contínuo × discreto**: `continuousSoftHardSet` do modelo decide o formato
  (§9). Enviar `1/2/3` para um modelo contínuo (ou 10..110 para um discreto) é ignorado
  `[INF]`.

### 8.3 Senha e lock

Frame de senha = frame de **hora** com `len` corrigido para `18+7 = 25` e 7 bytes anexados
`[APK Util.java:258-275]`:

```
4C 64 41 70 19  00 05 AA MM DD hh mm ss tz  p1 p2 p3  op  n1 n2 n3  <CRC32>
```

- `p1..p3` = senha atual como inteiro de 6 dígitos em **3 bytes big-endian** (ex.: `123456` →
  `01 E2 40`); string vazia → `00 00 00` `[APK Util.java:258-275]`.
- `n1..n3` = senha nova (mesmo formato).
- `op`:

| op | Ação | Evidência |
|---|---|---|
| `0` | **unlock** | `[APK HomepageFragment.java:253-254]` |
| `1` | **lock** | idem |
| `2` | auto-lock **ON** | `[APK LockSettingActivity.java:116-118]` |
| `3` | auto-lock **OFF** | idem |
| `11` (`0x0B`) | definir / trocar / limpar senha | `[APK LockSettingActivity.java:86-133]` |

- Resultado é lido 1 s depois no `lockState` (bits 0 e 4..7, §6.2)
  `[APK LockSettingActivity.java:135-179]` `[APK HomepageFragment.java:253-275]`.
- O app guarda a senha por nome do dispositivo em SharedPreferences
  `[APK HomepageFragment.java:229,268]`.

### 8.4 Pegadinhas de escrita (comandos ignorados, unidades, bugs)

| # | Situação | Efeito / mitigação |
|---|---|---|
| 1 | Sem telemetria nos últimos 1,5 s | comando **descartado em silêncio** `[APK BtManager.java:215-216]` → implementar timeout explícito e reenviar |
| 2 | Modelo contínuo × discreto | `1/2/3` em modelo contínuo (ou 10..110 em discreto) é ignorado `[INF]`; decidir por `continuousSoftHardSet` (§9) |
| 3 | Campo `0x80` no settings | função ausente neste firmware: **não enviar** |
| 4 | HW `004x` (Patton) | app manda **só** a variante `6B`; nas demais manda as duas `[APK BtManager.java:254-261]` |
| 5 | Calibração do giroscópio | exige velocidade 0; o app manda o **mesmo** frame para iniciar e parar (toggle) `[APK GyroscopeSettingActivity.java:70-75,121-123]` `[INF]` |
| 6 | Modo ASCII (frame < 47 B) | ações viram strings (`CLEARMETER`, `SETx`, `SetLightON/OFF`, `OLDCMDb`) e o "desligar em 10 s" desaparece `[APK BtManager.java:322-335]` |
| 7 | Unidades | a roda **sempre** fala métrico (km/h, m, °C); milhas são conversão do app (×0,6214) `[APK UnitManager.java:105-112]` |
| 8 | Sem ACK | a confirmação é o próximo frame (settings/main/lockState); não existe resposta síncrona |
| 9 | Ride mode contínuo | valor enviado = 10 + %, mas o reportado é **valor + 90** (100..200 no frame) `[APK SetRideModeActivity.java:100-119]` `[APK ControlActivity.java:376-380]` |
| 10 | `correção de tensão` | byte **assinado**; 0x80 (128) é sentinela, não "+12,8 %" |

---

## 9. Modelos e especificidades

Tabela embutida no APK (`Util.CAR_DATA_JSON`, versão 9) `[APK Util.java:27]`
`[APK CarDataManager.java:38-48]`:

| `sNum` | Nome | HW | Sistema | Ride mode | Faixa da tabela SOC (0,01 V) | Tensão a 100 % |
|---|---|---|---|---|---|---|
| 10 | **Patton-S** | `0070` | 126 V | contínuo | 9450 → 12375 | 123,75 V |
| 9 | **Sherman-L** | `0060` | 151,2 V | contínuo | 11340 → 14850 | 148,50 V |
| 8 | **LYNX** | `0050` | 151,2 V | contínuo | 11340 → 14850 | 148,50 V |
| 7 | **Patton** | `0040` | 126 V | discreto (1/2/3) | 9450 → 12375 | 123,75 V |
| 6 | **Sherman-s** | `0030` | 100,8 V | discreto | 7560 → 9900 | 99,00 V |
| 5 | **ShermanMax** | `0011` | 100,8 V | discreto | 7560 → 9900 | 99,00 V |
| 4 | **Abrams** | `0020` | 100,8 V | discreto | 7560 → 9900 | 99,00 V |
| 3 | **Sherman** | `0010` | 100,8 V | discreto | 7560 → 9900 | 99,00 V |
| 2 | *Firmware downloader* | `6661` | 4,2 V | — | 315 → 413 | 4,13 V |
| 1 | *Firmware downloader* | `6660` | 4,2 V | — | 315 → 413 | 4,13 V |

- `6660/6661` são **pseudo-modelos** usados pelo bootloader/DFU (bateria de 1 célula), não são rodas.
- A tabela SOC tem **100 pontos** (1 % por ponto), em centivolts do **pack**, por modelo
  (`analysis/leaperkim/soc_tables.csv`). Cálculo: menor índice cujo valor ≥ tensão; `≤ tabela[0]`
  → 0 %, `≥ tabela[99]` → 100 % `[APK Util.java:92-108]`.
- Modelos com BMS detalhado mandam % direto (byte 50, §6.1) e a tabela deixa de ser usada
  `[APK BtManager.java:799-819]`.

### 9.1 Como o app identifica o modelo

Não há detecção de protocolo por nome/serviço: o app **lê o hardware code** dos 4 primeiros
dígitos da versão (`[30][28][29]`) e:
1. procura o modelo na tabela acima (nome/URL/imagem) `[APK CarDataManager.java:38-48]`;
2. escolhe o layout de ride mode (`continuousSoftHardSet`) `[APK SetRideModeActivity.java:79-100]`;
3. escolhe a tabela de bateria `[APK Util.java:84-90]`.

O nome/imagem também podem vir da roda (frame tipo 35, string GBK `nome,?,url`)
`[APK BtManager.java:754-783]`.

### 9.2 O que muda por modelo

| Aspecto | Comportamento |
|---|---|
| Ride mode | contínuo (0..100 %) em Patton-S/Sherman-L/Lynx; 3 modos em Patton e anteriores |
| Settings disponíveis | definidos pelo firmware no bloco de settings (`0x80` = ausente) |
| % bateria | BMS (novos) ou tabela tensão→% (antigos) |
| BMS detalhado | presente nos modelos com BMS; o app **não** condiciona por HW — se os frames 1..7 chegarem, mostra `[APK BtManager.java:447-504]` |
| Lock | bit 7 do `lockState` esconde a UI; senha só com bit 6 |
| Variante de comando | HW `004x` recebe só `6B`; demais recebem `6B`+`64` |

---

## 10. Firmware e event log

### 10.1 Firmware

1. **Download**: `http://www.baat22.com:800/LKAPP/firm/latest/` e `.../manual/`
   `[APK UpgradeActivity.java:45]` `[APK EditUpgradeActivity.java:44]`, com listagem em
   `_firlist.txt` e arquivos `<versao_com_underscores>.bin` `[APK EditUpgradeActivity.java:261,383]`.
2. **Compatibilidade**: a versão do arquivo (sem pontos) precisa **começar** com o hardware code
   atual `[APK EditUpgradeActivity.java:129-134]`.
3. **Pré-condição**: `|carPose| ≥ 4000` (roda deitada/inclinada), senão recusa
   `[APK UpgradeActivity.java:225-232]`.
4. **Entrada no bootloader**: envia a string `AT+RINTOPRO` (GBK) e **repete a cada 2–3 s
   enquanto ainda chegam frames** de telemetria `[APK UpgradeActivity.java:247-259]`.
5. **Transferência**: envia o `.bin` **cru** (sem framing/CRC) em chunks de 20 B, com
   progresso a cada 20 pacotes `[APK UpgradeActivity.java:270]` `[APK BluetoothLeService.java:485-497]`.
6. **Verificação**: espera a telemetria voltar (até 15 s) e compara `fullVersionCode` com a
   versão instalada `[APK UpgradeActivity.java:260-300]`.

### 10.2 Event log

- Comando: `CMD_READ_LOG` = `4C 6B 41 70 14 01 80 80 80 80 80 80 80 80 80 01` (variante 6B) e
  `4C 64 41 70 14 01 00 80 80 80 80 80 80 80 80 01` (64), enviados juntos
  `[APK BtManager.java:80,89]` `[APK LogUploadActivity.java:129-131]`.
- A partir daí a roda manda frames tipo **32** e **33** (§4.2). O app para quando vê índice
  `255` (modo 32) ou `4095` (modo 33) `[APK LogUploadActivity.java:262-300]`.

**Frame tipo 32** (2 entradas) `[APK BtManager.java:642-696]`:

| Offset | Campo |
|---|---|
| 47 | índice (8 bits) |
| 48-49 | código do evento (u16 BE) |
| 50-53 | timestamp (u32 BE, epoch **segundos**) |
| 54-58 | 5 bytes de dados (`extra1..5`) |
| 59-70 | 2ª entrada (mesmo layout, índice+1) |

**Frame tipo 33** (1 entrada) `[APK BtManager.java:697-753]`:

| Offset | Campo |
|---|---|
| 47-48 | total de eventos (`b47*16 + b48/16`) |
| 48-49 | índice de 12 bits (`(b48%16)*256 + b49`); `4095` = fim |
| 50-53 | timestamp (u32 BE, segundos) |
| 54-55 | código do evento (u16 BE) |
| 56 | `n` = quantidade de extras de 4 bytes |
| 57..57+4n−1 | `n` × i32 BE |
| 57+4n .. | string GBK terminada em `0x00` |

- `content` (código do evento) **não é traduzido** pelo app: ele mostra o número cru
  `[APK LogItemInfo.java:178-184]`. A tabela de códigos é lacuna (§11).
- Timestamp é formatado como hora local `[APK LogItemInfo.java:203-219]`.

---

## 11. Limitações e lacunas

1. **CRC dos frames de RX** não é validado pelo app; a hipótese de 4 bytes finais = CRC32 BE
   (mesma função do TX) é `[INF]` → confirmar com captura.
2. **Bytes não mapeados**: `45` (RX), `48-49`, `51`, `54`, `62`, `67` (settings),
   `22`, `38-45` (frame principal) e o byte 3 do header. O offset 65 é lido (base de tensão de
   carga) mas o app não o escreve.
3. **`batteryTempMode`** (36-37): só 100/101/110/111 têm semântica ("normal"/"alta"); provável
   bitmask de 3 sensores, não confirmado.
4. **`carPose`**: unidade desconhecida (o app só compara com ±4000); suspeita de 0,01°.
5. **`shutDownTime`**: rótulo "Sleep" — é contagem para dormir, tempo desde o boot ou
   temporizador? Não verificável estaticamente.
6. **Variantes `6B` × `64`**: qual geração aceita qual (e por que o Patton recebe só `6B`).
7. **Modo ASCII**: o app só parseia os mesmos offsets; não se sabe se o frame curto tem campos
   diferentes, nem se o comando de init (`AT+RINTOPRO`) é necessário para entrar/sair.
8. **Event log**: tabela de códigos e semântica dos extras.
9. **BMS no Sherman original**: se HW 0010 envia os frames 1..7.
10. **Horímetro**: a roda não expõe horas de uso; só o timer de sleep.
11. **Nenhuma captura real** foi usada: todos os offsets vêm do parser do app. É preciso
    validar tensão/velocidade/odômetro contra a roda (ou contra a tela dela).
12. A taxa real de frames (1 Hz?) é inferida do watchdog de 1500 ms.
13. **Divergência com o fork Nosfet** (`protocolos/nosfet.md` §8.2): aqui o alarme de sobrepressão do
    freio fica no offset **69** do frame de settings (§5.4) e o comando de escrita tem `len` **34**
    (§8.1); o fork o documenta no offset **65** (frame `4C 64 41 70 1E …`, `len` **30**) — e os campos
    `@65` (base da tensão de carga), `@66` (assistência acel./desacel.) e `@68` (redução do
    acelerômetro) só estão documentados **neste** doc → ⚠️ pendente de reconciliação via captura
    (layout pode variar por firmware/modelo, ou um dos lados está deslocado).

---

## 12. Evidências e referências

**Artefato e método**: ver `analysis/leaperkim/evidencias.md` (identidade, comandos, achados
brutos com linha de código, verificação cruzada e lacunas).

**Arquivos de apoio**:

| Arquivo | Conteúdo |
|---|---|
| `analysis/leaperkim/src/` | árvore JADX completa |
| `analysis/leaperkim/apktool-out/` | recursos (manifest, `strings.xml`, layouts) |
| `analysis/leaperkim/exports/` | classes-chave copiadas (`BtManager`, `Util`, `BluetoothLeService`, `BatteryGroupBean`, `ControlSettingData`, `CarDataPackageInfo`, `HomepageFragment`, `ControlActivity`, `LockSettingActivity`, `control/*`) |
| `analysis/leaperkim/tx_frames.json` | todos os frames TX do app (tokens, `len`, CRC) |
| `analysis/leaperkim/exemplos-frames.txt` | frames de exemplo com CRC calculado |
| `analysis/leaperkim/car_data_json.json` | tabela de modelos/SOC original |
| `analysis/leaperkim/soc_tables.csv` | 100 pontos de tensão→% por modelo |

**Referências cruzadas**:

- `[APK]` app oficial LeaperKim 1.4.8 (fonte primária deste documento).
- `[COM]` EUC World 2.66.0: constante `DC 5A 5C` presente no DEX (header de RX); o código de
  protocolo do EUC World não é recuperável estaticamente (sem classes de adapter de roda no DEX).
- `[COM]` WheelLog (GPL): mesma família de header `4C 3? 41 70` / `DC 5A 5C`. **Nenhum código foi
  copiado**; apenas fatos de framing.
- `docs/07-plano-multi-protocolo.md` §3.5 (contexto; corrigido por este documento quanto a
  `FFE0/FFE1` e à posição dos bytes de controle).
