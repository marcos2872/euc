# Protocolo KingSong — Referência técnica

Extraído por engenharia reversa do **app oficial KingSong** (`com.kingsong.dlc` 4.9.87,
sha256 `08dc8cab…e842`) e cruzado com o **EUC World 2.66.0** já descompilado em
`analysis/decompiled/sources` (adaptador KingSong = `p364uh/C5909c0.java`).

> **Proveniência:** **[APK]** = app oficial descompilado (referências `arquivo:linha` apontam para
> os fontes em `analysis/kingsong/jadx/`); **[EUCW]** = EUC World 2.66.0
> (`analysis/decompiled/sources/p364uh/C5909c0.java`); **[INF]** = inferência; **[LAC]** = lacuna
> que exige captura BLE ou teste na roda.
> O protocolo KingSong **não tem escape/stuffing** (diferente do Inmotion) e usa quadros de
> **20 bytes** com ou sem CRC de 2 bytes.

---

## 1. Visão geral

| Item | Valor |
|---|---|
| Marca / app oficial | KingSong / `com.kingsong.dlc` 4.9.87 (versionCode 245), minSdk 29, targetSdk 36 |
| Famílias de resposta | `AA 55 …` (clássica, usada por WheelLog/EUC World) e `F1 EF …` (usada pelos modelos novos — KS-S20/S22 e derivados) **[APK]** |
| Comprimento do quadro | 20 bytes fixos (2 de header + 16 de payload + 2 de rodapé/CRC); quadros de nome/serial/firmware são maiores (payload extra após os 20 bytes) |
| Checksum | (a) sem CRC: rodapé fixo `5A 5A`; (b) CRC-16/CCITT-FALSE (poly `0x1021`, init 0) big-endian; (c) soma de 16 bits little-endian |
| BLE | serviço `FFE0` + característica `FFE1` (write+notify); variante `AD00/AD01/AD02`; DFU `02f00000-…-ff02` |
| Auth | **não há handshake criptográfico**; senha de 6 dígitos por comando (`0x41`/`0xA5`) |
| Modelos | todos compartilham o mesmo protocolo; capacidades/limites vêm do servidor (`MachineConfigBean`) — ver §9 |
| Arquivos de referência | `analysis/kingsong/fx_tabela.txt` (catálogo TX extraído), `analysis/kingsong/rx_codes_brutos.txt` |

**[APK]** Enum de protocolos do EUC World (`AbstractC5919h0.java:528-551`) confirma que este
adaptador é o `KINGSONG` (`C5909c0`); a mesma tabela de quadros é usada para Rockwheel (`RW…`) e
LAMAX/GT (nome BLE), que são rebrands.

---

## 2. Transporte BLE

### 2.1 UUIDs

| Papel | UUID | Onde | Notas |
|---|---|---|---|
| Serviço principal | `0000ffe0-0000-1000-8000-00805f9b34fb` | `BleService.java:76` | [APK] |
| Característica principal (write + notify) | `0000ffe1-0000-1000-8000-00805f9b34fb` | `BleService.java:77` | write type **1 = WRITE_TYPE_DEFAULT (com resposta)**; `BleService.java:988-991` |
| Transporte alternativo (add-on/DFU-like) | `0000ad00-…` (serviço), `0000ad01-…` (write), `0000ad02-…` (notify) | `BleService.java:80-82` | o app tenta **AD00/AD02 primeiro** (`z0()`, linha 1745) e só depois FFE0/FFE1 (`y0()`, linha 1735) |
| Serviço DFU | `02f00000-0000-0000-0000-00000000ff02` | `BleService.java:64` | botão de entrada no bootloader |
| DFU auxiliares | `02f00000-…-fe00`, `02f00000-…-ff01` | `BleService.java:83-84` | usados pelo firmware update **[INF]** |
| CCCD | `00002902-0000-1000-8000-00805f9b34fb` | `BleService.java:65` | |
| Info de dispositivo | `0000180a-…`, `00002a23-…` (serial) | `BleService.java:78-79` | leitura opcional |

### 2.2 Scan

**[APK]** `MainCarFrgm.java:606-678`:
1. Nome do dispositivo é comparado (case-insensitive, `startsWith`) com a lista de prefixos
   baixada do servidor (`AllVehiclesTwoBean.bluetoothName`).
2. Sem lista (offline), aceita nomes que começam com **`KS`**, **`lamax`** ou **`gt`**.
3. Caso especial: se o `ScanRecord` (advertising data) contiver a sequência de bytes
   `02 66 66` → modelo **E1** (scooter), independente do nome.
4. Filtros de serviço BLE **não** são usados no scan (o EUC World usa e é mais restritivo).

### 2.3 Parâmetros de conexão / escrita

| Parâmetro | Valor | Fonte |
|---|---|---|
| MTU solicitado | **512** (`d0(bleDevice, 512, cb)`) | `BleService.java:1724` |
| Tamanho de bloco | **20 bytes** (todo comando é um quadro de 20 B) | `fx`/`BleService` |
| Write com resposta | sim (`setWriteType(1)`) | `BleService.java:989` |
| Fila de envio | `LinkedBlockingQueue(20)`, duas filas (`q`/`r`); modelos E1 usam fila 8 | `BleService.java:328-329, 1421-1424` |
| Ritmo entre pacotes | **≥50 ms** após o `onWriteSuccess` | `BleService.java:1066, 1116` |
| Retentativas | 3 tentativas por pacote; a cada 5 falhas acumuladas → aborta e limpa filas | `BleService.java:581-591, 714-733, 1449-1512` |
| Watchdog | 1000 ms por escrita (mensagem 0/1), 500 ms para o segundo fluxo | `BleService.java:899, 1032` |
| Config. FastBle | reconnect 4× / 2500 ms; timeout de conexão 20 s; timeout de escrita 3000 ms; `c0(1)` (split-write) | `BleService.java:1432-1434` |
| Reconexão do app | até 7 tentativas a cada 2 s; antes de conectar derruba conexões GATT "órfãs" e remove bond | `BleService.java:437, 1199-1212, 1656-1689` |
| Timeouts (constantes) | `U=50, V=1000, W=3000, X=5000` ms | `BleService.java:72-75` |

**[APK]** Notificação recebida → `BleService$c.e(byte[])` (linha 300) repassa para os listeners e
emite o broadcast `bluetooth_data` com o `byte[]` extra `"data"`; o parser vive em
`MainFragmentAty$b.onReceive` → `MainFragmentAty.a2(byte[])` (`MainFragmentAty.java:657-695`).

---

## 3. Conexão e inicialização

**[APK]** Sequência observada:

