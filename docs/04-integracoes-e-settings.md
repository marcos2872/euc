# EUC World 2.66.0 — Integrações e configurações

> Fonte: código descompilado em `analysis/decompiled/sources/` + preferências em `res/xml/`.
> Inventário completo de settings (50 telas, ~750 chaves): `analysis/05-prefs-completo.json`.

## 1. Wearables e acessórios

### 1.1 Wear OS — `services/WearService.java`

- Telemetria por `MessageClient.sendMessage` para o relógio, path `/data`, JSON do catálogo de campos, a cada **500 ms**.
- Outros paths: `/alarm` (limites de alarme, 2 s), `/phone` (bateria do telefone), `/sync` (config),
  `/tapresult` (resultado da ação), `/cl` (connection lost).
- Recebe do relógio: `/singletap`, `/doubletap`, `/longpress`, `/button` (executam ação configurada),
  `/sync` (refresh/página), `/watch` (bateria + frequência cardíaca), `/tour-start|pause|resume|finish`.

### 1.2 Pebble — `services/PebbleService.java`

- App UUID `185c8ae9-7e72-451a-a1c7-8f1e81df9a3d`; protocolo PebbleKit por broadcasts.
- Dicionário numérico: 0 velocidade, 1 tensão, 2 corrente/100, 4 conectado, 5 código de alarme,
  6 unidade, 7 max speed, 8 bateria, 9 distância/100, 10 energia, 12/13 temperaturas. Atualização 100 ms.

### 1.3 Samsung Gear — `services/GearService.java`

- `SAAgent` com perfil `accessoryservices.xml` (`/net/lastowski/eucworld`, canal 136).
- Mesmos paths do Wear OS, com `"path"` dentro do JSON.

### 1.4 Garmin ConnectIQ — `services/ConnectIQService.java`

- App ID `f91f9459-a2e8-4cdd-8e3d-3bda08076036`; recebe `buttonPressed`, envia `close`.
- **[INF]** Telemetria vai pelo **BLE server do telefone** (FFE0/FFE1), não pelo ConnectIQ.

### 1.5 Flic 2 — `services/Flic2Service.java`

- 2 botões simultâneos (`flic2_address[_2]`), detecção de modelo Duo, gestos opcionais.
- Eventos: single/double/click-and-hold → ações configuráveis (24 ações: buzina, luz, lock, música,
  volume, AVAS, strobe, mute, lift sensor, radar, views, marcar dados, iniciar/parar tour/CSV…).

### 1.6 TPMS — `services/TpmsService.java` + drivers

| Driver | Detecção | Decodificação |
|---|---|---|
| Michelin TPMS | manufacturer ID 2088 | pressão `u16LE/1000 − 1` bar; temp `b2 − 60`; bateria por curva 2.0–3.0 V |
| V11 TPMS Sensor | service `FBB0` + manufacturer 256 | pressão `i32LE@6/100000`; temp `i32LE@10/100`; bateria `b14`; alarmes `b15` |
| Pecham (BR) | service `27A5` / nome "BR" | CRC próprio; pressão psi→bar; temp `b7/10` |
| Pecham (VD) | nome "VD" | temp `b3 − 50`; pressão `(i16BE@4 − 100)/100` |
| Spedal | service `FBB0` + manufacturer 172 | pressão `b0×0.01 + 1.22` bar; temp `b2 − 55`; bateria `b1×0.0705` |

- Campos: `mpr` (pressão), `mte` (temperatura), `mba` (bateria), `mal` (alarmes), `mrsi` (RSSI).
- Pairing por endereço do monociclo (`tpms_sensor_id`).

### 1.7 Radar traseiro (Garmin Varia) — `services/RadarService.java`

- UUIDs: serviço `6A4E3200-667B-11E3-949A-0800200C9A66`; dados `6A4E3203` (3 B) e `6A4E3204` (9 B).
- Formato 3 B: `[id, distância(m), velocidade]`; formato 9 B: `[id, ?, lateral×0.1, distância i16×0.0125, ?, tamanho]`
  (tamanhos: 0=NONE, 1032=SMALL, 1808=MEDIUM, 2364=LARGE).
- Alertas: 4 sons (far/med/near/passing) com volume por distância e pitch pela velocidade relativa;
  balanço estéreo por `atan2(lateral×4, distância)`.
