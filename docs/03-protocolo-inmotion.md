# Protocolo Inmotion — Referência técnica

Extraído por engenharia reversa do **EUC World 2.66.0** (`net.lastowski.eucworld`, sha256 `5af0c929…f9a7`)
e cruzado com documentação comunitária open-source (eucplanet/MIT, WheelLog/GPL, eucWatch).

> **Proveniência:** fatos marcados com **[APK]** foram extraídos do APK descompilado (arquivo:linha em `analysis/decompiled/sources/`).
> Fatos marcados com **[COM]** vêm de projetos open-source (ver "Referências"). **[INF]** = inferência.
> A Inmotion tem **duas famílias de protocolo incompatíveis**: V1 (pré-V11) e V2 (V11+).

---

## 1. Famílias de protocolo

| Família | Modelos | Transporte BLE |
|---|---|---|
| **V1** ("inmotion") | V5, V8, V10, L6, Solowheel, R-series | Serviços `0xFFE0` (notify `0xFFE4`) + `0xFFE5` (write `0xFFE9`); framing CAN |
| **V2** ("inmotion_new") | V9, V11, V11y, V12, V13, V14, E20, P6 | Nordic UART `6e400001` (write `6e400002`, notify `6e400003`) |

**[APK]** O EUC World escolhe o transporte em `net/lastowski/eucworld/services/BleService.java`:
- INMOTION (V1): escreve em `FFE5/FFE9` em blocos de 20 bytes, escuta em `FFE4` (BleService.java:218-241, 1681-1684).
- INMOTION_NEW (V2): escreve em NUS `6e400002`, escuta em `6e400003` (BleService.java:274-295, 1677-1680).

**[APK]** Enum de protocolos do app (`p364uh/AbstractC5919h0.java:175-182`): `GOTWAY, INMOTION, INMOTION_NEW, KINGSONG, NINEBOT, NINEBOT_NEW, VETERAN`.

**[APK]** Adaptadores: `C5935x` = Inmotion V1; `C5937z` = Inmotion V2 (V11+) — 4.223 linhas.

---

## 2. Framing V2 (V11+) — formato de quadro

**[APK]** Construção TX em `p364uh/C5937z.java:11214W` (linha ~1516):

```
AA AA <tipo> <len> <payload...> <XOR>
```

| Offset | Tam | Campo |
|---|---|---|
| 0-1 | 2 | `0xAA 0xAA` (header) |
| 2 | 1 | tipo do quadro: `0x11`=INITIAL, `0x14`=DEFAULT, `0x16`=EXTENDED, `0x13`=AUTH |
| 3 | 1 | comprimento do payload |
| 4..N | N | payload |
| N+1 | 1 | checksum = XOR de todos os bytes anteriores |

**[APK]** Validação RX (`m11185Z`, linha 2095): XOR de todos os bytes exceto o último == último byte.

**[APK]** Unescape RX (`m11155D0`, linha 1552): `0xA5` é escape — `A5 XX` → `XX`; `A5 A5` → `A5`.
O app acumula bytes recebidos num buffer (`f23725d2`), remove escapes, valida XOR, e então extrai payload = `frame[4..len-1)` (linha 3131-3133).

**[COM]** Byte stuffing completo (eucplanet): `0xAA` → `A5 AA`; `0xA5` → `A5 A5`. Checksum calculado sobre o payload **não escapado**.

**[COM]** Forma estendida (tipo `0x16`): payload = `02 21 <cmd> <data...>` (routing fixo `02 21`).

---

## 3. Sequência de inicialização V2

**[APK]** Máquina de estados em `p364uh/C5937z.java:2686-2900` (`m11215X`), na ordem:

