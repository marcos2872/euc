# euc

App Android (Kotlin + Jetpack Compose) para acompanhar um monociclo elétrico em tempo
real: telemetria, alarmes com voz, configuração da roda e gravação de passeios com mapa.
Desenvolvido para a **Inmotion V11y**, com arquitetura multi-protocolo (KingSong,
Begode/Gotway, LeaperKim, Nosfet e Ninebot documentados para depois).

> Projeto pessoal. Os APKs de terceiros usados na engenharia reversa (`binario/`) e os
> artefatos de análise (`analysis/`) **não** fazem parte do repositório.

## Módulos

| Módulo | O que é |
|---|---|
| `:core:protocol` | Núcleo puro (Kotlin/JVM, sem Android): framing, comandos, parsers de telemetria/settings/estatísticas, motor de alarmes, detecção de protocolo |
| `:core:ble` | Transporte Bluetooth LE (Android): scan, conexão, notificações, escrita sequenciada |
| `:app` | App Compose: painel com velocímetro, alarmes, ajustes da roda, passeios com GPS/mapa, exportação CSV/GPX |

## Como rodar

```bash
./gradlew :core:protocol:test :app:test   # testes (JVM, sem aparelho)
./gradlew :app:assembleDebug              # APK de debug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requisitos: JDK 17+, Android SDK com `platforms;android-37.2` e `build-tools;36.0.0`
(ver `docs/08-instalacao-e-teste-adb.md`).

### Modo debug (útil porque o MIUI bloqueia `adb shell input`)

```bash
# abre uma tela específica
adb shell am start -S -n world.euc.app/.MainActivity --es screen <connect|dashboard|tours|alarms|settings|about>
# abre um passeio
adb shell am start -S -n world.euc.app/.MainActivity --es screen tours --es tour <id>
# telemetria sintética (valida o painel sem a roda)
adb shell am start -S -n world.euc.app/.MainActivity --es screen dashboard --ez demo true
```

## Documentação

| Doc | Assunto |
|---|---|
| [01](docs/01-visao-geral-e-arquitetura.md) | Visão geral e arquitetura |
| [02](docs/02-funcionalidades.md) | Funcionalidades |
| [03](docs/03-protocolo-inmotion.md) | Protocolo Inmotion (framing, telemetria, comandos, auth) |
| [04](docs/04-integracoes-e-settings.md) | Integrações e mapa de settings |
| [05](docs/05-dados-tours-nuvem.md) | Passeios: formato `.tour`, CSV, GPS, mapa |
| [06](docs/06-plano-app.md) | Plano do app e fases |
| [07](docs/07-plano-multi-protocolo.md) | Plano multi-protocolo |
| [08](docs/08-instalacao-e-teste-adb.md) | Instalação e teste por ADB |
| [09](docs/09-pendencias-inmotion.md) | **Pendências**: correções e features novas do protocolo Inmotion |
| [protocolos/](docs/protocolos/) | Extração dos apps oficiais por marca (Inmotion, KingSong, Begode, LeaperKim, Nosfet) |

## Estado

**Funcionando e validado na V11y:** conexão, telemetria, alarmes (voz/vibração), limite de
velocidade (tiltback em framing estendido), modo de condução, buzina, volume,
sensibilidade, passeios com GPS/mapa, exportação CSV/GPX, log CSV, painel com gráficos.

**Pendências conhecidas:** ver [`docs/09-pendencias-inmotion.md`](docs/09-pendencias-inmotion.md)
(correções no protocolo, BMS por célula, itens que precisam de captura BLE).
