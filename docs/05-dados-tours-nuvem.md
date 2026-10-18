# EUC World 2.66.0 — Dados, tours e nuvem

> Fonte: código descompilado (`analysis/decompiled/sources/`). Entidades Room geradas aparecem como `gi.*`,
> banco em `p235db`.

## 1. Banco de dados (Room)

| Item | Valor |
|---|---|
| Arquivo | **`livemap`** |
| Versão do schema | **18** (17 migrations de 1→18) |
| Tabelas | `Tour`, `DataPoint`, `Ride`, `Picture`, `Video`, `DataLog`, `Eip` |
| Índices | `DataPoint.tourTimestamp`; únicos em `Picture(filename,tour)` e `Video(filename,tour)` |

### Tabelas principais

| Tabela | PK | Conteúdo |
|---|---|---|
| `Tour` | `timestampStart` | 75 colunas: identidade (userId, bdAddr, euc, make, model, title, info, localeId, timeZoneId), status/publish/delay, coordenadas início/fim, distância, durações, velocidades (avg/avgRiding/max), e **42 colunas `vehicle*`** (bateria min/max, corrente min/avg/max + fase, limites, distância, durações, energia, potência, RSSI, safety margin, temperaturas, tensão) |
| `DataPoint` | `gpsTimestamp` | 46 colunas: GPS (lat/lon/accuracy/altitude/bearing/distance/speed/duration), tour (distance/duration/speeds), veículo (alarms, bateria filtrada, corrente filtrada + fase, limite, distância, durações, potência, RSSI, safety margin, velocidades, temperatura, temperatura bateria/motor, tensão filtrada), phoneBattery, watchBattery, heartRate |
| `Ride` | (`userId`,`uuid`,`bdAddr`) | 71 colunas — viagem (equivalente a Tour com UUID e stats GPS) |
| `Picture` / `Video` | `id` auto | mídia do tour (uri, filename, lat/lon, timestamp, status, syncTransaction) |
| `DataLog` | `id` auto | logs CSV (uri, filename, timestamp, pending, **contentType**: 0=EUC data log, 1=charging, 2=radar, 3=event log) |
| `Eip` | `filename` | cache do conteúdo EIP |

**Status de Tour** (inferido do uso): 0=idle, 1=gravando, 2=pausado, 3=finalizado.
**Sync**: `syncTransaction`/`syncDataPoint` = 0 → sincronizado; queries de pendência usam essas colunas.

## 2. Tour — formatos

### 2.1 Upload para a nuvem (`api/request/SyncTour.java`) — formato real

POST JSON com dois blocos:

- **`tour`**: 52 campos abreviados — `i` (timestampStart ms), `t` (finish), `d` (delay), `s` (status),
  `p` (publish), `a` (autoResume), `tdi` distância km, `tua`/`tur` durações, `tpa` pausa,
  `tsa/tsr/tsx` velocidades, `euc/info/title/make/model/localeId/timeZoneId`,
  `vbx/vbm` bateria, `vca/vcn/vcx` corrente (+fase `vcpa/vcpn/vcpx`), `vclx/vcln` limite,
  `vdi/vuc/vur` distâncias/durações do veículo, `veca` consumo médio, `vpa/vpn/vpx` potência,
  `vsmx/vsmn` safety margin, `vsa/vsr/vsx` velocidades, `vslx/vsln` limite de velocidade,
  `vtebx/vtebn` temp bateria, `vtx/vtn` temp, `vtxo/vtno` temp motor, `vvx/vvn` tensão, `uhr` FC.
- **`dataPoints`**: 39 arrays paralelos — `sn, ts, gla, glo, gac, gal, gbe, gdi, gua, gur, gsp, gsa,
  gsr, gsx, gts, tdi, tua, tur, tsa, tsr, tsx, val, vbf, vcf, vcpf, vcli, vdi, vur, vpf, vsmg, vsp,
  vsli, vte, vteb, vteo, vvf, pba, wba, uhr` (null quando ausente).

Otimização: campos descritivos só são reenviados quando mudam (`SyncTour.java:1428-1470`).

### 2.2 `api/classes/Tour` (download) — 75 campos com nomes completos (= colunas da tabela Tour).

### 2.3 `json/TourJson.java` — **código morto em 2.66.0** (nenhum call-site). Formato: status, timeZoneId,
title, distance (km), duration(s), durationRiding(s), timestamps em **segundos**, coords (7 casas),
`path: [[lat, lon, gpsSpeed]]`. Útil como referência de export simples.

## 3. Logging CSV

| Item | Valor |
|---|---|
| Arquivo | `Downloads/EUC World/EUC data logs/EUC data log - <make> <model> (<BT>) - yyyy-MM-dd HHmmss.csv` |
| Separador / EOL | vírgula / **CRLF** |
| Frequência | 1 linha por frame de telemetria (broadcast `wheelDataAvailable`); novo arquivo quando muda o endereço BLE |
| Valores ausentes | campo vazio |