| # | Op | Tipo | Payload | Resposta esperada |
|---|---|---|---|---|
| 1 | INIT_ID (carType) | `0x11` | `02 01` | `82 01 …` |
| 2 | INIT_SERIAL | `0x11` | `02 02` | `82 02 <serial ASCII>` |
| 3 | INIT_FIRMWARE | `0x11` | `02 06` | `82 06 <versões u32>` |
| 4 | INIT_SETTINGS | `0x14` | `20 20` | `A0 20 …` |
| 5 | READ_VALUES (poll) | `0x14` | `04` | `82 04 …` (repetido continuamente) |
| 6 | READ_TRIP | `0x14` | `10 00 01` | `82 10 …` |
| 7 | READ_TOTAL | `0x14` | `11` | `82 11 …` |
| 8 | READ_BATTERY | `0x14` | `05` | `82 05 …` |
| 9+ | Baterias 2/3/4 (por modelo) | `0x16` | `02 25 04` / `02 26 04` / `02 27 04` | seriais dos packs |
| 10 | Tensões de célula pack N | `0x16` | `02 24 02` … `02 27 02` | 32 × u16 mV |
| 11 | Status pack N | `0x16` | `02 24 01` … `02 27 01` | status |

**[COM]** eucplanet confirma a mesma ordem e acrescenta: `getUselessData()` (cmd `0x10`, data `00 01`) é **obrigatório enviar** durante o init (resposta ignorada) — espelha o app oficial.
**[COM]** P6 quebra essa sequência (precisa de routing estendido + auth em toda conexão); detectar pelo nome BLE `P6-` **antes** do primeiro pacote.

**[APK]** Detecção de modelo (`mo11015h`, caso 1, linhas 3150-3197): lê `payload[3]`:

| byte | Modelo (EUC World) | Config |
|---|---|---|
| 6 | V11 (variant 2 → "V11Y") | 20S/4P (se variant==2) |
| 7 | V12 | |
| 8 | V13 | 30S/8P |
| 9 | V14 | 32S/4P |
| 10 | E20 | |
| 11 | V12S | 20S/4P |
| 12 | V9 | 20S/4P |
| 13 | P6 | 56S/4P |
| outro | assume V12 com aviso | |

(**[INF]** Os números são o "model class" reportado pela roda; WheelLog usa um modelId diferente — 61=V11, 71/72/73=V12 HS/HT/Pro, 81=V13, 82=V13Pro, 91/92=V14, 111=V12S, 121=V9, 21=P6.)

---

## 4. Handshake de autenticação (V2)

**[COM]** Framing `0x13`, routing `02 00`:
1. Phone → roda: `AA AA 13 04 02 00 00 02 17` (requestAuthKey)
2. Roda → phone: `02 80 02 [16 bytes]` (blob)
3. Phone → roda: `AA AA 13 14 02 00 00 82 [mesmo blob] CK` (echo)
4. Roda → phone: `02 80 82 01` = sucesso

**[COM]** É um gate de protocolo, não criptografia: a roda aceita o próprio blob de volta.
Obrigatório para `SET_LOCK` em V12+ e para **todo** controle no P6. V11 não exige.

**[APK]** O EUC World 2.66.0 **não** implementa esse handshake (nenhuma referência a `0x13`/`02 00` no adaptador). [INF] Comandos de lock podem ser descartados em V12+ — ver "Limitações do app".

---

## 5. Telemetria V2 — parse por modelo

**[APK]** Handlers de resposta por modelo (`p364uh/C5937z.java`, switch em `mo11015h`):
- "V11" → `m11165K` (valores) / `m11206J` (settings)
- "V12" → `m11172P` / `m11208O`
- "V13" → `m11174R` / `m11211Q`
- "V14" → `m11177T` + `m11207L`/`m11205G`/`m11213U` / `m11212S`

**Não existe layout único:** cada modelo tem parser próprio. As tabelas abaixo estão
separadas por família. **Convenção de offsets:** "nos dados" = payload sem o byte de
opcode; "no payload" = incluindo o opcode.

### 5.1 V11y / família V14 (fw ≥1.4) — validado na roda

Resposta ao poll `04`: opcode `0x84`, i16/u16 little-endian, offsets **nos dados**.
Validado numa V11y real (fw 2.5.54) pelo app novo; bate com o layout do V14 do
eucplanet/WheelLog:

