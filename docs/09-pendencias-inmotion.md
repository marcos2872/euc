# 09 — Pendências do protocolo Inmotion

> **Origem:** validação cruzada (out/2026) entre o que o app **implementa**
> (`core/protocol/src/main/kotlin/world/euc/protocol/inmotion/`), o que está
> documentado no [`docs/03-protocolo-inmotion.md`](03-protocolo-inmotion.md) e o que
> o **app oficial 8.1.34** expõe ([`docs/protocolos/inmotion.md`](protocolos/inmotion.md)).
>
> Cada item traz evidência (`arquivo:linha`) e prioridade. Marcar `[x]` ao concluir.
> **Regra:** nada aqui deve ser implementado sem captura/teste na roda — a coluna
> “o que já funciona” existe para não regredir o que está validado.

---

## 0. O que já está validado (não mexer)

| Item | Bytes no app | Fonte que confirma | Validado na roda? |
|---|---|---|---|
| Framing `AA AA <tipo> <len> <payload> <xor>` + escape `A5` | `InmotionV2Framing.kt` | doc oficial §4.1 | ✅ |
| Tipos `0x11` INITIAL · `0x13` AUTH · `0x14` DEFAULT · `0x16` EXTENDED | `InmotionV2Framing.kt:15` | doc oficial §4.1 | ✅ |
| Init `02 01` → `02 02` → `02 06` → `20 20` → `04` | `InmotionV2Commands.kt:51-63` | doc 03 §3 | ✅ |
| Tiltback `02 21 60 21 <u16 LE×100> 00 00` (extra) | `InmotionV2Commands.kt:135` | doc oficial §8.1 | ✅ (30→35→60, 59→59,00) |
| Lock `60 31 <0/1>` (extra) | `InmotionV2Commands.kt:84` | doc oficial §8.3 | ⏳ auth não confirmada (ver 1.3) |
| Buzina por modelo: V11Y/V13/V14 → `51 02 64`; V9/V11/V12/V12S → `51 18 01` | `InmotionV2Commands.kt:117` | doc oficial §8.2 | ✅ |
| Ventilador `53` (fw≥1.4) / `43`; silencioso `38` | `InmotionV2Commands.kt:200` | doc oficial §8.5 | ⏳ |
| Calibração `52 01 00 01` (yaw) / `52 01 01 00` (pedal) | `InmotionV2Commands.kt:256` | doc oficial §8.5 | ⏳ |
| Alarmes de velocidade `3E <u16> <u16>` | `InmotionV2Commands.kt:250` | doc oficial §8.1 | ⏳ |
| Tilt lateral estendido `45` | `InmotionV2Commands.kt:243` | doc oficial §9.1 | ⏳ |
| Odômetros `10 00 01`→`0x90`@23 · `11`→`0x91`@1/@5 | `InmotionV2StatisticsParser.kt:66-68` | doc 03 §6.1 | ✅ |
| Modo `23`, volume `26`, sensibilidade `25`, performance `24`, DRL `2D`, brilho `2B`, no-load `36`, bateria baixa `37`, auto-off `3D`, standby `28`, transporte `32` | `InmotionV2Commands.kt:151-192` | doc 03 §6 | ✅ (modo/volume/sensibilidade) |
| Telemetria: tensão@0, corrente@2, velocidade@8, carga@14, pitch@20, roll@22, bateria@34, limite@40, limite de corrente@48, temps@58-63 (tudo `/100`, temps `u8-40`) | `InmotionV2TelemetryParser.kt:114-126` | app validado | ✅ |
| Settings: limite@0, tilt@8, modo bit0@10, sensibilidade@11, split acel@13/brk@14, volume@15, flags@34 | `InmotionV2SettingsParser.kt:73-82` | app validado | ✅ |

---

## 1. Correções no app (código)

### 1.1 🔴 Modos separados usam o opcode dos alarmes de velocidade

- **Evidência:** `InmotionV2Commands.kt:224` (`splitRidingModes` → `0x3E`, V11y) **e**
  `:251` (`speedAlarms` → `0x3E <u16> <u16>`) — o mesmo opcode para duas coisas.
