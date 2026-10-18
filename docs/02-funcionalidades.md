# EUC World 2.66.0 — Inventário de funcionalidades

> Extraído do APK (strings, preferências XML, manifest, código descompilado).
> Este é o checklist de paridade para o app pessoal modernizado.

## 1. Conexão e roda

- **Suporte multi-marca**: KingSong, Gotway/Begode, Inmotion (V1 e V11+), Ninebot (One S2/A1 e Z), Veteran, Solowheel/Rockwheel.
- **Detecção automática** de protocolo por nome BLE + serviços GATT, com cache do protocolo por dispositivo.
- Reconexão automática, keep-alive, MTU negociado, fila de comandos com timeout/retry.
- **Controle da roda** (por modelo): farol, luzes diurnas (DRL), brilho, modo de condução, modo performance,
  split riding modes (aceleração/frenagem), sensibilidade e tilt dos pedais, limite de velocidade (tiltback),
  volume do alto-falante, sons, buzina, ventilador + modo silencioso, detecção de motor sem carga,
  modo transporte, botão de alça, bloqueio/desbloqueio, calibração, LEDs (Gotway/KingSong/Ninebot), luzes de freio.
- **Alarmes da própria roda** (para modelos que suportam): alarmes de velocidade da roda, safety margin.
- **Reset de distância do usuário**, modo "go home" (bateria baixa), AVAS (sons do veículo).
- **Bateria**: nível por tensão com curvas customizáveis, tensão média por célula, resistência do circuito,
  status por pack (Inmotion 4 packs), alertas de balanceamento.

## 2. Telemetria e dashboard

**63 campos configuráveis** em colunas (EUC/GPS esquerda/direita + gauge). Lista completa:

| Categoria | Campos |
|---|---|
| **EUC** | Speed, Speed - Avg, Speed - Avg Riding, Speed - Max, Speed Limit, Acceleration, Power, Current, Current - Phase, Relative Load, Safety Margin, Voltage, Battery Level, Battery Avg Cell Voltage, Battery Circuit Resistance, Distance, Distance - Total, Distance - User, Duration, Duration - Riding, Temperature (+ Battery, CPU, IMU, Mainboard, Motor), Energy Consumption, Energy Consumption - Avg, Energy Consumed, Tilt, Roll |
| **GPS** | Speed, Speed - Avg, Speed - Avg Riding, Speed - Max, Altitude, Bearing, Distance, Duration, Duration - Riding |
| **Tour** | Distance, Duration, Duration - Riding, Speed - Avg, Speed - Avg Riding, Max Speed, Energy Consumed, Energy Consumption - Avg |
| **Radar** | Battery Level, Objects Count, Object Distance, Object Speed, Object Speed - Relative |
| **TPMS** | Pressure, Temperature |
| **Outros** | Phone • Battery Level, Watch • Battery Level, User • Heart Rate, Weather • Temperature/Apparent Temperature/Humidity/Wind Speed |

- **Gauge (velocímetro)** configurável: modo indicador de velocidade/carga/bateria, cue de aceleração, cores.
- Indicadores: bateria, temperatura, PWM/carga, corrente, tensão — com faixas de cor configuráveis.
- **Tela sempre ligada**, orientação, tema (dark/light), correções de velocidade/distância.

## 3. Alarmes (do app)

- **3 níveis de alarme de velocidade** (com prioridades e pré-alarmes).
- Alarme de **limite de velocidade** (margem configurável em relação ao tiltback).
- Alarmes de **corrente** (pico e sustentada), **temperatura**, **tensão** (subtensão/sobretensão),
  **bateria** (por nível), **pressão de pneu** (TPMS), **safety margin**, **perda de conexão**.
- Modos: somente vibração / vibração+beeps / vibração+beeps+voz.
- **Dynamic tiltback** (tiltback dinâmico configurável por faixas).

## 4. Voz e sons

- **Avisos de voz (TTS)** com mensagens customizáveis por evento; intervalos configuráveis
  (periódico, heartbeat, hora).
- **Sons customizados** por tipo de alarme (substituíveis pelo usuário).
- Spellout de números (números falados em pt-BR etc.).
- **AVAS** (sons do veículo) com sound packs baixáveis (assets + API `AvaspackList`).

## 5. Tours e navegação

- **Gravação de tour** com GPS: mapa (MapLibre), velocidade, distância, tempo, energia.
- **Mapa ao vivo** (livemap) com publicação em tempo real e auto-finish configurável.
- **Navegação por rota planejada** (rotas do euc.world): guia de rota, tolerância off-track,
  mensagens de off-track, download de tiles.
- **Heatmap** de rotas (API), weather (temperatura/vento/chuva/qualidade do ar),
  endereço reverso (reverse geocoding).
- **Fotos/vídeos do tour** (CameraActivity; adiciona mídia ao tour).
- **Sincronização com euc.world** (upload automático/manual, atraso configurável, intervalos de sync).
- **Logging CSV** automático/manual, com auto-upload.

## 6. Integrações

| Integração | O que faz |
|---|---|
| **Wear OS** | Telemetria no relógio (companion app) |
| **Pebble** | Telemetria em relógios Pebble |
| **Garmin ConnectIQ** | App para relógios Garmin |
| **Samsung Gear** | App via Accessory SDK |
| **Flic 2** | Botões físicos com 24 ações (buzina, luz, lock, música, volume, AVAS, strobe, marcar dados, iniciar/parar tour, CSV, mute, lift sensor, radar, views) e gestos |
| **Óculos ActiveLook** | HUD com 9 views configuráveis (brilho, gestos, posição) |
| **TPMS** | Sensores de pressão de pneu |
| **Radar traseiro** | Detecção de veículos atrás (bateria, objetos, distância/velocidade relativa) |
| **Controle de carregador** | Smart plugs: TP-Link HS110, Shelly (Gen1/Gen2), carregador de hardware próprio; limites de carga, timer, delay, balanceamento |
| **Web server embutido** | API HTTP + WebSocket com valores ao vivo (integração com outros apps) |
| **BLE server embutido** | App se anuncia como servidor BLE para dispositivos próximos |

## 7. Conta e nuvem (euc.world)

- Login/cadastro, nonce/sid (autenticação), reset de senha, assinatura (premium).
- Sync de tours, rides, fotos, vídeos, crash logs.
- Heatmap, weather, endereços, tilesets de mapa, activity center, heartbeat.
- **Firmware**: listas e download de firmware Gotway, KingSong, Veteran (DFU via Nordic/mcumgr).
- Verificação de atualização do app, avisos de segurança (ex.: update obrigatório do V11), suporte.

## 8. Experiência do usuário

- **Wizard de primeiro uso** (permissões: bluetooth, localização, notificações, armazenamento,
  media control, bateria, sign-in).
- Dashboard com colunas configuráveis + gauge.
- Media control (controle de música do telefone pelo app/roda).
- Camera activity (fotos/vídeos no tour), filtros de câmera.
- Tema claro/escuro, unidades (métrico/imperial), 20 idiomas.
- Notificações persistentes com telemetria; quick actions.
