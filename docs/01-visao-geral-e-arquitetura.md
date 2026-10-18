# EUC World 2.66.0 — Visão geral e arquitetura

> Análise por engenharia reversa do APK oficial (`net.lastowski.eucworld`, v2.66.0, sha256 `5af0c929…f9a7`),
> descompilado com JADX 1.5.6 (4.161 classes, 67 falhas de decompilação) + apktool 2.11.1 para recursos.
> Alvo de referência para o app pessoal modernizado.

## 1. Identidade

| Item | Valor |
|---|---|
| Pacote | `net.lastowski.eucworld` |
| Autor | Sebastian Łastowski |
| Versão analisada | 2.66.0 (versionCode 2020660000) |
| minSdk / targetSdk | 23 / 35 |
| Classes | 6.800 (a maioria ofuscada por R8) |
| Recursos | 6.139 |
| Libs nativas | `libsentry.so` (crash reporting) |
| Distribuição | Google Play (com verificação de licença `com.android.vending.CHECK_LICENSE`) |

## 2. Stack técnica observada

| Área | Tecnologia |
|---|---|
| Linguagem | Kotlin + Java (app legado, views XML) |
| UI | AndroidX + Material Components (views), ButterKnife (view binding antigo), preferences XML |
| BLE | Android BluetoothGatt direto (sem lib externa no core); Nordic lib (`no.nordicsemi`) para DFU; `mcumgr` (`io.runtime.mcumgr`) |
| Rede | OkHttp + Retrofit2 + Jackson |
| Banco | Room (`net.lastowski.eucworld.db`) |
| Mapas | MapLibre (`org.maplibre`) |
| Imagens | Glide/Fresco (`com.facebook`) |
| Crash/analytics | Sentry (`io.sentry`), Firebase (Crashlytics/Analytics/Messaging) |
| Watch/vestíveis | Wear OS (Play Services Wearable), Samsung Accessory SDK, Garmin ConnectIQ SDK, Pebble |
| Óculos inteligentes | ActiveLook (`com.activelook`) |
| Serialização | kotlinx.serialization (Settings) + org.parceler |
| Servidor local | Jetty/OkHttp webserver embutido (HTTP + WebSocket) |
| Ads/billing | Google Ads + Play Billing (verificação de licença) |

## 3. Estrutura de classes (parte não ofuscada)

```
net.lastowski.eucworld
├── EucWorld                 # Application
├── Eip / EipData            # conteúdo remoto "EIP" (avisos/informações)
├── Settings                 # configurações persistidas (kotlinx.serialization)
├── activities/              # MainActivity, StartActivity, UpgradeActivity, CameraActivity
├── api/                     # cliente euc.world (classes, request, response)
├── chargercontroller/       # controle de carregador (Shelly etc.)
├── db/                      # Room (Db + entities/DAOs)
├── dialogs/adapters/        # adaptadores de diálogo (lista de rodas, firmwares)
├── json/                    # TourJson, PlannedRouteJson
├── receivers/               # BootReceiver, SyncReceiver
├── services/                # 15 serviços (ver abaixo)
├── utils/                   # AVASPack, SupportRequest, spellout (números falados)
├── views/                   # views customizadas (preferências "Upgrade*")
└── webserver/               # handlers HTTP + WS de valores ao vivo
```

**Ofuscação:** o núcleo (protocolos, telemetria, UI) está em pacotes obfuscados (`p364uh`, `p296qh`, `p133hi`, `p415xh`…).
O adaptador Inmotion V2 é `p364uh/C5937z.java` (4.223 linhas); o modelo de valores é `p296qh/C5206j.java`.

## 4. Serviços (manifest)

| Serviço | Função |
|---|---|
| `BleService` | Conexão BLE, descoberta GATT, transporte por protocolo |
| `GpsService` | Localização, track de tours |
| `DataLoggingService` | Logging CSV dos valores |
| `SpeechSoundsService` | Avisos de voz (TTS) + sons |
| `AvasService` | AVAS (sons do veículo) |
| `WearService` | Wear OS (data layer) |
| `PebbleService` | Relógios Pebble |
| `GearService` | Samsung Gear (Accessory SDK) |
| `ConnectIQService` | Garmin ConnectIQ |
| `Flic2Service` | Botões Flic 2 (ações remotas) |
| `TpmsService` | Sensores de pressão de pneu |
| `RadarService` | Radar traseiro (aviso de veículos) |
| `SmartglassesService` | Óculos ActiveLook (HUD) |
| `MediaControlService` | Controle de mídia do telefone |
| `MediaControlService`/`SyncReceiver` | Sincronização com euc.world |

## 5. Protocolos de roda suportados

Enum `p364uh/AbstractC5919h0.java:175-182`: `GOTWAY, INMOTION, INMOTION_NEW, KINGSONG, NINEBOT, NINEBOT_NEW, VETERAN`.

| Protocolo | Classe | Transporte BLE |
|---|---|---|
| KingSong | `C5909c0` | `FFE0/FFE1` |
| Gotway/Begode | `C5933v` | `FFE0/FFE1` + `FFE5/FFE9` chunked |
| Veteran | `C5917g0` | `FFE0/FFE1` + `FFE5/FFE9` |
| Ninebot (One S2/A1) | `C5911d0` | `FFE0/FFE1` |
| Ninebot (Z) | `C5915f0` | Nordic UART |
| **Inmotion (pré-V11)** | `C5935x` | `FFE5/FFE9` write, `FFE4` notify |
| **Inmotion (V11+)** | `C5937z` | Nordic UART `6e400002/3` |

Detalhes do protocolo Inmotion: ver `docs/03-protocolo-inmotion.md`.

## 6. Fluxo BLE (visão geral)

1. `BleService` escaneia dispositivos; identifica o protocolo pelo **nome anunciado + serviços GATT** (mensagens "BLE device recognized as …" no log).
2. Conecta, descobre serviços, seleciona características de escrita/notificação conforme o protocolo (switch em `si/C5600a.java`).
3. Ativa notificações (CCCD) e roda a sequência de init do adaptador.
4. Recebe notificações → `BleService.m8192e` → roteia para o adaptador (`C5937z.mo11015h` etc.).
5. Adaptador parseia, atualiza campos estáticos → `C5206j` propaga para o modelo de valores (`C5197a`) → UI, alarmes, log, webserver, vestíveis.

## 7. Limitações da análise

- O código ofuscado perdeu nomes; a semântica de alguns campos internos foi recuperada por uso (UI/settings/alarmes), o resto permanece inferido.
- 67 classes falharam ao descompilar (smali disponível, não analisadas em detalhe).
- Nada foi executado em dispositivo: todas as conclusões são estáticas (shipped-artifact).
- A análise de runtime (captura BLE real com a roda) fica como próximo passo de validação.
