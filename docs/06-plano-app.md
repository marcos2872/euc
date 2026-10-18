# Plano — App pessoal "EUC" (Kotlin + Jetpack Compose)

> Base: engenharia reversa do EUC World 2.66.0 (docs 01-05). Objetivo: paridade de funcionalidades
> com UI modernizada, começando pelo **Inmotion V11y**.

## 1. Decisões de arquitetura

| Decisão | Escolha | Motivo |
|---|---|---|
| Plataforma | Android nativo, Kotlin + Jetpack Compose | BLE em primeiro plano, performance de telemetria |
| UI | Material 3 + Compose (dark/light) | Modernização pedida |
| Arquitetura | MVVM + camada de domínio pura (sem Android) | Testabilidade do parser de protocolo |
| BLE | `BluetoothGatt` direto (como o EUC World) + coroutines/Flow | Controle fino de notificações e fila de comandos |
| Persistência | Room (tours/amostras) + DataStore (settings) | Equivalente ao EUC World, moderno |
| Mapas | MapLibre (Compose) | Mesmo do original, gratuito |
| DI | Hilt | Padrão |
| Protocolo | Camada `WheelProtocol` com adaptadores por marca; Inmotion V2 primeiro | Escalável para outras marcas |
| Anti-cópia | Implementação própria a partir de fatos de protocolo; referência de código apenas de projetos MIT (eucplanet) | Evitar contaminação de licença (WheelLog é GPL) |

## 2. Estrutura de módulos

```
:app                 — UI Compose, navegação, ViewModels
:core:protocol       — framing, checksum, escape, fila de comandos, adaptadores (Inmotion V2, V1)
:core:telemetry      — modelo de valores (catálogo de ~220 slots), agregadores (min/avg/max)
:core:data           — Room (tours, amostras, eventos), DataStore, CSV logger
:core:ble            — transporte BLE genérico (scan, connect, GATT, notificações)
:feature:dashboard   — gauge + campos configuráveis
:feature:wheel       — configurações da roda (tela V11y)
:feature:alarms      — motor de alarmes + voz (TTS)
:feature:tour        — gravação, mapa, navegação
:feature:charts      — gráficos
```

## 3. Fases de implementação

### Fase 1 — MVP de telemetria (o essencial para andar)
1. Scan + conexão BLE ao V11y (Nordic UART) com reconexão automática.
2. Protocolo Inmotion V2: framing `AA AA`, escape `A5`, XOR, fila de comandos com timeout/retry.
3. Sequência de init (carType → serial → firmware → settings → poll `04`).
4. Detecção de modelo (byte 6 variant → "V11Y") + parser de telemetria `m11168M` (offsets mapeados).
5. Dashboard Compose: velocidade (gauge), bateria, tensão, corrente, potência, PWM, temperaturas,
   safety margin, distância, duração.
6. Tela de conexão com estados + log de debug (hex TX/RX, como o EUC World).
7. Testes unitários do parser com frames sintéticos.

### Fase 2 — Controle da roda (paridade com a tela V11y do EUC World)
1. Leitura de settings (`A0 20`) → parse `m11207L` → UI reflete estado real.
2. Escrita: farol+brilho, DRL, ventilador (+quiet), riding mode, split modes, performance,
   limite de velocidade, sensibilidade/tilt dos pedais, volume, low-battery, no-load, transporte,
   botão de alça, lock/unlock, buzina, calibração.
3. Implementar **framing estendido (0x16) + handshake de auth (0x13)** desde o início (o EUC World não tem — melhoria).
4. Confirmar cada comando contra a roda real (validação).

### Fase 3 — Alarmes e voz
1. Motor de alarmes: 3 níveis de velocidade + pré-alarmes, corrente, temperatura, tensão, bateria,
   safety margin, limite de velocidade (com margens), perda de conexão.
2. Modos: vibração / beeps / voz; sons customizáveis.
3. TTS com mensagens (velocidade, bateria, distância, tempo, status), intervalos configuráveis.

### Fase 4 — Tours e dados
1. Gravação de tour com GPS (Room), mapa ao vivo, estatísticas do tour.
2. CSV logging com colunas equivalentes.
3. Gráficos (velocidade/corrente/tensão/temperatura/potência no tempo).
4. Exportação/importação (JSON de tour).

### Fase 5 — Extras (opcional, conforme uso)
1. Webserver local de valores (JSON) + BLE server.
2. Wear OS companion.
3. TPMS (sensores suportados), radar Garmin Varia.
4. Controle de carregador (Shelly).
5. Sync com euc.world (se desejado) ou backend próprio.

## 4. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Escalas ambíguas no V14/V11Y (velocidade/potência) | Validar contra a tela da roda; usar o parser V11Y extraído do EUC World como referência primária |
| Comandos ignorados (framing errado) | Implementar EXTENDED + auth desde o início; logar hex e comparar |
| Firmware varia (V11y tem revisões) | Detectar versão (`INIT_FIRMWARE`) e condicionar comandos (ex.: fan `96 53` vs `96 43` em fw ≥1.4) |
| Perda de contexto entre sessões | Documentação em `docs/` + memórias do projeto + evidências em `analysis/` |

## 5. Próximo passo imediato

1. Criar o projeto Gradle (`:app` + módulos) com Compose e Hilt.
2. Implementar `:core:protocol` (framing + Inmotion V2) com testes unitários.
3. Implementar `:core:ble` (scan/connect/GATT).
4. Primeira tela: conexão + telemetria ao vivo.