| Offset | Campo | Escala |
|---|---|---|
| 0 | Tensão | /100 V |
| 2 | Corrente | /100 A |
| 8 | Velocidade | /100 km/h |
| 12 | Torque (não lido pelo app) | /100 N·m |
| 14 | Carga do motor (PWM) | /100 % |
| 20 | Pitch (tilt) | /100 ° |
| 22 | Roll | /100 ° |
| 28 | Viagem (V14) | ×10 m |
| 34 | Bateria | /100 % |
| 40 | Limite de velocidade — tiltback real da roda | /100 km/h |
| 48 | Limite de corrente (58 A na V11y) | /100 A |
| 58-63 | Temperaturas: MOS, motor, bateria, placa, CPU, IMU | `byte − 176` °C |
| 74 | Estado: bits 0-2 = modo (0 travado, 1 rodando, 2 desligado, 3 parado) | — |

Observações:
- Temperaturas: byte `< 0x80` é tratado como sensor ausente (mesma proteção do app oficial).
- O limite de corrente em `48` foi lido como 58,00 A na V11y; o eucplanet documenta `50`
  para o V14 — **[INF]** pode variar por modelo (capturar antes de assumir).
- Viagem e totais **não** vêm nesse quadro: usar `10 00 01` / `11` (§6.1).
- O campo de limite do **quadro de settings** (§8) usa outra unidade (0,5 km/h); este
  aqui é o valor real aplicado.

### 5.2 V12 (EUC World 2.66.0, `m11160H`, linhas 1596-1625) — offsets **no payload**

Todos i16 little-endian /100:

| Offset | Campo |
|---|---|
| 1 | valor 1 (escala 0.01) |
| 3 | valor 2 (0.01) |
| 5 | valor 3 (abs, 0.01) |
| 9 | `100 - valor*0.01` |
| 17, 21, 25, 31, 33 | valores × 0.01 |
| 41, 44, 45, 46 | tensões de célula (via `m11154C0`) |
| 55 | bitfield: bits 0-2 modo, bit 6, bit 7, bits 8-9 |
| 59 | inteiro |
| 63 | bitfield + inteiro |

**[COM]** V12 (layout compacto): velocidade em `4`, PWM em `8`, trip em `22` /100 km,
bateria única em `24` /100, temps em `40-46`, state byte em `54`, light byte em `55`.

### 5.3 V14 (WheelLog/eucplanet) — offsets nos dados

`0` tensão /100 V · `2` corrente /100 A · `8` velocidade /100 km/h · `12` torque /100 N·m ·
`14` PWM /100 % · `16` potência bateria · `18` potência motor · `20` pitch /100° · `22` roll /100° ·
`28` trip ×10 m · `34`/`36` bateria 1/2 % /100 · `40` limite velocidade dinâmico /100 ·
`50` limite corrente /100 · `58-63` temperaturas (`byte − 176` °C) · `74` pcMode · `76` bit 1 = farol.

### 5.4 V11 (fw <1.4) — pendente

**[COM]** A V11 tem **duas variantes** (fw <1.4 e ≥1.4) — offsets diferentes; a variante
antiga não está documentada. Se aparecer uma, capturar.

**[COM]** ⚠️ Divergência entre WheelLog e eucplanet na escala de potência (offset 16) e
velocidade do V14: validar contra a roda real.

---

## 6. Comandos de controle V2

**[APK]** Tabela completa extraída de `p364uh/C5937z.java` (payload não escapado; tipo `0x14` salvo indicação):

> **Notação:** os payloads de **escrita** começam com o opcode `0x60` (`60 xx …`).
> Versões anteriores deste doc transcreviam esse byte como `96` — `0x96` é
> `0x80 | 0x16`, o opcode de **resposta** (mesmo padrão de `0x90`/`0x91` =
> `0x80|0x10`/`0x80|0x11`); `0x60` é o de **comando**. Confirmado no app oficial
> (`docs/protocolos/inmotion.md` §4.3) e validado na V11y.