- **O app oficial diz:** `0x3E` = `genSetWarningLimitSpeedMsg` (alarmes 1/2), e os modos
  separados têm outros geradores (`genSpeedingBrakingOnOffMsg`, `genAccForceMsg`,
  `genBrakeForceMsg`) — ver doc oficial §8.1.
- **Risco:** o botão "Ativar" dos modos separados provavelmente **não faz nada** (ou
  escreve um payload de 1 byte num comando que espera 4).
- **Ação:** capturar o app oficial (§4.1) e descobrir o opcode real. Enquanto isso,
  esconder o cartão "Modos separados" da UI (tirar `WheelSettingId.SPLIT_RIDING_MODES`
  das capacidades da V11y) ou marcá-lo como experimental.
- [ ] capturar
- [ ] corrigir o opcode (ou esconder da UI até corrigir)

### 1.2 🟠 Inversões não confirmadas: `0x2C` (som/mute) e `0x2E` (botão da alça)

- **Evidência:** `InmotionV2Commands.kt:177` e `:183` mandam `if (on) 1 else 0`.
- **Doc 03 §6** anota "invertido no fio" para os dois (o EUC World inverte o valor).
- **Ação:** testar na roda — o botão da alça é o teste mais fácil (ligar/desligar e ver
  se o sensor passa a cortar o motor ao levantar a roda).
- [ ] testar botão da alça
- [ ] testar mute (se a UI expuser)

### 1.3 🟠 Autenticação / lock sem validação

- **O que está certo:** o handshake do doc 03 §4 está implementado corretamente
  (`InmotionV2Commands.kt:268-278`: `13 04 02 00 00 02` → `13 14 02 00 00 82 <blob>`).
- **O que falta:** confirmar na V11y que o lock realmente engata depois do handshake
  (a roda precisa estar **parada e apoiada**).
- [ ] testar lock com a roda parada
- [ ] capturar o handshake do app oficial e comparar byte a byte

### 1.4 🟡 Comandos implementados mas sem uso/validação

- `sounds(0x2C)`, `standbyTimer(0x28)`, `motorSound(0x39)`, `screenAutoOff(0x3D)`,
  `splitRidingModesSettings(0x3F/0x40)` — existem no código, mas não estão expostos na
  UI da V11y (ou não foram testados). Decidir: expor (com validação) ou remover.
- [ ] revisar

---

## 2. Correções na documentação

- [ ] **2.1** `docs/03` §6: trocar a notação `96` por `60` nos payloads de **escrita**
      (`0x96 = 0x80 | 0x16` é o opcode de **resposta**; `0x60` é o de comando — confirmado
      no app oficial §4.3 e no nosso app).
- [ ] **2.2** `docs/03` §9.1: corrigir o exemplo do tiltback — o valor é `km/h × 100`
      (30 km/h = `B8 0B`), não `30 00`. O app validou `59 → 59,00`.
- [ ] **2.3** `docs/03` §5: separar a tabela de telemetria **por variante** (V11 fw<1.4 ×
      V11y/V12) e registrar os offsets validados do app (seção 0 acima). Hoje a tabela
      mistura as duas e não diz qual é qual.
- [ ] **2.4** Adicionar a **tabela de settings** validada (offsets da seção 0).
- [ ] **2.5** `docs/protocolos/inmotion.md` §11: marcar as lacunas conforme forem
      fechadas pela captura.

---

## 3. Funcionalidades novas (que o app oficial tem)

### 3.1 🔴 BMS por célula na V11y (prioridade alta — a roda expõe)

- O app oficial tem módulo próprio (`v11_bms_info`) com **tensões de célula, log e
  proteções**; o nosso só lê `05` (bateria em tempo real).
- **Ação:** ler as tensões de célula (rotas `02 24/25/26/27` — doc 03 §7), guardar
  min/máx/delta e mostrar numa tela de bateria (com alarme de desequilíbrio).