**Cabeçalho (46 colunas, ordem exata):**
`datetime,duration,duration_riding,distance,distance_total,speed,speed_avg,speed_avg_riding,speed_max,speed_limit,acceleration,voltage,current,current_phase,power,energy_consumption,energy_consumed,battery,battery_corrected_voltage,temp,temp_motor,temp_batt,temp_cpu,temp_imu,safety_margin,cpu_load,tilt,roll,fan,alert,alarm,gps_datetime,gps_duration,gps_duration_riding,gps_distance,gps_lat,gps_lon,gps_speed,gps_speed_avg,gps_speed_avg_riding,gps_speed_max,gps_alt,gps_bearing,gps_acc,hr,extra`

- Formatos: km `%.3f`; km/h `%.2f`; tensão `%.2f`; corrente `%.2f`; potência `%.0f`; energia `%.1f`/`%.0f`;
  bateria `%.0f`; temperaturas `%.0f`; safety margin/cpu load `%.0f`; tilt/roll `%.1f`; lat/lon `%.7f`;
  datetime `yyyy-MM-dd'T'HH:mm:ss.SSSZ`.
- Coluna `extra`: 1 par `chave=valor` por linha (fila FIFO com `euc.*`/`info.*` no início do log).
- **[INF] Bug observado:** coluna `alert` sempre vazia; `alarm` recebe o bitmask.

## 4. GPS (GpsService)

- Foreground service, provider **GPS apenas** (1 Hz, sem Fused).
- Descarta fix com accuracy ≥ 25 m; corrige relógio se offset > 24 h.
- Velocidade = `location.speed × 3.6` (km/h).
- Anti-outlier: max GPS só conta se `> 1.1 × velocidade do veículo` (veículo > 2 km/h).
- Distância/duração "riding" só contam com velocidade > 2 km/h.
- **DataPoint no banco: 1 a cada 2 s** (mesmo com GPS a 1 Hz).
- Timer 1 s: durações de tour/GPS/pausa; GPS perdido após 10 s.
- Sync periódico a cada `tourtracking_sync_interval` (default 10 s) com limpeza >30 dias.

## 5. API euc.world

| Item | Valor |
|---|---|
| Base | `https://euc.world` (dev se debugger); API em `/api/<path>` |
| Auth | `nonce` → Play Integrity (PIA token) → `sid` → `signIn` → **`Authorization: ApiKey <user.apiKey>`** |
| Envelope | `{error, msg, extMsg, data}`; `error == 0` = sucesso |

**Endpoints:** `nonce`, `sid`, `signIn`, `signUp`, `passwordReset`, `signOut`, `heartbeat`,
`syncTour`, `syncRide`, `syncPicture`, `syncVideo`, `syncCrashLog`, `syncDataLog`, `syncEip`,
`getEipData`, `checkForUpdates`, `tour`, `paths` (heatmap GeoJSON), `getAddress` (reverse geocoding),
`firmwareUpdate`, `avaspackList`.

- **Heartbeat**: 1 min se tempestade/tour/andando, senão 5 min. Devolve `user, weather, airPollution,
  thunderstorms, legalSpeedLimit` — **é daí que vem o clima** (não há endpoint /weather).
- **Mapas**: estilos `mbtiles.euc.world/app/styles/{white,bright,dark,black}/style.json`;
  radar de chuva `weather.euc.world/{z}/{x}/{y}.png`; overlays `tiles.euc.world/overlay-cycling|hiking`.
- **Heatmap**: classes existem mas sem call-site (código morto em 2.66.0).

## 6. Webserver local

| Item | Valor |
|---|---|
| Porta | `web_server_port` (default 8080) |
| Rotas HTTP | `GET /api/info`, `GET /api/values`, `GET /api/charger` |
| WebSockets | `/gauge`, `/events`, `/values`, `/map` (só PREMIUM) |
| Auth | **nenhuma** (CORS `*`) |
| Descoberta | mDNS `_http._tcp.` como "EUC World <6 chars>" |

- `GET /api/values?filter=<regex>&full=<bool>` → `{"values":[{w,v,vl,vh,s,t,a,y,l,m,r,...}]}`.
- WS `/values`: cliente assina `{"values":["id1",...]}`; push com debounce 250 ms a cada mudança.
- IDs de valores = catálogo de 223 slots (ver `analysis/06-catalogo-valores.txt`).

## 7. Lacunas

1. Semântica de parâmetros de 1 letra em respostas (`d` em `/api/tour`, `k/f/i/t` em syncTour).
2. WS `/gauge` não detalhado (existe, com `GaugeResponse/GaugeConfigResponse/GaugeFieldsResponse`).
3. `/api/charger` não decompilado (serializa o ChargerController).
4. Biblioteca do webserver não identificada (API tipo NanoHTTPD).
