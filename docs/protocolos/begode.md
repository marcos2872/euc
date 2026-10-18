# Protocolo Begode/Gotway (app oficial) — Referência técnica

Extração estática do **app oficial Begode** `com.zzcyi.monotroch` **1.0.52**
(`binario/begode.apk`, sha256 `5de6d7acdc4b8d3df79d34dc23a732fdc704a3848e62bef0d38545f3fc3b9f52`,
base do XAPK `binario/begode.xapk`), cruzada com o **EUC World 2.66.0** já descompilado em
`analysis/decompiled/sources/` (adaptador Gotway `p364uh/C5933v.java`) e com 210 frames reais
encontrados em código morto do próprio app (`analysis/begode/samples-dataPackets.txt`).

> **Proveniência / marcadores**
> - **[APK]** fato extraído do app oficial Begode (`analysis/begode/decompiled/<arquivo>:<linha>`).
> - **[APK-EW]** fato do EUC World 2.66.0 (`analysis/decompiled/sources/…`), implementação
>   independente do mesmo protocolo — usado só para corroboração (sem copiar código).
> - **[COM]** documento/projeto comunitário verificável (WheelLog/GPL, eucplanet) — apenas fatos.
> - **[INF]** inferência própria (ex.: exemplo hex calculado) — **não validado em roda**.
>
> ⚠️ Nada aqui foi validado em roda real ainda: o protocolo foi reconstruído do app + de uma
> captura embutida. Ver §11 (lacunas que exigem captura BLE / teste na roda).

---

## 1. Visão geral

O app oficial fala **uma única família** de protocolo com as rodas Gotway/Begode/Extreme Bull:

- **TX:** letras ASCII de 1 byte (ou sequências curtas) — `N`, `V`, `W B 5`, `W Y 6 0` …
- **RX:** frames binários fixos de **24 bytes**, header `55 AA`, trailer `5A 5A 5A 5A`,
  com o "tipo" do conteúdo em `[18]` e um discriminador em `[19]`.

| Item | Valor |
|---|---|
| Serviço BLE | `0000ffe0-0000-1000-8000-00805f9b34fb` |
| Característica write | `0000ffe1-…` (mesma usada para notify) |
| Característica notify | `0000ffe1-…` |
| Outra característica declarada | `0000fff6-…` ("AT") — **nunca usada** no código do app |
| Autenticação | **nenhuma** (sem senha, sem handshake, sem serial) |
| Checksum | **nenhum** em TX; RX é validado apenas por header/trailer |
| Serviço DFU separado | **não existe** (firmware usa as mesmas FFE0/FFE1) |

> **[INF]** Comparado ao que o plano multi-protocolo (§2/§3.4) descreve para o EUC World
> (`gotway_v1/v2/v3`, `FFF0/FFF1`, DFU `1d14d6ee…`), o app oficial usa **somente FFE0/FFE1**.
> FFF0/FFF1 não aparecem em nenhum lugar do APK oficial (busca por strings de UUID).

---

## 2. Transporte BLE

### 2.1 UUIDs e direção [APK]

`ble/BleSppGattAttributes.java:17-21`:

| Constante | UUID | Uso |
|---|---|---|
| `BLE_SPP_Service` | `0000ffe0-0000-1000-8000-00805f9b34fb` | serviço |
| `BLE_SPP_Write_Characteristic` | `0000ffe1-…` | escritas |
| `BLE_SPP_Notify_Characteristic` | `0000ffe1-…` | notificações (mesma characteristic) |
| `BLE_SPP_AT_Characteristic` | `0000fff6-…` | declarada, **não referenciada** em nenhum fluxo |

### 2.2 Escrita (chunking, tipos e atrasos) [APK]

Todas as telas usam o mesmo bloco (`HomeFragment.java:2060-2068`, idêntico em
`HomeSettingActivity.java:1927-1934`, `AmbientLightSettingActivity.java`, `ModeSetActivity.java`,
`SetActivity.java:217-225`, `MineFragment.java`, `FirmwareUpdateActivity.java:508-516`):

| Parâmetro | Valor | Observação |
|---|---|---|
| `setPackageSize` | `mtu - 3` | MTU default **23** → **20 bytes** por chunk |
| `setPackageWriteDelayMillis` | 5 ms | entre chunks do mesmo comando |
| `setRequestWriteDelayMillis` | 10 ms | entre requests da fila |
| `setWaitWriteResult` | `true` | escrita sequencial confirmada por callback |
| `setWriteType` | `1` (WRITE) se a characteristic tiver propriedade `4`; senão `2` (NO_RESPONSE) | |

Não há pedido explícito de MTU em nenhum ponto do app (`getMtu()` só é lido;
`ConnectionImpl.java:68` mantém 23 até um `requestMtu` que o app não faz). **[INF]** Logo,
na prática, **cada escrita é um único byte/ASCII curto** em blocos de 20 B.