- Campos: `roc` (nº objetos), `rod` (distância), `ros`/`rosr` (velocidade).

### 1.8 Óculos ActiveLook — `services/SmartglassesService.java`

- 9 vistas: 0-7 = grade 2×4 de campos (catálogo de valores), vista 8 = radar/mapa bitmap (MONO_4BPP_HEATSHRINK).
- Overlays: bateria dos óculos, relógio piscando, indicadores de radar/GPS/logging.
- Layout enviado do asset binário `assets/config.activelook`.

### 1.9 AVAS — `services/AvasService.java` + `utils/AVASPack.java`

- 7 categorias de som: SPEED, BRAKE, CURRENT, TEMPERATURE, IDLING, STARTUP, TURNOFF.
- Pitch/volume interpolados (mín/máx) a cada 100 ms; silencia em chamadas e `avas_mute_when_charging`.
- Sound packs `.avaspack` = ZIP com `config.json` + sons; instalação em `filesDir/avas/`.
- Pack padrão em `assets/default.avaspack`.

### 1.10 Voz e sons — `services/SpeechSoundsService.java`

- TTS do sistema ou Google TTS; pitch/rate 0.75/1.0/1.5.
- 34 mensagens faladas configuráveis (velocidade, distância, bateria, temperatura, tempo, status de tour…).
- Earcons (mp3) para alarmes; mensagens de hora, amanhecer/anoitecer, vento, avisos meteorológicos.
- Spellout de números (19 idiomas, `res/raw/spellout.json`, ICU MessageFormat).

### 1.11 Servidores embutidos

| Servidor | Detalhe |
|---|---|
| **Web server** | `web_server_enabled`, porta 8080; `GET /api/values?filter=<regex>&full=<bool>` → JSON; WebSocket equivalente; UI em `assets/webroot/` |
| **BLE server** | `ble_server_enabled`; anuncia "EUC World <6 chars do ANDROID_ID>"; GATT `180A` + `180F` + `FFE0/FFE1` com stream binário de telemetria |

### 1.12 Controle de mídia — `services/MediaControlService.java`

- `NotificationListenerService` + `MediaSessionManager`; broadcasts `mediaControlPlay/Pause/PlayOrPause/Next/Previous`.

## 2. ChargerController — controle de carregamento

### 2.1 Dispositivos suportados

| Valor | Driver | Transporte | Descoberta |
|---|---|---|---|
| `shelly` (default) | Gen1 + Gen2 | HTTP local | mDNS/NSD `_http._tcp.` (nome contém "shelly"); fallback `192.168.33.1` |
| `hw_charger` | hardware próprio | BLE GATT (`FFE1` write, `FFE2` notify) | scan por service UUID |
| outros (ex. TP-Link) | Kasa HS110 | UDP broadcast :9999 | protocolo TP-Link (JSON XOR 0xAB) |

### 2.2 Endpoints Shelly

`GET /shelly` (detecção), `GET relay/0?turn=on|off`, `GET /status` (Gen1: meters/relays/temperature),
`GET /rpc` + `Switch.GetStatus` (Gen2: output/apower/voltage/current/aenergy.total/temperature).

### 2.3 Lógica de carga (máquina de estados)

`OFF → WAITING → PRECHECK → CHARGING → BALANCING → FINISHED` (+ estados ABORTED_*).

- **PRECHECK**: valida tensão de saída do carregador contra tensão-alvo do modelo (até 3 tentativas).
- **Proteções**: sobretensão da bateria (10 amostras), temperatura da bateria (<0 °C ou >45 °C),
  timer (`charging_timer`), sem carga (corrente < limite).
- **Fim**: `charging_battery_level_limit` (% , default 100) ou `charging_avg_cell_voltage_limit`
  (default 4.20 V/célula) → BALANCING por `charging_balancing_time` (default 60 min) → FINISHED.
- `charging_disconnect_on_finish` (default true) desconecta o monociclo ao terminar.

### 2.4 Tabela de tensão-alvo por modelo

Inclui V11=100.8 V, V13=126.0 V, E20=54.6 V, além de KingSong/Gotway/Veteran/Ninebot.

## 3. Inventário de configurações (resumo por tela)

> 50 telas em `res/xml/preferences*.xml`. Detalhe completo em `analysis/05-prefs-completo.json`.