| # | Ação | Como |
|---|---|---|
| 1 | Conexão GATT | `BleService.W/X(addr)` → `connect`; 200 ms depois pede MTU 512 |
| 2 | Habilitar notify | `z0()`: `AD00/AD02` (fallback `FFE0/FFE1`, `y0()`) |
| 3 | Antes de liberar o app | `MainFragmentAty` limpa estado local, assina recebedor e envia os primeiros comandos |
| 4 | `fx.U1(1)` e `fx.U1(2)` (50 ms/100 ms) | comandos de "startup"/serial do BMS (`MainFragmentAty.java:618-619`) |
| 5 | 4,5 s após notify OK | `TimerTask` envia `fx.K0()` = `AA 55 … 00 00 … 5E 14 5A 5A` (`BleService.java:274-282`) |
| 6 | `fx.i2(serial)` quando o serial local é conhecido | `MainFragmentAty.java:564, 2605` |
| 7 | Poll contínuo | `fx.V1()` (quadro `AA 55 00…00 00 14 5A 5A`) a cada **1000 ms**; se houver ≥2 dispositivos conectados, a cada **500 ms** (`BleService.java:886-899`) |
| 8 | Leituras de settings | disparadas pelas telas (`fx.S(...)`, `fx.*()` — ver §8) |
| 9 | Senha | se a roda exigir: `fx.a1(...)` (cmd `0x41`) e família `0xA5` (ver §8.3) |

**[APK]** O app também baixa uma lista de comandos do **servidor** (`InitCommandBean`) e a envia
em sequência (hex em `commandInfo`, 3 repetições, 200 ms —
`MainFragmentAty.java:724-729` + `CommandBle`). Isso significa que parte do init pode variar por
firmware/modelo sem estar no APK. **[LAC]** capturar essa lista (log do app) para replicar.

**[EUCW]** O EUC World executa máquina de estados de init/leitura com timeouts de 500/3000 ms
(`m10977K`, `m10978L`) — usar como referência de ordem e de retry.

---

## 4. Framing

### 4.1 Formato A (clássico, usado por WheelLog/EUC World)

```
offset  0  1   2 .......... 15   16   17   18  19
        AA 55  dados (14 B)      CMD  14   5A  5A
```

**[EUCW]** `C5909c0.java:1426-1435` (`m10984y`): quadro de 20 bytes com `[0]=0xAA`, `[1]=0x55`,
`[17]=0x14`, `[18]=[19]=0x5A`; `[16]` é o **byte de comando**.
**[APK]** As mesmas constantes aparecem em dezenas de métodos de `defpackage.fx` (ex.:
`fx.E0` cmd `0x53`, `fx.G0` cmd `0x50`, `fx.D0` cmd `0x54` — `fx.java:328-338, 176-185`).

### 4.2 Formato B (com CRC-16) — usado por BMS, luzes, firmware e comandos "novos"

```
offset  0  1   2 .......... 15   16   17   18     19
        AA 55  dados (14 B)      CMD  TIPO CRChi  CRClo
```

**[APK]** CRC em `fx.N(byte[])` (`fx.java:756-765`) = CRC-16/CCITT-FALSE sobre os 16 bytes
`[2..17]`, big-endian em `[18..19]`. O byte `[17]` deixa de ser `0x14` e passa a ser um
"bloco"/subtipo: `0xE3/0xE4` (leitura/escrita de packs BMS), `0x01`, `0x10/0x11/0x12/0x20`, etc.
Verificação de resposta: `fx.I(frame, hi, lo)` (`fx.java:437-449`).

### 4.3 Formato C (soma de 16 bits) — comandos NFC/calibração

**[APK]** `fx.X(payload)` (`fx.java:1275-1284`): soma simples dos 16 bytes mod 65536, gravada
**little-endian** em `[18..19]` (`[18]=lo`, `[19]=hi`). Usada por `fx.K/fx.d0/fx.e0/fx.l0/fx.Z1`.

### 4.4 Quadros estendidos (nome/serial)

**[APK]** `fx.y(nome, tipo, page)` (`fx.java:2769-2789`):
`AA 55 00 00 00 00 <len> <crcHi> <crcLo> 00 00 00 00 00 <page> EB <tipo> <sumLo> <sumHi> <nome…>`
— o tamanho em `[6]`, o CRC-16 do nome em `[7..8]`, a soma de `[2..17]` em `[18..19]` e o nome
UTF-8 **anexado após os 20 bytes**.

### 4.5 Rodapé de resposta e exemplo hex

**[APK]** Respostas chegam com `[0]=0xAA,[1]=0x55` e o **código de resposta em `[16]`** (o app
testa `data[16]` em todo o parser). Exemplos montados a partir dos construtores:

```
poll (1 Hz)             AA 55 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 14 5A 5A
read auto-off (EUCW)    AA 55 00 00 00 00 00 00 00 00 00 00 00 00 00 00 3F 14 5A 5A
write auto-off 600 s    AA 55 01 00 58 02 00 00 00 00 00 00 00 00 00 00 3F 14 5A 5A
read conteúdo display   AA 55 02 00 00 00 00 00 00 00 00 00 00 00 00 00 1B 14 5A 5A
write modo display 1    AA 55 03 00 01 00 00 00 00 00 00 00 00 00 00 00 1B 14 5A 5A
write limite de carga   AA 55 09 00 64 00 00 00 00 00 00 00 00 00 00 00 8A 14 5A 5A
write lateral tilt 5°   AA 55 03 00 32 00 00 00 00 00 00 00 00 00 00 00 8A 14 5A 5A   ([2]=3, [4..5]=50=5.0°×10)
read BMS1 pág. 2        AA 55 02 00 00 00 00 00 00 00 00 00 00 00 00 00 F1 01 5A 5A   ([17]=1 → BMS1_2)
lock (com senha)        AA 55 01 00 00 00 00 00 00 00 d0 d1 d2 d3 d4 d5 5D 14 5A 5A
```

> Nota: os quadros de leitura podem ser enviados com `[17]=0x00` (via `fx.S(byte)`, ex.:
> `AA 55 00…<cmd> 00 …5A5A`); o firmware aceita ambos. O EUC World sempre usa `0x14`.

---

## 5. Telemetria (leitura)

**[APK]** Parser principal: `MainFragmentAty.a2(byte[])` — `MainFragmentAty_fallback.java:5267-14113`
(cadeia de `if/else` sobre `data[16]`). Nomes/validação cruzada: **[EUCW]** `C5909c0.java:2160-2700`.

### 5.1 Códigos de resposta da família `AA 55`