| Comando | Payload | Método EUC World | Notas [COM] |
|---|---|---|---|
| WRITE_SPEED_TILTBACK | `60 21 <u16 LE×100>` | m11192f1 | **Na V11y só funciona estendido**: `02 21 60 21 <u16 LE> 00 00` (tipo `0x16`) — validado em captura (30 → 35 → 60 km/h). O valor é **km/h × 100** (30 km/h = `B8 0B`), confirmado na roda: 59 → a roda reportou 59,00. O EUC World manda em framing padrão para a V11y e é ignorado |
| WRITE_PEDAL_TILT | `60 22 <u16 LE>` | m11184Y0 | |
| WRITE_PEDAL_SENSITIVITY | `60 25 <v> <v>` | m11183X0 | |
| WRITE_RIDING_MODE | `60 23 <modo>` | m11187a1 | 1 = Sport (material comunitário chama de classic), 0 = Comfort |
| WRITE_PERFORMANCE_MODE | `60 24 <0/1>` | m11186Z0 | |
| WRITE_SPEAKER_VOLUME | `60 26 <0-100>` | m11190d1 | |
| WRITE_STANDBY_TIMER | `60 28 <u16 seg>` | m11195i1 | |
| WRITE_HEADLIGHT_BRIGHTNESS | `60 2B <v>` ou `<low> <high>` | m11173Q0/m11175R0 | V12: dois feixes |
| WRITE_SOUNDS (mute) | `60 2C <0/1>` | m11189c1 | invertido no fio |
| WRITE_DAYTIME_RUNNING_LIGHTS | `60 2D <0/1>` (V14) / `60 44` | m11167L0 | V12 não tem DRL |
| WRITE_HANDLE_BUTTON | `60 2E <0/1>` | m11171N0 | invertido |
| WRITE_AUTO_HEADLIGHT | `60 2F <0/1>` | m11159G0 | V12/V13/V14 |
| WRITE_LOCK | `60 31 <0/1>` | m11176S0 | exige auth em V12+ |
| WRITE_TRANSPORTATION_MODE | `60 32 <0/1>` | m11196j1 | |
| WRITE_LOW_BATTERY_RIDING | `60 37 <0/1>` | m11178T0 | |
| WRITE_COOLING_FAN_QUIET_MODE | `60 38 <0/1>` | m11166K0 | |
| WRITE_MOTOR_SOUND | `60 39 <0/1>` | m11181V0 | |
| WRITE_SPEED_ALARMS | `60 3E <u16> <u16>` | m11191e1 | dois tiers |
| WRITE_HEADLIGHT | `60 50 <0/1>` (V14) / `<low> <high>` (V12) | m11210P0 | 1 byte ignorado em V12 HS/HT/Pro |
| WRITE_HORN | `60 51 <id> <vol>` | — | **V11Y/V13/V14: `02 64` (playBeep)**; V9/V11/V12/V12S: `18 01` (playSound) — ver nota abaixo |
| WRITE_CALIBRATION_CHECK/PERFORM | `60 52 01 00 01` (yaw) / `60 52 01 01 00` (pedal) | m11245z0 | |
| WRITE_MOTOR_NO_LOAD_DETECTION | `60 36 <0/1>` | m11179U0 | |
| WRITE_SCREEN_AUTO_OFF | `60 3D <0/1>` | m11188b1 | V12 |
| WRITE_COOLING_FAN | `60 53 <0/1>` (fw≥1.4) / `60 43` | m11164J0 | |
| WRITE_SPLIT_RIDING_MODES | `60 42 <0/1>` (V12) / `60 3E` | m11193g1 | |
| WRITE_SPLIT_RIDING_MODES_SETTINGS | `60 3F <acc> <brk>` (V11+) / `60 40` (V12) | m11194h1 | |
| WRITE_EXTENDED_LATERAL_TILT_LIMIT | `60 45 <0/1>` | m11169M0 | |
| WRITE_AUTO_HEADLIGHT_THRESHOLDS | `60 2A <a> <b>` | m11163I0 | V12 |
| WRITE_AUTO_HEADLIGHT_LOW_TO_HIGH_THRESHOLD | `60 30 <u16 LE>` | m11161H0 | V12 |
| WRITE_LEDS_MODE / LEDS_MUSIC_COLOR / LEDS_SPEED_MODE | (ver código) | — | |
| WRITE_DIAGNOSTICS | (ver código) | — | |

**[APK]** Tabela completa de ops (56) em `p364uh/C5937z.java` (classe interna `a`), incluindo as de leitura
(INIT_*/READ_*) e escrita (WRITE_*). Cada comando enfileirado como `b(op, tipo, payload)` e enviado com timeout de 500 ms;
retry implícito: comando sem resposta correta → `m11216Y()` reenvia/reinicia (linha 3153).