- **Modelos:** V11/V11y, V13, V14, V18, X1, P6, E25, LE1, LR1 (`FullBMSFeatureCmdTrait`).
- [ ] capturar as respostas de célula na V11y
- [ ] parser + modelo (`WheelBms`) com testes
- [ ] UI (tela/expansão no painel) + alarme de delta de célula

### 3.2 🟠 Extras de identificação na tela "Sobre"

- Comandos do app oficial: `genGetDeviceInfoMsg`, `genGetMotorSnMsg`,
  `genGetBatterySnMsg`, `genGetHwVersionMsg`, `genGetActivateDateMsg` (§3.2).
- **Ação:** ler na conexão e mostrar (SN do motor, SN da bateria, HW, data de ativação).
- [ ] capturar os bytes
- [ ] implementar leitura + exibição

### 3.3 🟠 Alarmes/proteções do BMS na UI

- O oficial tem **23 proteções + 9 avisos + 13 erros de HW** (doc oficial §6.3).
- **Ação:** mapear os bits para o motor de alarmes do app (aviso na tela + voz).
- [ ] depende de 3.1

### 3.4 🟡 Por modelo (só se aparecer uma roda dessas)

TPMS, efeitos de luz, sound packs, cruise, one-pedal, HAC/HDC, spin-kill, assist
balance, berm angle, auto-lock, lost mode, factory reset, ativação, limites de carga,
OTA e sistema de arquivos — inventário completo no doc oficial §8. Nada disso é útil
para a V11y hoje.

---

## 4. Como validar

### 4.1 Captura BLE do app oficial (fecha a maioria das lacunas)

1. No Android: *Opções de desenvolvedor* → **"Ativar registro de HCI snoop"** +
   "arquivo de log Bluetooth".
2. Abrir o **app oficial**, conectar na V11y e exercitar, um de cada vez:
   - modos separados (ligar/desligar + sliders de aceleração/frenagem) → **1.1**
   - som/mute e botão da alça → **1.2**
   - lock (roda parada e apoiada) → **1.3**
   - calibrar giroscópio/pedais
   - abrir a tela de bateria (células) → **3.1**
3. `adb bugreport` → `btsnoop_hci.log` → **Wireshark** (filtro `btatt`) → exportar os
   writes/notifies em hex.
4. Anotar no `docs/03` e no `docs/protocolos/inmotion.md` o que for confirmado.

### 4.2 Teste direto na roda (sem captura)

| Comando | Como testar | Esperado |
|---|---|---|
| Botão da alça | alternar e levantar a roda pelo botão | motor desliga (ligado) / não desliga (desligado) |
| Modos separados | alternar "Ativar" e mexer nos sliders | resposta de aceleração/frenagem muda |
| Lock | ativar com a roda apoiada | motor travado + alarme ao mover |
| Calibração | calibrar giroscópio na horizontal | nível corrigido |
| Ventilador | ligar/desligar e ouvir | ventoinha liga/desliga |

---

## 5. Ordem sugerida

1. **Captura (§4.1)** — destrava 1.1, 1.2, 1.3 e 3.1
2. **Correções nos docs (§2)** — barato, evita repetir erro
3. **BMS por célula (§3.1)** — maior ganho de funcionalidade para a V11y
4. **Extras de identificação (§3.2)**
5. **Alarmes de BMS (§3.3)**

---

## 6. Referências

- [`docs/03-protocolo-inmotion.md`](03-protocolo-inmotion.md) — protocolo V1/V2, framing,
  init, auth, telemetria, odômetros, comandos e mapa de settings da V11y.
- [`docs/protocolos/inmotion.md`](protocolos/inmotion.md) — extração do app oficial
  8.1.34 (231 geradores de comando, BMS, modelos, lacunas §11).
- [`docs/07-plano-multi-protocolo.md`](07-plano-multi-protocolo.md) — arquitetura
  multi-protocolo e tabela de detecção BLE.
- Código: `core/protocol/src/main/kotlin/world/euc/protocol/inmotion/`.