### 2.3 Conexão [APK]

`HomeFragment.java:461-464`:

| Parâmetro | Valor |
|---|---|
| connect timeout | 4000 ms |
| request timeout | 1000 ms |
| tentativas de reconexão | 2 |
| auto-reconnect | `false` |
| transport / PHY | LE / 1M (`ConnectionConfiguration.java:23-24`) |
| delay de descoberta de serviços | 600 ms (`ConnectionConfiguration.java:17`) |

### 2.4 Descoberta (scan) [APK]

`HomeFragment.java:340-362` (`ScanListener.onScanResult`):

- descarta dispositivos sem nome;
- aceita apenas nomes que **contenham** `GotWay` **ou** `BEGODE` (case-sensitive, `String.contains`);
- evita duplicar por MAC;
- conecta automaticamente ao MAC salvo em `EasySP("address")`;
- permissões: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
  (Android 12+; antes: `BLUETOOTH_ADMIN` + localização) — `HomeFragment.java:637-645`.

### 2.5 Reassemblagem [APK]

Ver §4.3 — o app converte cada notificação em **string hex** e acumula até 48 caracteres (24 bytes).

---

## 3. Conexão e init

Sequência completa (sem auth):

| # | Passo | Detalhe | Evidência |
|---|---|---|---|
| 1 | Conectar GATT | `EasyBLE.connect(mac, config)`; sem request de MTU | `HomeFragment.java:685-687` |
| 2 | Esperar `SERVICE_DISCOVERED` | estado da conexão | `HomeFragment.java:731-736` |
| 3 | `setNotifi()` | varre os serviços; exige característica `FFE1` com propriedade **16** (NOTIFY); então habilita notificação | `HomeFragment.java:830-874` |
| 4 | `onNotificationChanged` (success) | salva MAC, zera o buffer, marca `lastTime` e envia **`N` (0x4E)** | `HomeFragment.java:875-891` |
| 5 | Roda responde ASCII `NAME:<nome>` | pode chegar em várias notificações; o app usa `String.contains("NAME:")` e pega `substring(5)` | `HomeFragment.java:905-910` |
| 6 | (Tela "Settings") envia **`V` (0x56)** | resposta = **9 bytes ASCII** de versão de firmware | `SetActivity.java:168`; `FirmwareUpdateActivity.java:238,501` |
| 7 | Streaming | o app **não faz poll**: telemetria e BMS chegam continuamente | §5/§6 |

Detalhes e pegadinhas:

- **[APK]** A notificação é habilitada apenas se a characteristic tiver a propriedade NOTIFY (16);
  se a roda não expuser isso, o app não envia `N` e nunca recebe dados.
- **[APK]** Após desconectar, o app limpa os buffers de BMS e reinicia o scan (auto-reconnect off)
  — `HomeFragment.java:731-760`.
- **[INF]** Não há comando de "manter vivo" (keepalive) nem timeout RX no app; a ausência de
  dados não gera erro na UI.
- **[APK]** A versão de firmware é usada para: (a) escolher o pacote de update (`V` → dígitos finais),
  (b) **[APK-EW]** identificar o modelo (`^(GW|JL|JN|CF)\d{7}` e o prefixo **CF** = variante
  "dynamic tiltback").

---

## 4. Framing

### 4.1 Formato do frame RX (24 bytes, fixo)

```
offset  0    1    2 ....................... 17   18    19   20 21 22 23
       0x55 0xAA  payload (16 bytes)             tipo  len  0x5A×4
```

| Offset | Tam | Campo | Observação |
|---|---|---|---|
| 0x00 | 1 | `0x55` | header |
| 0x01 | 1 | `0xAA` | header |
| 0x02 | 16 | payload | numéricos **big-endian** (u16/u32/nibbles) |
| 0x12 | 1 | **tipo** | `0x00` telemetria · `0x01` bateria/falhas (ou "board" de BMS quando `[19] ≤ 6`) · `0x02` calibração (ou pack 1 quando `[19] ≤ 6`) · `0x03`/`0x05`/`0x06` packs 2..4 · `0x04` settings+odômetro+modelo · `0x07` corrente/temp. motor/PWM (ver §6) |
| 0x13 | 1 | discriminador | `0x18` (24) nos frames "de serviço"; nos frames de BMS = **página** `0x00..0x06` |
| 0x14-0x17 | 4 | `5A 5A 5A 5A` | trailer |

**[APK]** Validação (todas as telas — `HomeFragment.java:937`, `BatteryPackActivity.java:840`,
`HomeSettingActivity.java:1484`, `ModeSetActivity.java`, `AmbientLightSettingActivity.java`):

```
b[0]==0x55 && b[1]==0xAA && b[20]==0x5A && b[21]==0x5A && b[22]==0x5A && b[23]==0x5A
```

**[APK]** **Não há checksum nem CRC** — o frame é aceito só por header/trailer/24 bytes.