| `data[16]` | Conteúdo | Campos (offsets, tipo, escala) | Fonte |
|---|---|---|---|
| `0xF1` + `[17]=0x00..0x07` | **BMS1 páginas 1..8** | ver §6 | [APK] 6156-6600 |
| `0xF1` + `[17]=0xD0/0xD1` | BMS1 páginas extras (9/10) | ver §6 (BMS1_12/13 em modelos maiores) | [APK] 6600-7400, 10900-11600 |
| `0xF2` + `[17]=0x00..0x07` / `0xD0/D1` | **BMS2 páginas 1..8** / extras | ver §6 | [APK] 7389-7800 |
| `0xA9` | **telemetria principal** | `[2..3]` u16 LE ÷100 = tensão (V) · `[4..5]` u16 LE ÷100 = velocidade (km/h) · `[6..9]` u32 "word-swapped" ÷1000 = **odômetro total** (km) · `[10..11]` u16 LE ÷100 = corrente (A) · `[12..13]` i16 LE ÷100 = temperatura (°C) · `[22+2i]` u16 LE = tensões de célula (i=0..n) · `[14]/[15]` = modo (ver 5.2) | [APK] 9500-9720; [EUCW] `b12==-87` (0xA9) |
| `0xB9` | **viagem/estado do evento** | `[2..5]` u32 word-swapped ÷1000 = viagem (km) · `[6..7]` u16 = tempo ligado (s) · `[8..9]` u16 LE ÷100 = velocidade média · `[10]` = bluetooth audio mode · `[11]` = voice mode · `[12]` = status do fan · `[13]` = status de carga · `[14..15]` u16 LE ÷100 = temperatura do controlador (°C, clamp -10..200) | [APK] 9932-10169 |
| `0xB5` | **alarmes de velocidade/tiltback** | `[4..5]`,`[6..7]`,`[8..9]`,`[10..11]` = 4 × u16 LE → alarme 1/2/3 + "wane speed" | [APK] 8832-8930 |
| `0xA2` | **modo de condução** | `[2]=1` OK, `[3]=0` OK, `[4]` = 0 play / 1 ride / 2 study | [APK] 8931-8988 |
| `0xA6` | **calibração horizontal** | `[2]=0 && [3]=1` = sucesso; senão falha | [APK] 8989-9020 |
| `0xBB` | **nome/versão** | 17 chars ASCII remontados de `[2..15]` + `[17..19]`; inclui `KS-S20`/`KS-S22` (caso especial de handshake) | [APK] 10170-10308 |
| `0xB3` | **serial number** | 17 chars de `[2..15]`+`[17..19]` (rejeita `FFFF…`/`0000…`) | [APK] 10366-10496 |
| `0xB1` | **data/hora da roda** | `[2..7]` = YY MM DD hh mm ss → `20YY-MM-DD hh:mm:ss` | [APK] 10880-10916 |
| `0xBE` | **hora + info BMS12** | `[3..4]` u16 (ano?) + `[5..7]` h/m/s; `[2]=2` → pág. BMS1_12, `[2]=3` → pág. BMS1_13 | [APK] 10917-11060 |
| `0xC9` | **temperatura da bateria** | `[4..5]` u16 LE = °C (clamp -20..100); `[15] & 0x10` = flag | [APK] 8708-8753 |
| `0xF6` | **capacidade/limite** | `[4]` = capacidade (1..101 → kWh 0..100); `[2..3]` u16 LE ÷100 | [APK] 9021-9060 |
| `0x50` | **near-awakening ON/OFF** | `[2]` | [APK] 9469-9488 |
| `0x72` | **near-awakening distância** | `[2]` | [APK] 9489-9509 |
| `0x95` | **lista de voz atual** | `[2]=2`, `[3..n-4]` = bytes string | [APK] 8764-8801 |
| `0xA4` | **resultado de senha/lock** | `[2]==1 && [3]==0` → msg 4; senão msg 5 | [APK] 8802-8831 |
| `0xE7` | serial alternativo (variante de 18 bytes) | | [APK] 10497+ |
| `0xAC`/`0xAD`/`0xAE` | telemetria "et_kphard" (log apenas) | `[4..5]` u16 LE | [APK] 10309-10365 |
| `0xF9` | **download de log de viagem** | `[17]`: `0x70/0x71/0x72/0x73` = páginas de um registro; `0x7B` = registro de 59 B (ver 5.3); `0xA3` = contador/índice; `0x11/0x12` = progresso/falha de update de BMS | [APK] `defpackage.oc.t()` (oc.java:382-722) |
| `0xE9`, `0xD9` | keep-alive/ignorado | — | [APK] 8563-8569 |

**[EUCW]** Nomes oficiais dos códigos (mesma faixa):
`27`=display content · `63`=senha · `70`=flag "protegido por senha"/auth · `74`=spectra light ·
`76`=lift sensor · `77`=spectra mode · `82`=magic light mode · `84`=brilho do farol ·
`85`=farol piscando · `88`=bluetooth audio · `92`=cores de LED · `95`=lock · `107`=idioma ·
`110`=magic light · `0x8A`=limite de carga / tilt lateral / modo do lift / modo do display ·
`0xA0`=pedal tilt · `0xA2`=riding mode · `0xA4`=alarmes · `0xA9`=dados principais ·
`0xB3`=serial · `0xB5`=alarmes (leitura) · `0xBB`=nome+versão · `0xE1`=serial BMS1 ·
`0xE2`=serial BMS2 · `0xE5`=nome BMS1 · `0xE6`=nome BMS2.

### 5.2 Detalhes do quadro `0xA9` (principal)

| Offset | Tipo | Escala | Campo |
|---|---|---|---|
| 2-3 | u16 LE | ÷100 | tensão da bateria (V) |
| 4-5 | u16 LE | ÷100 | velocidade (km/h; o app converte para mph se configurado) |
| 6-9 | u32 word-swapped¹ | ÷1000 | odômetro total (km) |
| 10-11 | u16 LE | ÷100 | corrente (A) — valores em (-0,12; 0,05) são zerados |
| 12-13 | i16 LE | ÷100 | temperatura do motor/bateria (°C) [INF: confirmar sinal] |
| 14-15 | byte | — | se `[15]==0xE0`: `[14]` = modo (0 play/1 ride/2 study); senão `-1` |
| 22+2i | u16 LE | — | tensões de célula (o app publica em `lc` slot 20003 e calcula % por média) |

¹ "word-swapped" = `d[a] | d[a+1]<<8 | d[b]<<16 | d[b+1]<<24`, com `b=a+2` (função `fx.D` do EUC World
/ `d2()` do app oficial — `MainFragmentAty.java:2264-2266`). Ou seja: `[6]` e `[7]` formam a palavra
ALTA e `[8]`/`[9]` a palavra BAIXA.

### 5.3 Quadro de log de viagem (`0xF9`/`0x7B`, 60 bytes)

**[APK]** `oc.java:588-680`: `[24..27]` = fim do evento (u32 epoch, LSB primeiro), `[28]` = evento,
`[29..32]` = início, `[33]` = tensão ligado, `[34]` = tensão desligado, `[35]` = temp ligado,
`[36]` = temp máxima, `[37]` = reservado, `[38..39]` u16 = potência máxima, `[40]` = velocidade
máxima, `[41]` = velocidade média, `[42]` = corrente máxima, `[43..46]` = odômetro (word-swapped),
`[47..48]` u16 ÷10 = odômetro da viagem, `[49..50]` u16 = Wh, `[52]`/`[53]` = tensões mín/máx,
`[54]`/`[55]` = temp máx do motor/bateria, `[56..57]`/`[58..59]` = Wh Z/F.