**[COM]** Power off não é CONTROL: é DIAGNOSTIC (cmd `0x03`) em dois estágios (o 2º estágio não está documentado).

---

## 6.1 Odômetros (viagem e total) — validados na V11y

Dois quadros, ambos no framing DEFAULT (`0x14`), respondidos pela roda:

| Quadro | Pedido | Resposta | Campo | Offset no payload | Escala |
|---|---|---|---|---|---|
| Viagem | `10 00 01` | `0x90` | distância da viagem | 23 | u32 LE / 100 (km) |
| Viagem | `10 00 01` | `0x90` | tempo desde a ligação | 39 | u32 LE (s) |
| Totais | `11` | `0x91` | distância total (odômetro) | 1 | u32 LE / 100 (km) |
| Totais | `11` | `0x91` | tempo total ligada | 5 | u32 LE (s) |

Offsets do EUC World 2.66.0 (`p364uh/C5937z.java`: `getInt(23) * 0.01f` na `0x90`;
`getInt(1) * 0.01f` e `getInt(5) / 3600f` na `0x91`; inteiros lidos como **u32**),
**confirmados em quadro real da V11y**:

```
RX 90 00 01 40 26 62 00 00 00 86 4A 69 81 00 00 00 00 00 00 82 82 82 82 00 ... 00 AE 03 00 00
      ^^^^^ eco do pedido        ^^^^^ 4 bytes zerados = viagem 0,00 km            ^^^^^ 942 s ligada
RX 91 94 C9 00 00 B1 A4 32 00 07 DB 07 00 6F 6D 01 00 3B DE 05 00 82 82 00 00
      ^^^^^^^^^^^^^ 51.604 -> 516,04 km   ^^^^^^^^^^^^^ 3.318.961 s -> 921,9 h ligada
```

Observações:
- A viagem (`0x90`) **zera ao desligar** a roda (é por sessão de uso); o total (`0x91`) é permanente.
- O contador de tempo do quadro de viagem incrementa 1 por segundo (confirmado em captura).
- A V11y responde aos dois quadros; modelos que não responderem devolvem `null` no parser.

## 7. Bateria V2

**[APK]** EUC World consulta por pack: `02 24 01/02`, `02 25 …`, `02 26 …`, `02 27 …` (tipo `0x16`).
Modelos com 4 packs: V14/V13/V12 (via hash de modelo nas linhas 2872-2930).

**[COM]** Resposta de tensões: 32 × u16 LE em mV. Status por pack: byte 0 = bit0 valid / bit1 enabled.

**[COM]** Bateria % do V14 = média arredondada dos packs 1 e 2 (clamp 0-100).
**[COM]** V1 (pré-V11): percentual **não vem no fio** — derivado de curva tensão→% por modelo.

---

## 8. Settings V2 (resposta `A0 20`)

**[APK]** Parse por modelo: V11 `m11206J`, V12 `m11208O`, V13 `m11211Q`, V14 `m11212S`.

### 8.1 V11y — validado na roda

Resposta `A0 20` (payload `A0 20 <dados…>`); offsets **nos dados** (após os 2 bytes `A0 20`):

| Offset | Campo | Escala |
|---|---|---|
| 0 | Limite de velocidade* | /100 |
| 8 | Tilt dos pedais | /100 ° |
| 10 | bit 0: modo de condução (0 = Comfort, 1 = Sport) | — |
| 11 | Sensibilidade dos pedais | % |
| 13 | Split riding: aceleração | % |
| 14 | Split riding: frenagem | % |
| 15 | Volume do alto-falante | % |
| 34 | bit 2: split ligado · bit 5: tilt lateral estendido | — |

\* O campo vem em unidades de **0,5 km/h** (12000 = 60 km/h), então `/100` daria 120 para
a V11y — o app usa o **tiltback real da telemetria** (§5.1, offset 40) como referência. O
EUC World tem o mesmo defeito.

Confirmado em captura: modo (`dado[10]` bit 0) e volume (`dado[15]`). O EUC World lê os
mesmos campos com +2 de deslocamento (payload: 2, 10, 13, 15, 16, 17, 36) — as duas
convenções batem.