### 4.2 Formato do frame TX

Não existe "frame": cada letra/valor é uma **escrita BLE independente de 1 byte**
(ASCII). Exemplo calculado **[INF]** para tiltback de 60 km/h:

```
TX1: 57            'W'
TX2: 59            'Y'
TX3: 36            '6'
TX4: 30            '0'
(com Thread.sleep(100) entre elas)
```

Leitura de nome/versão: `TX: 4E` ('N') / `TX: 56` ('V').

### 4.3 Reassemblagem e sincronização [APK]

- Cada notificação é convertida para texto hex sem separador (`HexUtil.formatHexString`,
  `ble/util/HexUtil.java:52-72`) e **concatenada** num `StringBuilder`.
- Só se tenta interpretar quando o buffer tem **≥ 48 caracteres** (= 24 bytes).
- Depois de extrair um frame válido, apaga exatamente 48 caracteres.
- Se não encontrar frame, o buffer **não é descartado** (só cresce);
  há uma guarda morta `if (mStringBuilder.length() > 1000)`.
- **[INF]** Recomendação de implementação: manter um buffer **de bytes** com resync por
  `55 AA … 5A 5A 5A 5A` (busca de trailer a partir do header) — mais simples e imune a
  notificações partidas no meio de um frame.

---

## 5. Telemetria (leitura)

Todos os campos numéricos são **big-endian**. Referências: `HomeFragment.java:947-1440`,
`HomeSettingActivity.java:1493-1870`, `BatteryPackActivity.java`.

### 5.1 Tipo `0x00` — telemetria principal [APK]

| Off | Tipo | Escala | Campo | Fórmula |
|---|---|---|---|---|
| 0x02 | u16 | ×0.01 | tensão do pack (V) | `v/100` |
| 0x04 | i16 | ×0.036 km/h | velocidade | `v/100*3.6` (raw em cm/s) |
| 0x06 | u16 | km | autonomia restante | `v` (0 = sem estimativa) |
| 0x08 | u16 | m | viagem (trip) | `v` |
| 0x0A | i16 | ×0.01 A | corrente | `v/100` |
| 0x0C | i16 | — | temperatura | `v/340 + 36.53` °C |
| 0x0E | u16 | bitfield | ângulos/estado (ver §8.4) | `>>10` angle10, `>>5&31` angle5, `>>3&3` power3, `&7` operating mode |
| 0x10 | u16 | 1..9 | volume do buzzer | usado na tela de settings |