### 5.4 Família `F1 EF` (modelos novos)

**[APK]** `MainFragmentAty_fallback.java:5289-5630`. Diferenças: header `F1 EF`, dispatch em
`data[16]`, **sem CRC aparente** e sem `5A 5A` (não verificado no parser — [LAC]).

| `data[16]` | Conteúdo | Campos |
|---|---|---|
| `0xC2` | telemetria | `[2..3]` u16 LE ÷100 = tensão · `[4..5]` u16 LE ÷100 = velocidade · `[6..9]` u32 word-swapped ÷1000 = total km · `[10..11]` u16 ÷60 = minutos ligada · `[8]` (byte) = bateria/estado · `[9]` = velocidade máx. (`setNowMaxSpeedNum`) · `[12]` = auto-off · `[13..15]` não usados |
| `0xC5` | viagem/parcial | `[4]`,`[5]`,`[2]`,`[3]` word-swapped ÷1000 → distância; `[6..7]` u16 ÷60 = minutos (base da média) |
| `0xC9` | "gear"/estado rápido | `[2]` = modo atual (4→0) · `[3]` = btlock · `[4]` = bluemode · `[5]` = "sliding start" · `[6]` = unidade (1 = mph) · `[7..15]` = valores brutos |

**[INF]** Essa família parece ser usada pelos modelos S20/S22/A-series; o app trata
`KS-S20`/`KS-S22` de forma especial na resposta `0xBB` (recalcula checksum e reenvia o quadro).

---

## 6. BMS

**[APK]** Não há gerenciamento de BMS separado: a roda envia páginas continuamente e o app as
publica em `PostBmsInfoUtils.postBmsInfo("<BMS1|BMS2>_<n>", quadro)`
(`MainFragmentAty_fallback.java`, várias linhas). Páginas identificadas:
`BMS1_1..BMS1_13` e `BMS2_1..BMS2_13` (as 12/13 aparecem nos modelos maiores).

### 6.1 Seleção de página

| `data[16]` | `data[17]` | Página |
|---|---|---|
| `0xF1` | `0x00..0x07` | BMS1_1 .. BMS1_8 |
| `0xF1` | `0xD0` / `0xD1` | BMS1_9 / BMS1_10 (usados p/ BMS1_12/13 em modelos novos — **[LAC]** confirmar o mapeamento exato) |
| `0xF2` | `0x00..0x07` | BMS2_1 .. BMS2_8 |
| `0xF2` | `0xD0` / `0xD1` | BMS2_9 / BMS2_10 (idem) |

### 6.2 Página 1 (resumo do pack) — `BMS1_1` (`MainFragmentAty_fallback.java:6600-6720`)

Publicada em `lc` slot `20000` (8 doubles, índices 0..7):

| Índice | Bytes | Tipo |
|---|---|---|
| 0 | `[2..3]` | u16 LE |
| 1 | `[4..5]` | u16 LE |
| 2 | `[6..7]` ×100 ÷ `[8..9]` | cálculo (percentual) |
| 3 | `[8..9]` ×100 ÷ `[12..13]` | cálculo |
| 4 | `[6..7]` | u16 LE |
| 5 | `[12..13]` | u16 LE |
| 6 | `[10..11]` | u16 LE |
| 7 | `[8..9]` | u16 LE |

**[INF]** Pelos campos que o app calcula a partir desses slots (slot `20017` = última célula,
média ×100 → `setBatteryR/L`), os campos devem corresponder a: tensão total, corrente/SOC,
capacidade, célula máx/mín, temperaturas. **[LAC]** identificar/validar cada um com captura
(vide planilha do EUC World `p296qh/C5198b.java`).

### 6.3 Páginas 2..8 (células) — `BMS1_2..8` / `BMS2_2..8`

**[APK]** `MainFragmentAty_fallback.java:6453-6610`: 7 células por página, cada uma **u16 LE**
(pares `[2..3]`, `[4..5]`, `[6..7]`, `[8..9]`, `[10..11]`, `[12..13]`, `[14..15]`), publicadas no
slot `20002` (BMS1) / `20006` (BMS2 [INF]). O app calcula a média das células > 0 ×100 →
`setBatteryL` (pack 1) / `setBatteryR` (pack 2).

### 6.4 Páginas 12/13 (extendidas, `0xBE` + `0xF1`)

**[APK]** `10880-11060`: `[2]==2` → BMS1_12 (`[8..9]` u16 ÷10, `[10..11]` …), `[2]==3` →
BMS1_13. Slots `300010`, `300013`; valores `lc.g/i/j/X` (tensão/temperatura/célula).

### 6.5 Atualização/log de BMS

**[APK]** `BmsService.java:401-520` usa a família `U1/A1/B1/C1/D1/f1/h1/J1/K1/L1/p1/u1/v1/w1`
(comandos com `[16]=0x74` e `[17]` variando — ver §10) para log e update dos packs.
Status de update chega no quadro `0xF9` `[17]`: `0x11`/`0x12` = falha, `0x02` = progresso
(`[2]` = %, `[4]` = %) — `oc.java:392-431`.

---

## 7. Odômetros e contadores

| Campo | Fonte | Escala | Observações |
|---|---|---|---|
| Odômetro total | `0xA9` `[6..9]` (word-swapped u32) | ÷1000 km | ex.: `00 00 00 00` = 0 km |
| Viagem/"trip" | `0xB9` `[2..5]` (word-swapped u32) | ÷1000 km | zera por evento |
| Tempo ligado | `0xB9` `[6..7]` u16 | segundos | app divide por 60 p/ minutos |
| Velocidade média | `0xB9` `[8..9]` u16 | ÷100 km/h | |
| Odômetro (comando `0x44`/`0xC5`) | `0xC5` word-swapped ÷1000 | km | usado no "trip2" |
| Consumo | `0xB9`/log `[49..50]` | Wh | |
| Autonomia estimada | calculada no app | — | `lc` + `MachineConfigBean` |

**[APK]** O app guarda também `setTotalMile(22)` (constante de referência usada em comparações) e
`limitMileage` do modelo — não é um campo do protocolo.

---

## 8. Configuração (escrita) — tabela completa

Cada comando é um quadro de 20 bytes no formato A (salvo indicação). Colunas: função, método do
app oficial (`fx.*`), bytes `[2..15]`, comando `[16]`, resposta, e nome no EUC World.

### 8.1 Leitura de parâmetros