**Não reportado nesse quadro** (o firmware não devolve; o app mantém o último valor
conhecido): farol, DRL, brilho, ventilador, performance, low-battery, no-load,
transporte, botão de alça e lock.

### 8.2 V14 [COM]

`0` max speed /100 · `2` alarm speed /100 · `8` ajuste pedal /10° · `10` mode flags ·
`11`/`12` sensibilidade comfort/classic · `20` standby s · `30` flags A (bit0 NOT mute, bit2 DRL) ·
`31` flags B (bit2 locked, bit4 transport).

### 8.3 V12 [COM]

`9` max speed /100 · `11`/`13` alarmes 1/2 /100 · `15` pedal /10° · `17` standby ·
`19` mode flags · `22` volume · `39` flags (bit0 NOT mute, bit6 transport).

---

## 9. Limitações conhecidas do EUC World 2.66.0 (oportunidades para o app novo)

1. **[APK]** Sem handshake de auth (`0x13`) → lock pode não funcionar em V12/V13/V14.
2. **[APK]** Sem framing estendido para a maioria dos comandos → [COM] alguns comandos V12+ exigem `0x16` e podem ser descartados silenciosamente.
3. **[COM]** eucWatch relata: "stats e a maioria dos comandos de setting (luz/buzina) não funcionam" no V12 com framing default — corrobora.
4. **[COM]** Usuário em fórum: ajustes não aplicam no V14 Pro via EUC World.

**Para o app novo: implementar framing EXTENDED + handshake de auth desde o início** (referência: eucplanet, MIT).

---

## 9.1 Correção importante — buzina da V11y (bug do EUC World)

O EUC World 2.66.0 não tem caso específico para a **V11Y** no comando de buzina: ela cai no
default `60 51 18 01` (`playSound`, som 24). No firmware da V11y esse som é um **tom contínuo**
que não para sozinho — o sintoma é a roda apitando sem parar.

A referência comunitária (eucplanet, validada contra capturas do app oficial) agrupa a
**V11y com a família V13/V14**, que usa `playBeep`:

| Modelo | Payload | Efeito |
|---|---|---|
| **V11Y, V13, V14, E20** | `60 51 02 64` | bipe curto |
| V9, V11, V12, V12S | `60 51 18 01` | bipe curto |

Regra implementada em `InmotionV2Commands.hornFor(model)`.

**Lock em V12+:** o firmware ignora o lock em framing DEFAULT — é obrigatório usar
EXTENDED (`AA AA 16 05 02 21 60 31 01 CK`). Implementado em `lock(locked, extended = true)`.

## 9.2 O que a V11y NÃO suporta (evidência das telas do EUC World)

As telas de configuração do EUC World por modelo mostram o que funciona na prática:

| Opção | V11y | V13/V14 | V12 | V9/V12S |
|---|---|---|---|---|
| Tilt lateral estendido | ❌ não | ✅ | ❌ | ❌ |
| DRL + brilho + ventilador | ✅ | ❌ (usa farol automático) | ❌ | ✅ |
| Alarmes de velocidade | ❌ | ❌ | ✅ | ✅ |
| Lock | ✅ (ação rápida, não na tela) | ✅ | ✅ | ✅ |
| Farol automático (limiares) | ❌ | ✅ | ✅ | ❌ |

O app novo filtra a tela por `InmotionV2Capabilities.forModel()`, então opções não
suportadas não aparecem (evita comandos ignorados silenciosamente).

## 10. Apêndice A — V11Y: settings ↔ protocolo (mapa completo)

Tela de configurações do V11y em `res/xml/preferences_wheel_inmotion_new_v11y.xml`.
Sincronização roda→app em `si/C5600a.java:37-155` (callback key → chave de settings).