Exemplo real (`samples-dataPackets.txt`, frame #10):

```
55 AA 19 83 00 00 00 00 00 00 FF D8 FA CC 0C 80 00 00 00 18 5A 5A 5A 5A
      └65,31 V ┘ └0 km/h┘ trip 0 m   corrente −0,40 A  temp 32,6 °C
```

### 5.2 Tipo `0x01` — bateria / falhas [APK]

| Off | Tipo | Escala | Campo |
|---|---|---|---|
| 0x02 | u16 | % (50..100) | **power alarm / safety margin** (mesmo valor escrito com `W P`) |
| 0x06 | u16 | **×0.1 V** | tensão (exibida como `valor/10` em `VehicleStateActivity.java:300`) |
| 0x0A | u16 | °C | temperatura máxima |
| 0x0C | u16 | °C | temperatura mínima |
| 0x10 | u16 | bitfield | falhas/estados (ver §5.6) |

⚠️ Pegadinha de escala: a tensão **não** tem a mesma unidade dos dois frames —
tipo `0x00` usa ×0,01 V e tipo `0x01` usa ×0,1 V. **[APK-EW]** `C5933v.java:2239-2243` faz
exatamente o mesmo (`getShort(2) * 0.01f` no tipo 0 e `getShort(6) * 0.1f` no tipo 1).

**[APK-EW]** `C5933v.java:2239-2290` valida `[0x02]` na faixa 50..100 e `[0x06]/10` contra a
tensão nominal do modelo — corrobora a escala.

### 5.3 Tipo `0x04` — settings, odômetro e modelo [APK]

| Off | Tipo | Escala | Campo |
|---|---|---|---|
| 0x02 | u32 | m | odômetro total (`/1000` → km) |
| 0x06 | u16 | bitfield | configurações atuais (§8.4) |
| 0x0A | u16 | km/h | velocidade de tiltback (≥100 = desligado) |
| 0x0C | u8 | 0..9 | modo do LED ambiente |
| 0x0E | u8 | bitfield | falhas (bit a bit — §5.6) |
| 0x0F | u8 | 0..3 | estado da luz (0=off, 1..3=ligada) |
| 0x10 | u16 | id | `devideType` (modelo/geração; ver §9) |

### 5.4 Tipo `0x07` — corrente, temperatura do motor e PWM [APK]

| Off | Tipo | Escala | Campo |
|---|---|---|---|
| 0x02 | i16 | ×0.01 A | corrente de barramento |
| 0x05 | 2 nibbles | — | nibble baixo: limite de inclinação lateral (0..9 → 45..90°) · nibble alto: field weakening (0..9) |
| 0x06 | i16 | °C | temperatura do **motor** (rótulo "Motor temperature") |
| 0x08 | i16 | % | PWM |

### 5.5 Tipo `0x02` — estado da calibração

| Off | Tipo | Campo |
|---|---|---|
| 0x02 | u16 | 0 = "veículo em movimento" (recusa) · 1 = iniciando/aguardando confirmação · 2 = concluída |

### 5.6 Falhas (bits)

**[APK]** Tipo `0x04`, byte `0x0E` (8 bits, do LSB ao MSB) — `HomeFragment.java:1258-1385`:

| Bit | Mensagem (`R.string`) |
|---|---|
| 0 | `alarm_power_h` — potência alta (perigoso) |
| 1 | `mos` — falha de MOS |
| 2 | `alarm_gyro` — falha do giroscópio (só exibida se velocidade < 15) |
| 3 | `alarm_volt_l` — subtensão |
| 4 | `alarm_volt_h` — sobretensão |
| 5 | `alarm_temp_h` — temperatura alta |
| 6 | `alarm_hall` — falha de Hall |
| 7 | `alarm_lock` — bloqueada (exige 3 leituras consecutivas) |

**[APK]** Tipo `0x01`, u16 BE @0x10 — `HomeFragment.java:1207-1233`, `BatteryInfoActivity.java`.
O app converte o u16 em string de 16 bits (byte 0x10 primeiro, MSB primeiro; índice 0 = bit 15)
e lê posições fixas:

| Posições (bit do u16) | Mensagem |
|---|---|
| chars 2..3 = bits **13..12** | `00` superaquecimento · `01` baixa temperatura |
| chars 9..11 = bits **6..4** | `001` comunicação anormal · `010` bateria desconectada · `100` tensão de grupo anormal · `101` carga anormal |

**[APK-EW]** EUC World lê os mesmos campos como `(v>>12)&3`, `(v>>4)&7` e ainda
`(v>>3)&1`, `(v>>7)&1`, `(v>>8)&3`, `(v>>10)&3`, `(v>>14)&3` (por pack) — `C5933v.java:2253-2262`.

### 5.7 Percentual de bateria

**[APK]** O app **não usa** um % da roda: calcula por uma tabela fixa de tensão
(`HomeFragment.showVoltage`, limiares em 0,01 V):

| ≥ 6550 | ≥ 6410 | ≥ 6270 | ≥ 6130 | ≥ 5990 | ≥ 5850 | ≥ 5720 | ≥ 5580 | ≥ 5450 | ≥ 5320 | < 5320 |
|---|---|---|---|---|---|---|---|---|---|---|
| 100 % | 90 % | 80 % | 70 % | 60 % | 50 % | 40 % | 30 % | 20 % | 10 % | 0 % |

**[INF]** A tabela é única para todos os modelos (não considera 67,2/84/100,8/134,4 V).
Para o app novo, recomenda-se a abordagem do EUC World: `batteryFullVoltage`/`batteryEmptyVoltage`
por modelo (`analysis/begode/eucworld-ref/res/raw/gotway_euc_list.json`).

---

## 6. BMS

Ver detalhamento em `analysis/begode/bms.md`. Resumo implementável:

| `[18]` | `[19]` | Conteúdo |
|---|---|---|
| `0x01` | `0x18` | resumo da bateria (tensão/temps/falhas/flags) — §5.2 |
| `0x01` | `0x00..0x06` | "info do BMS": alarme %, contador, índice, temperaturas, 16 bits de status |
| `0x02` | página | tensões de célula do **pack 1**: 8 × u16 BE em mV |
| `0x03` | página | pack 2 |
| `0x05` | página | pack 3 |
| `0x06` | página | pack 4 |

- Célula `i = página*8 + n`, valor `= u16 BE @ (0x02 + 2n)` em **mV**.
- O app lê **páginas 0..5** (48 células) e faz padding das listas até 50; as amostras reais
  mostram **páginas 0..6** (7 páginas) → a página 6 é ignorada pelo app.
- **[APK-EW]** EUC World usa o mesmo esquema (`célula = [19]*8 + n`, `getShort(2+2n)/1000` V)
  para 2 packs (tipos 2 e 3).
- Bitfield de status (16 bits, LSB primeiro): bit 7 = MOS; bits 8..9 = temperatura/tensão de
  grupo; 10..11 = equalização; 12..13 = balanceamento; 14..15 = estado de carga/carregando;
  4..6 = código de falha.
- **Não existe comando para pedir BMS**: os frames chegam sozinhos (o app só escuta).

**[INF]** Exemplo de frame de célula (pack 2, página 0) das amostras:

```
55 AA 10 05 10 04 10 04 10 04 10 04 10 04 10 04 10 03 03 00 5A 5A 5A 5A
 → 4.101 4.100 4.100 4.100 4.100 4.100 4.100 4.099 mV
```

---

## 7. Odômetros e contadores

| Valor | Onde | Escala | Como obter |
|---|---|---|---|
| Odômetro total | tipo `0x04`, u32 BE @0x02 | metros | `v/1000` km (também salvo em `EasySP("totalDistance")`) |
| Viagem (trip) | tipo `0x00`, u16 BE @0x08 | metros | vem do firmware |
| Autonomia estimada | tipo `0x00`, u16 BE @0x06 | km | vem do firmware |
| Tempo de condução | **calculado no app** | s | integra deltas de tempo somente quando velocidade > 0 (`HomeFragment.java:1040-1060`) |
| Velocidade máxima / média | **calculado no app** | km/h | picos e `trip/tempo` — `HomeFragment.java:990+` |
| Odomêtro por viagem (app) | **calculado no app** | m | diferença entre o primeiro trip lido e o atual |

**[APK]** O app **não** tem comandos de zerar viagem/odômetro nem de ler contadores
independentes. O "distance correction"/"speed correction" do EUC World é aplicado só no
aplicativo cliente. As estatísticas de viagem ficam em `SharedPreferences`/Gson no celular
(`EasySP("VehicleBean")`, amostras a cada 3 s).

---

## 8. Configuração (escrita)

### 8.1 Inventário completo de comandos TX [APK]

Formato: cada linha é uma **escrita BLE separada** (ASCII). `sleep` = atraso em ms entre escritas
(implementado com `Thread.sleep` em thread própria; a fila de escrita ainda adiciona 5/10 ms).

| Comando | Bytes (hex/ASCII) | Valores válidos | Efeito | sleeps | Ack lido em |
|---|---|---|---|---|---|
| Nome | `4E` `N` | — | pede nome; resposta `NAME:<nome>` | — | texto ASCII |
| Versão | `56` `V` | — | pede firmware; resposta 9 bytes ASCII | — | texto ASCII |
| Unidade km | `67` `g` | — | unidade = km | — | tipo `0x04` bit 0 |
| Unidade milhas | `6D` `m` | — | unidade = milhas | — | tipo `0x04` bit 0 |
| Modo Soft | `73` `s` | — | riding mode 0 | — | tipo `0x04` bits 13..15 |
| Modo Medium | `66` `f` | — | riding mode 1 | — | idem |
| Modo Strong | `68` `h` | — | riding mode 2 | — | idem |
| Buzina | `62` `b` | — | toca a buzina | — | — |
| Luz (ciclo) | `51` `Q` → `54` `T` → `45` `E` | 0→1→2→0 | próximo estado da luz | — | tipo `0x04` byte 0x0F |
| Luz (4º estado) | `52` `R` | só `devideType==205` | estado extra | — | idem |
| Alarmes: todos | `6F` `o` | — | liga os 2 níveis | — | tipo `0x04` bits 10..11 |
| Alarme nível 1 off | `75` `u` | — | desliga alarme 1 | — | idem |
| Alarme nível 2 off | `69` `i` | — | desliga alarme 2 | — | idem |
| Power-alarm tiltback OFF | `78` `x` | — | desliga tiltback por potência | — | tipo `0x00` bits 3..4 |
| Power-alarm tiltback ON | `65` `e` | — | liga | — | idem |
| Ângulo máx. Low | `3E` `>` | — | nível 0 | — | tipo `0x04` bits 7/8 |
| Ângulo máx. Medium | `3D` `=` | — | nível 1 | — | idem |
| Ângulo máx. High | `3C` `<` | — | nível 2 | — | idem |
| Calibração | `63` `c` | — | inicia calibração | 1000 | tipo `0x02` |
| Calibração (confirmar) | `79` `y` | — | confirma | — | tipo `0x02` = 2 |
| Tiltback desligado | `22` `"` | — | desliga o tiltback de velocidade | — | tipo `0x04` @0x0A (0 ou ≥100 = desligado) |
| Modelo + | `2B` `+` | — | entra em "seleção" (operating mode) | 2000 | tipo `0x00` bits 0..2 |
| Modelo − | `2D` `-` | — | encerra | — | idem |
| Volume do buzzer | `57 42 <d>` `W B d` | `d`=1..9 | volume | 100/100 | tipo `0x00` @0x10 |
| LED ambiente | `57 4D <d>` `W M d` | `d`=0..9 | modo do LED | 100/100 | tipo `0x04` @0x0C |
| Field weakening | `57 43 <d>` `W C d` | `d`=0..9 | "magnetic field increase speed" | 50/50 | tipo `0x07` nibble alto @0x05 |
| Inclinação lateral | `57 58 <d>` `W X d` | `d`=0..9 → 45..90° (passo 5°) | limite de inclinação esquerda/direita | 50/50 | tipo `0x07` nibble baixo @0x05 |
| Ajuste do berço/pedal | `57 52 <d>` `W R d` | `d`=0..9 | "horizontal adjustment" | 100/100 | tipo `0x00` bits 10..15 @0x0E |
| Compensação de ângulo | `57 55 <d>` `W U d` | `d`=0..8 (exibido `d-4`) | "pedal turning angle adjustment" | 100/100 | tipo `0x00` bits 5..9 @0x0E |
| Alarme de potência | `57 50 <t><u>` `W P t u` | 50..90 % (passo 5) | safety margin alarm | 50/50/100 | tipo `0x01` @0x02 |
| Tiltback de velocidade | `57 59 <t><u>` `W Y t u` | 3..90 km/h (passo 3) — 2 dígitos ASCII | velocidade de tiltback | 100/100 | tipo `0x04` @0x0A |

**[APK]** Valores oferecidos pela UI (tabelas do app):

- **tiltback de velocidade**: `Close`, 3, 6, 9, 12, …, 90 (passo 3) — `HomeSettingActivity.java:240`;
  acima de 100 = desligado no frame de settings.
- **alarme de potência**: 50, 55, 60, 65, 70, 75, 80, 85, 90 — `HomeSettingActivity.java:245`;
  **[APK-EW]** EUC World aceita 0..99 e valida a faixa 50..100.
- **volume**: 1..9; **field weakening**: 0..9; **tilt lateral**: 0..9 (45..90°);
  **LED ambiente**: 0..9; **ângulo do berço**: 0..9; **compensação**: 0..8.
- **"Maximum speed reminder"** existe na tela, mas é **local** (gravado em `EasySP`) e
  **não envia nada à roda** — `HomeSettingActivity.java:796-818`.

### 8.2 Como o app confirma a escrita [APK]

Não existe ACK dedicado. Após enviar, a tela abre um `Timer` de **3000 ms** e considera
sucesso quando o **valor de retorno aparece no frame correspondente** (tipo 0/1/4/7); se não
aparecer até 3 s, mostra "Setup failed" — `HomeSettingActivity.java:1988-2033` (`timer()`),
1417-1455 (`showDialogSet`) etc.

**[INF]** Implementação recomendada: enviar o comando, ler o frame refletido (tipos 0/1/4/7),
comparar o campo e considerar timeout de ~3 s.

### 8.3 Leitura do estado das configurações (tipo `0x04`) [APK]

Para montar a tela de settings, o app usa apenas o **frame tipo `0x04`**:

- modo de condução: u16 BE @0x06 `>> 13` (0=Soft/1=Medium/2=Strong);
- modo de alarme: `>> 10 & 3` (0 = todos ligados, 1 = nível 1 desligado, 2 = nível 2 desligado);
- nível de ângulo: bits 7 e 8 = `b[0x06].bit7*2 + b[0x06].bit0`;
- unidade: `b[0x07].bit0` (0=km, 1=milhas);
- tiltback: u16 BE @0x0A (km/h; ≥100 = desligado);
- LED: u16 BE @0x0C (0..9);
- `angle10`/`angle5`/`power3`/`operating mode`: u16 BE @0x0E → `>>10`, `>>5&31`, `>>3&3`, `&7`;
- `devideType`: u16 BE @0x10.

### 8.4 Bloco de "settings" no tipo `0x00` (u16 BE @0x0E) [APK]

| Bits | Campo | Valores |
|---|---|---|
| 0..2 | operating mode (`momm`) | 0 = off-road, 1 = Racing |
| 3..4 | power-alarm tiltback (`power3`) | 0 = off, 1 = on |
| 5..9 | compensação de ângulo (`angle5`) | exibido `valor-4` (−4..+4) |
| 10..15 | ajuste do berço (`angle10`) | 0..63 (setter 0..9) |

### 8.5 O que **não** existe

| Item do plano (§3.4) | Situação real | Evidência |
|---|---|---|
| Correção/offset de tensão | **não existe** comando; EUC World aplica offset no cliente | EUC World `f23538a0` (offset local) |
| Beeper mute (silenciar de vez) | só volume 1..9 (`W B`) | tabela §8.1 |
| Farol (headlight) dedicado | a luz é ciclo `Q`/`T`/`E` (+`R` no `devideType 205`) | `HomeFragment.java:2205-2257` |
| Lock/handshake de auth | **não existe** | nenhuma referência no APK |
| Dynamic tiltback (CF) | não é configurável pelo app; **[APK-EW]** a variante `CF` é detectada pelo firmware e o campo `safety margin` vem em outro offset | `C5933v.java:1666-1700` |
| Reset de viagem | **não existe** | — |
| Data/hora | **não existe** | — |

---

## 9. Modelos e especificidades

### 9.1 O app oficial não tem lista de modelos

- **[APK]** A identificação vem **da própria roda**: nome ASCII (`N` → `NAME:<nome>`) e
  firmware (`V` → 9 ASCII). Nenhuma tabela de modelos/nomes existe no pacote (busca por
  `MSuper`, `Nikola`, `Master`, `EX30`, `Extreme`, `T4` no APK: nenhuma ocorrência de modelo).
- **[APK]** O campo `devideType` (u16 BE @0x10 do frame `0x04`) é usado apenas para variar
  comportamento local: `8` (não revalida settings/LED; exige um passo a mais) e
  `205` (a luz ganha um 4º estado `R`). Nas 210 amostras reais o valor foi **18**.
- **[INF]** Logo, um adapter novo **não deve** depender de nome de modelo: use o firmware/nome
  reportado e um mapa de capacidades por prefixo de firmware.

### 9.2 Prefixos de firmware conhecidos [APK-EW]

| Prefixo | Significado |
|---|---|
| `GW########` | Gotway/Begode (ex.: `GW1511003`) |
| `JL…`, `JN…` | famílias alternativas aceitas pelo EUC World |
| `CF########` | variante **"dynamic tiltback"/CF** (margem de segurança em campo próprio) |

### 9.3 Lista de modelos suportados (referência do EUC World) [APK-EW]

`analysis/begode/eucworld-ref/res/raw/gotway_euc_list.json` — **93 modelos** com
`eucId`, `batteryFullVoltage`/`batteryLowVoltage`/`batteryEmptyVoltage`, `noLoadSpeed`,
`matchByName`/`matchByFirmware` e flags `battery1SmartBms`/`battery2SmartBms`:

- **Extreme Bull**: Commander C30/C38/Pro/Pro 50S/Mini/Mini Pro/GT/Max.
- **Gotway/Begode**: MCM 3/4/4 HS/4 Pro/5 (67/84 V)/5 V2, Msuper 3/Sport/V3S+/X (84/100 V)/X Plus,
  Msuper Pro C30/C38 (+Plus), Monster (84/100 V)/Pro/Sport/V2/V3/V3 Plus, Nikola/Nikola Plus/Plus Plus,
  ACM 16/S+, Tesla (67/84 V)/Tesla 2/2 Plus/T3, RS C30/C38 (+Plus), EX/EX Plus/EX.N/EX.N Plus/EX.N C30/C38,
  EX20S C30/C38, EX30 C40, X-Men C30/C38, Hero C30/C38, Mten 3 (67/84 V)/3 Plus/4/5/Mini, A1/A2/A5/C8,
  Luffy, Rocket, Future, T4/T4 Pro, Master/Master Pro/Master X, Falcon/Falcon Pro, Extreme,
  ET Max, GT Pro, RACE, X-Way (134/168 V), X-Max, Panther, Griffin, Blitz/Blitz Pro.
- **BMS "smart" (2 packs)** nas flags: Blitz, Blitz Pro, Extreme, ET Max, Falcon Pro, Griffin,
  Panther, RACE, X-Max, X-Way (134 V), X-Way (168 V) — **[INF]** os modelos com BMS detalhado
  são exatamente os que enviam os frames de célula (§6); a lista do app oficial não restringe nada.

---

## 10. Firmware e extras

### 10.1 Atualização de firmware (YMODEM sobre a mesma FFE1) [APK]

1. Tela de update envia `V` para ler a versão atual (9 bytes ASCII).
2. Ao iniciar, envia **`!` (0x21) e `@` (0x40) alternados a cada 3000 ms** (máx. 12 s).
3. A roda entra no bootloader e responde **1 byte `0x02` (STX)**.
4. O app baixa o `.bin` da API (`/api/version/...`) e envia via **YMODEM**:
   `%` (hello) → `SOH` → `C` (0x43) → cabeçalho 128 B (`SOH, blk 0, ~blk, "nome\0tamanho\0md5", CRC16`) →
   blocos de **1024 B** (`STX, blk, ~blk, dados, CRC16`) → `EOT` → bloco final 128 B zerado →
   a roda responde `0x05` (MD5 OK) ou `MD5_ERR`.
   ACK = `0x06`, NAK = `0x15`, CAN = `0x18`; timeout 6 s; até 6 retentativas por pacote.
5. Cada pacote YMODEM (até 1029 B) é fatiado pelo mesmo `sendData` (chunks de `MTU-3`).

Detalhes: `analysis/begode/ymodem-dfu.md`.

### 10.2 Outras funções do app (sem efeito no protocolo)

- Trajetória/GPS, câmera, gravação de tela, mural social, ranking, firmware via nuvem —
  tudo HTTP (`http://one-api.begode.com`, `/api/...`), irrelevante para o adapter.
- "Mileage statistics", gráficos de estado (tensão/corrente/PWM/temp/velocidade) e o
  histórico são derivados no app (`SharedPreferences` + Gson).

---

## 11. Limitações e lacunas (o que exige captura BLE / roda real)

| # | Lacuna | Como resolver |
|---|---|---|
| 1 | Confirmar em roda real os offsets/escalas (especialmente velocidade, temperaturas e o campo `[0x0E]` do tipo 0) | `btsnoop_hci.log` do app oficial conectado a uma roda Gotway/Begode |
| 2 | `devideType` (u16 @0x10): mapeamento valor → modelo e o que muda em `8` e `205` | capturar em Master/EX30/Extreme/T4 e comparar |
| 3 | Semântica de `+`/`-` (o app chama de "firmware model selection" e exibe "off-road"/"Racing") | teste na roda: observar o display e o campo `momm` |
| 4 | Significado de `[19]` (0x18 vs página 0..6) e existência de página 6 de células | captura de uma roda com mais de 48 células por pack |
| 5 | Campos desconhecidos do frame de BMS: `0x02`, `0x06`, `0x0E` (contador × tensão) | captura com roda em carga/descarga |
| 6 | Escala de temperatura do tipo `0x01` (@0x08/@0x0E) e do tipo 0 (@0x0C) | comparação com a tela da roda |
| 7 | Qual `[19]` (páginas) cada modelo usa; modelo com 2 vs 4 packs | capturas por modelo |
| 8 | Se o firmware novo (CF) aceita os mesmos comandos ASCII | testar `W X`/`W Y` em firmware `CF…` |
| 9 | Comportamento com MTU > 23 (o app nunca pede MTU maior) | teste com adapter novo pedindo MTU 247 |
| 10 | Robustez: frames chegando fatiados/colados; o app oficial tem bugs de buffer (nunca descarta lixo) | emulador de roda (§6 do plano) |

Pegadinhas já conhecidas:

- **[APK]** O buffer de RX **não é limpo** quando não há frame válido — em ambiente com ruído
  a UI pode ficar sem atualizar (recomendação: resync por trailer).
- **[APK]** `BatteryPackActivity` lê `minTem`/`maxTem` do tipo `0x01` com
  `byteArrayToInt` (BE de 2 bytes) enquanto `HomeFragment` usa `getShort` (BE u16) — mesmo valor,
  mas cuidado na hora de portar.
- **[APK]** A confirmação de escrita é feita por leitura do frame refletido com **timeout de 3 s**;
  comandos "sem retorno" (`b` buzina, `Q/T/E` luz, `c/y` calibração) não têm ack dedicado.
- **[APK]** O app **não** envia `N` novamente se a conexão oscilar sem cair (`autoReconnect=false`).

---

## 12. Evidências e referências

**Deste APK** (`analysis/begode/`):

| Arquivo | Conteúdo |
|---|---|
| `evidencias.md` | método, hashes, índice de achados |
| `tx-commands.md` | tabela de comandos TX com linhas do código |
| `rx-frames.md` | framing, offsets, escalas, validação das amostras |
| `bms.md` | frames de BMS e bitfield de status |
| `ymodem-dfu.md` | fluxo de firmware |
| `samples-dataPackets.txt` + `validate-samples.py` | 210 frames reais + validação numérica |
| `ui-strings-en.txt` | rótulos de UI em inglês |
| `decompiled/…` | 19 classes-chave descompiladas (HomeFragment, HomeSettingActivity,
  AmbientLightSettingActivity, ModeSetActivity, BatteryPackActivity, BatteryInfoActivity,
  VehicleStateActivity, SetActivity, MineFragment, FirmwareUpdateActivity, update/*,
  ble/BleSppGattAttributes, ble/ConnectionConfiguration, ble/util/HexUtil, base/Utils, Config) |
| `eucworld-ref/` | `gotway_euc_list.json` (93 modelos) e `preferences_wheel_gotway*.xml` do EUC World |

**Referências cruzadas**

- **[APK-EW]** EUC World 2.66.0 — `analysis/decompiled/sources/p364uh/C5933v.java`
  (adaptador Gotway: comandos `:846-1200`, parse de RX `:1540-2380`, nomes/firmware `:1400-1460`).
- **[APK-EW]** `analysis/07-bt-definitions.txt` — definições BLE `gotway_v1/v2/v3/ee`
  (FFE0/FFE1 + FFF0/FFF1 + DFU) usadas pelo EUC World — **não** usadas pelo app oficial.
- **[COM]** Plano multi-protocolo do projeto: `docs/07-plano-multi-protocolo.md` §3.4.
- **[COM]** WheelLog (GPL) — usado apenas para conferência de fatos de comportamento
  (letras `N`/`V`/`W …`), sem copiar código.

**Notas de fidelidade**

- Frames de exemplo "calculados" estão marcados **[INF]**; os exemplos com hex completo vêm de
  `samples-dataPackets.txt` (captura real embutida no APK, roda parada).
- O APK não foi executado; nenhuma roda real foi usada nesta análise.