| Função | `[16]` | `[2]` | Resposta | EUCW nome | Fonte |
|---|---|---|---|---|---|
| Auto-off | `0x3F` | — | `0x3F` `[4..5]` u16 LE (s) | READ_AUTO_OFF_TIME | EUCW:3592 |
| Bluetooth audio | `0x57` | — | `0x58` `[2]` 0/1 | READ_BLUETOOTH_AUDIO | EUCW:1502 |
| Display mode (km/h vs mph) | `0x8A` | `10` | `0x8A` `[4]` | READ_DISPLAY_MODE | EUCW:3564 |
| Display content (vel/tensão/corrente) | `0x1B` | `2` | `0x1B` `[4]` | READ_DISPLAY_CONTENT | EUCW:1513 |
| Brilho do farol | `0x54` (`[15]=0xF2`) | — | `0x54` `[4]` (%) | READ_HEADLIGHT_BRIGHTNESS | EUCW:1524 |
| Farol piscando | `0x54` | — | `0x55` `[2]` | READ_HEADLIGHT_FLASHING | EUCW:1534 |
| Modo do farol | `0x73` | — | `(0x73)` | WRITE_HEADLIGHT_MODE | EUCW:1749 |
| Idioma | `0x6A` | — | `0x6B` `[2]` | READ_LANGUAGE | EUCW:3609 |
| Limite de carga | `0x8A` | `8` | `0x8A` `[4]` | READ_CHARGING_LIMIT | EUCW:3619 |
| Tilt lateral | `0x8A` | `2` | `0x8A` `[4..5]` ×0,1° | READ_LATERAL_TILT_LIMIT | EUCW:3655 |
| Lift sensor (liga/desliga) | `0x81` | — | `0x4C` `[2]` 0/1 | READ_LIFT_SENSOR | EUCW:3633 |
| Lift sensor modo | `0x8A` | `4` | `0x4D` `[2]` | READ_LIFT_SENSOR_MODE | EUCW:3585 |
| Magic light | `0x6D` | — | `0x6E` `[2]` | READ_MAGIC_LIGHT | EUCW:3640 |
| Magic light modo | `0x51` | — | `0x52` `[2]` 0=auto,1=rotating,2=flowing,3=breathing | READ_MAGIC_LIGHT_MODE | EUCW:3647 |
| Spectra light | `0x80` | — | `0x4A` `[2]` 0/1 | READ_SPECTRA_LIGHT | EUCW:3778 |
| Spectra light modo | `0x96` | — | `0x4D` `[2]` | READ_SPECTRA_LIGHT_MODE | EUCW:3577 |
| Cores de LED | `0x5B` | — | `0x5C` 4 × RGB3 | READ_LED_COLORS | EUCW:3678 |
| Lock | `0x5E` | — | `0x5F` `[2]` 0/1 | READ_LOCK | EUCW:3685 |
| Pedal tilt | `0x8A` | `0` | `0xA0` | READ_PEDAL_TILT | EUCW:3599 |
| Alarmes + tiltback | `0x98` | — | `0xB5`: 4 × u16 LE | READ_SPEED_ALARMS_AND_TILTBACK | EUCW:3692 |
| Serial da roda | `0x63` (`fx.f2()`) | — | `0xB3` (17 chars) | READ_SERIAL_NUMBER | EUCW:1544; APK `fx.java:1803-1812` |
| Nome da roda | `0x9B` (`fx.c0()`) | — | `0xBB` (17 chars) | READ_NAME | EUCW:1556; APK `fx.java:1622-1631` |
| Serial BMS1 | `0xE1` | — | `0xB3`? (0xE1) | READ_BATTERY1_SERIAL_NUMBER | EUCW:3756 |
| Serial BMS2 | `0xE2` | — | `0xE2` | READ_BATTERY2_SERIAL_NUMBER | EUCW:3770 |
| Nome BMS1 | `0xE5` | — | `0xE5` (ASCII) | READ_BATTERY1_NAME | EUCW:3728 |
| Nome BMS2 | `0xE6` | — | `0xE6` | READ_BATTERY2_NAME | APK:1492 |
| Senha (alarmes) | `0x99` | — | `0x9D` (string) | READ_…PASSWORD | EUCW:3737 |

### 8.2 Escrita de parâmetros

| Função | `[16]` | Payload | Valores | Resposta | Fonte |
|---|---|---|---|---|---|
| Auto-off | `0x3F` | `[2]=1`, `[4..5]` u16 LE s | 0..65535 s | `0x3F` | EUCW:1652; `fx.Y0` (APK `fx.java:1347-1359`) |
| Bluetooth audio | `0x56` | `[2]` = 0/1 | | `0x58` | EUCW:1665 |
| Limite de carga (%) | `0x8A` | `[2]=9`, `[4]` = % | (0..100) | `0x8A` | EUCW:1676 |
| Conteúdo do display | `0x1B` | `[2]=3`, `[4]` | 0 veloc. / 1 tensão / 2 corrente | `0x1B` | EUCW:1688 |
| Unidade do display | `0x8A` | `[2]=11`, `[4]` | 0 métrico / 1 imperial | `0x8A` | EUCW:1700 |
| Brilho do farol (%) | `0x54` | `[4]` = %, `[14]=1`, `[15]=0xF2` | 0..100 | `0x54` | EUCW:1712; `fx.X2` (APK `fx.java:1321-1333`, `LightSettingActivity.java:76`) |
| Farol piscando | `0x53` | `[2]` = 0/1 | | `0x55` | EUCW:1725; `fx.E0` (APK `fx.java:227-237`) |
| Modo do farol / voice messages (mesmo comando) | `0x73` | `[2]` = 18/19/20, `[3]` = 0/1 | ver "[LAC] qual [2] = qual modo" | `0x73` | EUCW:1736, 3534-3549; `fx.H0` (APK `fx.java:393-405`) |
| Alarmes 1-3 + tiltback | `0x85` | `[2]`=alarme1, `[4]`=alarme2, `[6]`=alarme3, `[8]`=tiltback (bytes, km/h) + senha opcional `[10..15]` (6 dígitos ASCII) | leitura responde `0xB5` com 4 × u16 LE | `0xA4` | EUCW:3512-3530; `fx.g` (APK `fx.java:1814-1837`) |
| Pedal tilt (offset dos pedais) | `0x8A` | `[2]=1`, `[4..5]` = °×10 (u16 LE, pode ser negativo) | ±(0,1…20°) | `0x8A` | EUCW:3449-3461 (`m11019k0`) |
| Spectra light ON/OFF | `0x7D` | `[2]` = 0/1 | | `0x4A` | EUCW:3466-3475 (`m11021m0`) |
| Spectra light modo | `0x97` | `[2]` = 0 auto / 1 beating / 2 flashing / 3 breathing | | `0x4D` | EUCW:3497-3509 (`m11022n0`) |
| Magic light ON/OFF | `0x6C` | `[2]` = 1 desligar / 0 ligar (invertido) | | `0x6E` | EUCW:3407-3416 (`m11016h0`) |
| Magic light modo | `0x50` | `[2]` = 0 auto / 1 rotating / 2 flowing / 3 breathing | | `0x52` | EUCW:3420-3431 (`m11017i0`) |
| Riding mode (play/ride/study) | `0x87` | `[2]` = 0 ride / 1 play / 2 study, `[3] = 0xE0` | | `0xA2` | EUCW:3481-3492 (`m11020l0`); `fx.O0` (APK `fx.java:849-860`, `GearAdjustmentActivity.java:278`) |
| Tilt lateral | `0x8A` | `[2]=3`, `[4..5]` u16 LE = °×10 | ex. 5,0° → `32 00` | `0x8A` | EUCW:1755 |
| Lift sensor | `0x7E` | `[2]` = 0/1 | | `0x4C` | EUCW:1781 |
| Lift sensor modo | `0x8A` | `[2]=5`, `[4]` = 0/1/2 | 0 motor off, 1 parcial, 2 ligado | `0x4D` | EUCW:1801 |
| **Lock** | `0x5D` | `[2]=1` travar (sem senha) / `[2]=0` destravar + 6 dígitos em `[10..15]` (3 aleatórios + 3 calculados) | ver §8.3 | `0x5F` | EUCW:1920 |
| Cores de LED | `0x59` (`fx.C0`) | RGB das zonas primária/secundária/terciária/quaternária | 4 × RGB | `0x5C` | APK `fx.java:145-147` |
| Cor RGB (1 zona, `0x59` fixo) | `fx.Z1(r,g,b,…)` | payload começa com `52 47 42 01` ("RGB" + 1) | | | APK `fx.java:1446-1450` |
| Conteúdo do display (variação do app) | `fx.Y2(m)` | `[2]=3`, `[4]=m`, `[16]=0x1B` | m = 0 veloc. / 1 tensão / 2 corrente | `0x1B` | APK `fx.java:1380-1391`, `RideModeActivity.java:182` |
| Modo do display (variação do app) | — | `[2]=11`, `[4..5]` = u16, `[6..7]` = complemento `~`, `[16]=0x8A` | | `0x8A` | APK `RideModeActivity.java:204-218` |
| Grupo de leitura `0x8B` | `fx.S1(10)` / `t0()` | `[2]=10` ou `ms2.f`, `[16]=0x8B` | | `0x8B` | APK `fx.java:1050-1060`, `GearAdjustmentActivity.java:130` |
| Calibração horizontal | **[LAC]** comando de escrita não localizado | resposta `0xA6`: `[2]=0 && [3]=1` = sucesso | | `0xA6` | APK `MainFragmentAty_fallback.java:8989-9020`; EUCW `WRITE_CALIBRATION` |