| Setting (UI) | Chave | Comando BLE (sub) | Parse de volta (offset em `m11207L`) |
|---|---|---|---|
| Luzes diurnas (DRL) | `in_daytime_running_lights` | `60 2D` (V14/V13/V11Y) | dado[34] bit 2 |
| Farol on/off | `in_headlight_v11` | `60 50` (fw≥1.4) / `60 40` (antigo) | dado[34] bit 5 |
| Brilho do farol | `in_headlight_brightness` | `60 2B <0-100>` | — |
| Ventilador | `in_fan` | `60 53` (fw≥1.4) / `60 43` | — |
| Ventilador silencioso | `in_fan_quiet_mode` | `60 38` | — |
| Modo de condução | `in_riding_mode` | `60 23 <0/1>` (framing padrão, funciona) | dado[10] bit 0 (0 = Comfort, 1 = Sport) — confirmado em captura |
| Split riding modes | `in_split_riding_modes` | `60 42` (V12) / `60 3E` | — |
| Split aceleração | `in_split_riding_modes_acceleration` | `60 3F <acc> <brk>` (V11Y) / `60 40` (V12) | — |
| Split frenagem | `in_split_riding_modes_braking` | idem | — |
| Modo performance | `in_performance_mode` | `60 24 <0/1>` | — |
| Limite de velocidade | `in_speed_limit` | `60 21 <u16LE×100>` **estendido** (`02 21 60 21 … 00 00`) | telemetria dado[40] /100 (o campo do quadro de settings vem em unidades de 0,5 km/h: 12000 = 60 km/h) |
| Sensibilidade dos pedais | `in_pedals_sensitivity` | `60 25 <v> <v>` | dado[11] |
| Tilt dos pedais | `in_pedals_tilt` | `60 22 <u16LE>` | dado[8] /100 |
| Volume do alto-falante | `in_speaker_volume` | `60 26 <0-100>` | dado[15] |
| Low-battery riding | `in_go_home` | `60 37 <0/1>` | — |
| Detecção motor sem carga | `in_motor_no_load_detection` | `60 36 <0/1>` | — |
| Modo transporte | `in_transportation_mode` | `60 32 <0/1>` | — |
| Botão de alça | `in_handle_button` | `60 2E <0/1>` | — |
| Lock | `in_lock` | `60 31 <0/1>` | — |
| Alarmes de velocidade 1/2 | `in_speed_alarm_1st/2nd` | `60 3E <u16> <u16>` | — |
| Sons do motor | `in_motor_sound` | `60 39 <0/1>` | — |
| Sensibilidade som do motor | `in_motor_sound_sensitivity` | `60 38 <0-100>` | — |
| Auto-off da tela | `in_screen_auto_off` | `60 3D <0/1>` | — |
| Standby timer | `in_standby_timer` | `60 28 <u16 seg>` | — |
| Farol automático | `in_auto_headlight` | `60 2F <0/1>` | — |
| Limiares do farol automático | `in_auto_headlight_threshold_on/off` | `60 2A <a> <b>` | — |
| Limiar low→high | `in_auto_headlight_low_to_high_threshold` | `60 30 <u16LE>` | — |
| Brilho low/high (V12) | `in_headlight_brightness_low/high` | `60 2B <low> <high>` | — |
| Limite tilt lateral estendido | `in_extended_lateral_tilt_limit` | `60 45 <0/1>` | — |

**Fluxo de estado:** ao receber a resposta `A0 20` (settings), o adaptador parseia (`m11207L` para V11Y) e
dispara callbacks por chave (ex.: `"daytimeRunningLights"`), que o app persiste em SharedPreferences
(`si/C5600a.java:37-155`). Assim a UI reflete o estado real da roda ao conectar.

## 11. Referências

| Projeto | Uso | Licença |
|---|---|---|
| [eried/eucplanet](https://github.com/eried/eucplanet) | Referência primária Kotlin; protocolo V1+V2 completo com docs | MIT |
| [Wheellog/wheellog.android](https://github.com/Wheellog/wheellog.android) | Offsets por modelo, curvas de bateria V1, CAN IDs | GPL-3.0 (⚠️ não copiar código) |
| [enaon/eucWatch](https://github.com/enaon/eucWatch) | Bytes de comando em decimal (auditoria) | sem licença (⚠️ só documentação) |
| [Laufbursche42/inmotion-unlock](https://github.com/Laufbursche42/inmotion-unlock) | Captura de tráfego BLE | PolyForm NC |

**Captura de tráfego:** habilitar "Bluetooth HCI snoop log" nas opções de desenvolvedor do Android → `btsnoop_hci.log`.