| Tela | Destaques |
|---|---|
| **General** | idioma, tema, orientação, unidades (mi/ft/°F/inHg/PSI), nível de bateria + curvas custom, correções de velocidade/distância, web/BLE server, scan de dispositivos conhecidos, camera API |
| **Gauge** | max speed, modos de indicador (velocidade/bateria/carga), cue de aceleração, 12 campos em 4 grupos, ações de toque (single/double/hold), horn mode |
| **Alarms** | 3 níveis de velocidade + pré-alarmes, over/undervoltage, corrente (pico/sustentada), temperatura (70), pressão de pneu, safety margin (20), alarme de limite de velocidade + margem, usar alarmes da roda |
| **Sounds & Speech** | 33 chaves: volume, engine, rate/pitch, mensagens (34 tipos), intervalos, hora, vento, avisos de tempo, rota, heartbeat, sons custom por alarme (34 pares) |
| **Tour** | livemap publish/auto-start/finish, delays, sync interval/WiFi-only, HQ video, track guiding (tolerância off-track, ponto de partida) |
| **Smartwatch** | enable/power save/HRM, ações de botão/single/double/hold, horn |
| **Smartglasses** | 9 vistas (0-7 com 8 campos cada; 8 = radar/mapa), refresh, brilho, gestos, deslocamentos |
| **Flic** | setup/forget, ações por botão (2 botões, Duo com 2 "sides"), gestos, horn |
| **AVAS** | enable, mute when charging, download, por som: enable/som/min/max/vol min/vol max |
| **TPMS** | enable, pairing, alertas, pressão/temperatura/bateria/RSSI |
| **Radar** | enable, scan, volume, dados do dispositivo, log CSV |
| **Charging** | enable, dispositivo (shelly/hw_charger), proteções (tensão carregador/bateria, temperatura), limites (% e V/célula), timer, delay, balancing, disconnect on finish, params do HW charger |
| **Logs** | auto log, radar log, charging log, WiFi only |
| **Wheel (por modelo)** | Inmotion V11Y: DRL, farol+brilho, ventilador+quiet, riding mode, split modes (50/50), performance, limite 5-50 km/h, sensibilidade 100, tilt ±10°, volume 20, low-battery riding, no-load detection, transporte, botão de alça, reset distância |
| **Support** | FAQ, blog, YouTube, remoção de conta, reportar problema |

## 4. UI — telas e componentes

### 4.1 Activities

| Activity | Papel |
|---|---|
| `MainActivity` (4.274 linhas) | Dashboard: 5 páginas em `LockableViewPager` + drawer de settings |
| `StartActivity` | Wizard de primeiro uso (BT, localização, notificações, storage, conta, BMS, media) |
| `UpgradeActivity` | Upgrade/premium |
| `CameraActivity` | Câmera para vídeo do tour |

### 4.2 Páginas do dashboard

| # | Página | Conteúdo |
|---|---|---|
| 0 | **Gauge** | `DashboardGauge` (canvas custom, 4.316 linhas): arco de velocidade com ticks, marcador de limite, barra de energia, ícones de bateria/falha, campos de informação, safety margin, gestos |
| 1 | **Stats** | ~100 linhas título/valor (live + min/avg/max + "health": hall/IMU/motor/overload/temperatura/CPU/serial) |
| 2 | **Chart** | `SimpleChart` — gráfico temporal |
| 3 | **Tour** | mapa MapLibre + bottom sheets (mapa, navegação, weather, heatmap, tour) |
| 4 | **Charger** | valores do carregador (potência, fator de potência, eficiência, energia, duração) + BMS |

### 4.3 Componentes próprios (`net.lastowski.common.view/`)

- **DashboardGauge** — velocímetro custom (canvas)
- **BmsView** — visualização do BMS (células/tensões, layouts `bms_*`, `battery_*`)
- **SimpleChart** — gráficos de séries temporais
- **StatusBar** — barra de estado inferior

### 4.4 Preferências

- Widgets custom `Upgrade*` (switch, list, seekbar com diálogo, multiselect, toggle group…).
- Diálogos: flic2 setup, password/calibração KingSong/Gotway, seleção de modelo, baterias custom, cores, avaspack list, scan de carregador.
- Menu: General, Wheel (por modelo), Gauge, Alarms, Tour, Sounds & Speech, Smartwatch, Smartglasses,
  Flic, AVAS, TPMS, Radar, Charging, Support + conta/app.