### 8.2.1 Subcomandos do grupo `0x8A` (`[17]=0x14`)

| `[2]` | Uso | Codificação |
|---|---|---|
| 1 | Pedal tilt (offset dos pedais) | `[4..5]` i16 LE = °×10 |
| 2 | Tilt lateral (limite) | `[4..5]` i16 LE = °×10 |
| 3 | Limite lateral/reserva (usado também por `fx.X1`/`fx.z1` com senha) | `[4..5]` = valor |
| 4 | Modo do lift sensor (leitura) | resposta `0x4D` |
| 5 | Modo do lift sensor (escrita) | `[4]` = 0 motor off / 1 parcial / 2 ligado |
| 8 | Limite de carga (leitura) | resposta `0x8A` `[4]` |
| 9 | Limite de carga (escrita) | `[4]` = % |
| 10 | Modo do display (leitura) | resposta `0x8A` `[4]` |
| 11 | Modo do display (escrita) | `[4]` = 0 métrico / 1 imperial |
| 14 / 16 | Refresh interno (`fx.k1`/`fx.M1`) | `fx.k1()` = `[2]=14`, `fx.M1()` = `[2]=16` |
| 15 | "嗡鸣器" (buzzer/volume do beeper) | `[4..5]` u16 LE ([INF] valor 0..100) |
| 17 | "助力大小" (nível de assistência) | `[4..5]` u16 LE ([INF]) |

**[APK]** Fonte: `DeviceSeekbarActivity.java:175-230, 400-470, 730-745`; `fx.k1/g1/M1/U2`.

### 8.2.2 Subcomandos do grupo `0x87`

| Payload | Uso |
|---|---|
| `[2]=modo`, `[3]=0xE0` | Riding mode (0 ride / 1 play / 2 study) — `fx.O0`/EUCW `m11020l0` |
| `[2]=i`, `[3]=(i2>0?0:1)` | `fx.w0` (log "liftUpOnOff") — provavelmente ON/OFF do lift com parâmetro; **[LAC]** |
### 8.2.3 Senha, identidade, hora e outros

| Função | `[16]` | Payload | Resposta | Fonte |
|---|---|---|---|---|
| Senha: definir | `0x41` (`fx.a1`) | `[2..5]` = senha (4 ASCII), `[6..9]` = confirmação | `0x41` (eco) / `0x46` (flag "protegido") | APK `fx.java:1482-1504`; EUCW `m11018j0` (C5909c0:3443) |
| Senha: definir/alterar (família `0xA5`, sub 3) | `0xA5` (`fx.Y1`) | `[2]=3`, `[3..8]` = senha | `0xA5` | APK `fx.java:1361-1378` |
| Senha: alterar (com antiga) | `0xA5` (`fx.F`) | `[2]=3`, `[3..8]` = antiga, `[9..14]` = nova | `0xA5` | APK `fx.java:262-283` |
| Senha: limpar | `0xA5` (`fx.L`) | `[2]=4`, `[3..8]` = senha | `0xA5` | APK `fx.java:618-635` |
| Senha de 4 dígitos (modelo novo) | `0xFB` (`fx.K2`) | `[2..5]` = dígitos | — | APK `fx.java:591-616` |
| Nome da roda (escrita) | `fx.y(nome,…)` | ver §4.4 | — | APK `fx.java:2769-2789` |
| Serial da roda (17 chars) | `0x44` (`fx.L0`, com hífens) / `0x42` (`fx.Z0`) | `[2..15]`+`[17..19]` | — | APK `fx.java:637-673, 1408-1444` |
| MAC/ID (17 chars) | `0x60`/`0x61`/`0x62` (`fx.g2/h2/i2`) | `[2..15]`+`[17..19]` | — | APK `fx.java:1862-1950` |
| Hora da roda | `fx.I1(1,…)` / `fx.z1(1,…)` | `[0]=1`, `[14]=0xF9`, `[15]=0x01` | — | APK `fx.java:464-486, 2898-2924` |
| Alarmes/tiltback (forma alternativa do app) | `0x85` | 4 bytes + senha (ver §8.2) | `0xA4` | APK `fx.g` |
| Lista de voz / operações de voz | `0x95` (`fx.Q0/R0` e frame custom com `[4]/[5]=0xFF`) | `[2]=0` | `0x95` | APK `VoiceSettingActivity.java:200-229` |
| Near-awakening ON/OFF (ordem) | `0x48` (`fx.S0`/`fx.T0`) | `[2]=i`, `[3]=i2` | `0x50` | APK `fx.java:1032-1086` |
| Near-awakening distância | `0x71` (`fx.j`) | `[2]=i`, `[3]=i2` (i==1) | `0x72` | APK `fx.java:2010-2026` |
| Near-awakening tempo OFF | `0xA5` (`fx.l`, `[2]=0/1`) | `[4]=i2` (i==1) | — | APK `fx.java:2108-2124` |
| Near-awakening distância (user) | `0xA5` (`fx.k`, `[2]=2`) | — | — | APK `fx.java:2058-2071` |
| Near-awakening senha (6 dígitos) | `0x9A` (`fx.o2`) | `[2..7]` = dígitos | `0xA4`? | APK `fx.java:2290-2305` |
| Auto-off (leitura/escrita) | `0x3F` (`fx.X0`/`fx.Y0`) | `[2]=1`, `[4..5]` u16 LE s | `0x3F` | APK `fx.java:1286-1296, 1347-1359` (constante `g43.a=0x3F`) |
| Desligar roda | `fx.S1`/`fx.T1` (`[16]=0x8B`)/`fx.e()` (`[16]=0x8A`,`[2]=12`) | `[2]`/`[4..5]` | — | APK `fx.java:1050-1060, 1088-1107, 1730-1740` |
| Restaurar padrão | `fx.l1()` (`[16]=0x6A`) | só rodapé `5A 5A` | — | APK `fx.java:2136-2144` |
| Reset de viagem | `fx.d1()` (`[16]=0xA5`,`[2]=5`) | — | — | APK `MainFragmentAty.java:2493` |
| Lock (travar/destravar) | `0x5D` (`fx.I0`) | `[2]` = 1/0 + 6 dígitos (`fx.c`) | `0x5F` | APK `fx.java:451-462` |
| Leitura de lock | `0x5E` (`fx.K0`, `fx.o1`) | — | `0x5F` | APK `fx.java:569-578, 2280-2288` |
| Leitura de log/NFC | `fx.K/d0/e0/l0` (formato C) | "NFC"/"RGB"/… | — | APK `fx.java:558-616, 1690-1767` |
| Comandos de firmware | ver §10 | | | |

**[APK]** Métodos `fx.*` restantes com comando identificado mas finalidade não confirmada:
`0x45` (`fx.M0`), `0x47` (`fx.J0`), `0x50` (`fx.G0`), `0x51` (`fx.F0`), `0x53` (`fx.E0`),
`0x54` (`fx.D0`, `fx.x1`, `fx.m1`), `0x5B` (`fx.B0`), `0x6C` (`fx.A0`), `0x7C` (`fx.U0`),
`0x7D` (`fx.m2`), `0x7E` (`fx.y0`, `fx.x0`), `0x7F` (`fx.v0`), `0x80` (`fx.F1`, `fx.l2`),
`0x82/0x83/0x84` (`fx.P0`), `0x8A` sub `0/6/12/…`, `0x8B` (`fx.T1`), `0x90` (`fx.E1`),
`0x96` (`fx.e1`), `0xA6` (`fx.t1` → na verdade `[16]=0x1B` com `[2]` variável), `0xF6`
(`fx.Q1`/`fx.R1`), `0xF8` (`fx.Y`). Ver `analysis/kingsong/fx_tabela.txt` para a lista completa
(método → bytes) e `rx_codes_brutos.txt` para os códigos de resposta.

### 8.3 Lock/senha — algoritmo dos 6 dígitos **[APK]** (`fx.java:1605-1620`, `fx.d`/`fx.c`)

- 3 dígitos aleatórios em `[11]`, `[13]`, `[15]`;
- `[10] = (d11+d13+d15 + 10) % 10`;
- `[12] = (d11+d13+d15 + d10 + 9) % 10`;
- `[14] = (d11+d13+d15 + d10 + d12 + 5) % 10`;
- variante `d(...)` (lock por senha configurada pelo usuário) soma os dígitos da senha salva.
- Para **destravar** (`WRITE_LOCK` com `[2]=0`) o EUC World usa o mesmo esquema mas com offsets
  deslocados (`C5909c0.java:1926-1941`).

### 8.4 Envio e confirmação

- **[APK]** `md.b(frame, toast)` = envio imediato; `md.e(frame, delay, toast)` = envio com atraso;
  `md.f(frame, delay, count, toast)` = repetição (`defpackage.md.java:55-105`).
- **[APK]** Sem ACK a tela mostra "falha" após 3 tentativas; o app não valida CRC nas respostas
  (só o parser usa o byte de comando).
- **[EUCW]** Confirmação de escrita = receber o **mesmo código** com `data[2]` = valor ecoado.

---

## 9. Modelos e especificidades

| Aspecto | Como o app trata | Fonte |
|---|---|---|
| Lista de modelos/prefixos BLE | vem do servidor (`AllVehiclesTwoBean`, `MachineConfigBean`) | [APK] |
| Fallback offline | nomes `KS*`, `lamax*`, `gt*` | [APK] `MainCarFrgm.java:672` |
| Modelo E1 (scooter) | `ScanRecord` contém `02 66 66` → tratado como E1 (`Q4`) | [APK] `MainCarFrgm.java:610` |
| Modelos S20/S22 | flag `X2`: resposta `0xBB` refaz checksum e reenvia o quadro; versão < 0x75 muda o `[16]` devolvido para `0x63` | [APK] `MainFragmentAty_fallback.java:10249-10308` |
| Bateria (nº de packs, S/P, tensões) | `MachineConfigBean.battery_string/battery_pack/low_voltage/full_voltage` | [APK] `MainFragmentAty.java:2436-2484` |
| Corrente/potência/temperatura | `is_voltage`/`is_warp`/`temperature` do servidor | [APK] |
| BMS presente | `MachineConfigBean.bms` (`"1"`/`"2"`) → bind do `BmsService` | [APK] `MainFragmentAty.java:2436-2457` |
| Tipos S16/S19/S2/F18/F22/F22P | tabela de bateria (kWh, tensão de célula, Ah) | **[EUCW]** `C5909c0.java:3696-3723` |
| GT16 / Rockwheel (RW) / LAMAX | mesmo protocolo (nome BLE distinto) | [APK]+[EUCW] |

**[EUCW]** Exemplos de parâmetros por modelo (S16: 0,74 kWh, 20S; S19: 0,888 kWh, 24S;
S2: 1,08 kWh, 30S; F18: 1,332 kWh, 36S; F22: 1,369 kWh, 37S; F22P: 1,554 kWh, 42S).

Da linha de nomes observada: KS-14D/M/S, KS-16S/X, KS-18L/XL, KS-19, KS-S16/S18/S19/S20/S22,
KS-F18/F22/F22P, KS-N1/N10-B, KS-E1, GT16, LAMAX. **[LAC]** lista completa apenas no servidor.

---

## 10. Firmware / BMS update (essencial)

| Etapa | Comando (`[16],[17]`) | Payload | Fonte |
|---|---|---|---|
| Entrar no bootloader | `1D 10` (`fx.D2`) / `1D 20` (`fx.E2`) | `[2..3]` CRC-16 do bloco; `[12..13]` índice; 12 bytes no `[4..15]` | APK `fx.java:198-208, 250-260` |
| Bloco de dados (48 B/16 B) | `FB 51` (`fx.A2`), `70 41` (`fx.z2`), `1D 31` (`fx.N2`), `1D 21` (`fx.G2`) | CRC do bloco em `[2..3]`, contador em `[12..13]`, dados em `[4..15]` e **anexados após o quadro** (payload extra) | APK `fx.java:43-95, 2926-2968, 797-839, 360-379` |
| Iniciar sessão | `1D 10/20/30/40` + `[12..13]=FF FF` (`fx.t2/s2/v2/w2`) | | APK `fx.java:1109-1119, 2489-2505, 2650-2666, 2705-2721` |
| Finalizar | `1D 41`? (`fx.M2`), `FB 50` (`fx.L2`), `7A 10` (`fx.H2`) | `[12..13]=FF FF` | APK |
| BMS (família `[16]=0x74`) | `[17]` = `0xE3..0xE5` (A1/B1/f1), `0xB6` (C1), `0xA0` (D1), `0xB3/0xD5/0xD7/0xDD/0xDF/0xE1/0xB7` (J1/K1/L1/p1/u1/v1/w1), sempre com CRC-16 | `[2..5]` = serial de 14 dígitos em `fx.I1/r0`, CRC de update nos primeiros 2 bytes | APK `BmsService.java:401-520`, `fx.java` |
| Chunks | 12/16 B por quadro, CRC do bloco e CRC do cabeçalho | | `fx.z2/A2/N2` |
| Indicador de progresso | resposta `0xF9` `[17]=0x02` com `[2]`/`[4]` = % | | APK `oc.java:392-431` |
| Fonte da versão | resposta `0xBB` (nome/versão, string de 17 chars) e `MachineConfigBean.firmwareFeedback` (tipo 1 = placa-mãe, 2 = BMS, 3 = "meter") | | APK `MainFragmentAty.java:2388-2434` |

**[APK]** `FirmwareUpdateActivity(2)` orquestra as etapas (`fx.z2/y2/A2/I2/G2/N2/J2/H2/L2/M2/
t2/s2/v2/w2/u2/x2`, ~25 chamadas distintas). **[LAC]** sequência exata/tempos não extraídos.

---

## 11. Limitações e lacunas

1. **Sem CRC nas respostas**: o app não valida CRC/Tail ao receber; recomenda-se validar CRC do
   quadro `AA 55` por segurança própria.
2. **Família `F1 EF`**: apenas 3 códigos mapeados (`0xC2/0xC5/0xC9`); **[LAC]** confirmar header
   exato/CRC com captura em uma KS-S20/S22.
3. **Detecção de geração**: o app não tem tabela "v1/v2/v3" como o EUC World; ele começa por
   `AD00/AD01/AD02` e cai para `FFE0/FFE1`. **[LAC]** qual caminho cada geração usa de fato.
4. **Páginas BMS 9..13**: mapeamento de `[17]=0xD0/0xD1` para páginas 9/10 não confirmado.
5. **Semântica exata dos slots BMS1_1** (índices 0..7) precisa captura.
6. **Unidade dos alarmes de velocidade/tiltback** (km/h ×10? valores brutos?) não determinada.
7. **Init via servidor** (`InitCommandBean`): comandos hex remotos podem ser necessários para
   alguns firmwares.
8. **Tempos**: EUC World usa 500/3000 ms por leitura no init; o app oficial usa 50..300 ms entre
   comandos e 1000 ms de watchdog — validar com a roda real.
9. **Potência** não existe como campo direto: é calculada (tensão × corrente) no app e no EUC World.
10. **% de bateria**: calculada por média de células / tensão de pacote; não há campo dedicado.
11. **Tilt lateral (`0x8A`)**: a leitura usa `[2]=2` (§8.1, `READ_LATERAL_TILT_LIMIT`, EUCW:3655)
    enquanto a escrita usa `[2]=3` (exemplo §4.5 `AA 55 03 00 32 00 … 8A 14 5A 5A`, `[4..5]=50` =
    5,0°; EUCW:1755); o subcomando `2` também é descrito como "tilt lateral (limite)" e o `3` como
    "limite lateral/reserva" (§8.2.1) — **[LAC]** confirmar se 2/3 são o mesmo parâmetro em
    leitura/escrita ou parâmetros distintos (captura).
12. **Formato dos frames de BMS**: as leituras de página do app saem no formato clássico com rodapé
    `5A 5A` (exemplo §4.5, `F1 01 5A 5A`), enquanto as famílias de log/update de BMS
    (`[16]=0x74`, `BmsService.java`) aparecem "sempre com CRC-16" (formato B, §4.2/§10) —
    **[LAC]** confirmar qual formato as respostas da roda usam em cada caso (captura em S20/S22).
13. **Unidade das tensões de célula**: `0xA9[22+2i]` (§5.2) e as páginas BMS 2..8 (§6.3) são `u16 LE`
    **sem escala/unidade declarada** (mV? V? outro?) — **[LAC]** confirmar com captura.

---

## 12. Evidências e referências

- **App oficial**: `binario/kingsong.apk` (sha256 `08dc8cab6df91eadc72b591ae41239b0fb4c1d9fe5aa34895a0afae4d87be842`).
  Fontes extraídas em `analysis/kingsong/jadx/` e evidências brutas em
  `analysis/kingsong/evidencias.md`, `fx_tabela.txt`, `rx_codes_brutos.txt`, `dex/`, `apktool/`.
- **[EUCW]** EUC World 2.66.0 descompilado: `analysis/decompiled/sources/p364uh/C5909c0.java`
  (adaptador KingSong: enum de ~90 ops, writes, leituras, parser de respostas) e
  `p364uh/AbstractC5919h0.java:528-551` (identificação do adaptador).
- **[COM]** WheelLog (`KingSongAdapter.java`, GPL) e eucplanet usam o mesmo framing `AA 55 … 5A 5A`;
  usados apenas como conferência de fatos (bytes/escala), nunca como código.
- Ferramentas: REA MCP (`ev_5d7cc2f33298ae9bc1a5d2588eb876ffa98bf0901d2d87636151bc14a904d349`),
  JADX 1.5.6 (single-class e `-m fallback`), apktool 2.11.1, `strings`.
