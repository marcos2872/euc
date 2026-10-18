# Instalação e teste no celular via ADB

Guia para instalar o app no seu celular e validar com a roda (Inmotion V11y).

## 1. Pré-requisitos

| Item | Como obter |
|---|---|
| `adb` | Já instalado: `~/.local/opt/android-sdk/platform-tools/adb` (também em `$PATH` se você exportou o SDK) |
| Celular com Android 8+ | minSdk do app é 26 |
| Depuração USB ativada | Ver passo 2 |
| Roda carregada e ligada | Para o teste de conexão |

Dica: para encurtar os comandos, use a variável abaixo no terminal:

```bash
export ADB=~/.local/opt/android-sdk/platform-tools/adb
```

## 2. Ativar depuração no celular

1. **Configurações → Sobre o telefone → Informações do software**
2. Toque **7 vezes** em "Número da versão" (aparece "Você agora é um desenvolvedor")
3. **Configurações → Sistema → Opções do desenvolvedor**:
   - Ative **Depuração USB**
   - (Opcional, para o modo sem fio) Ative **Depuração sem fio**

## 3. Instalar o app

### Opção A — via USB (mais simples)

```bash
# 1. Conecte o cabo e autorize o computador na tela do celular
$ADB devices            # deve listar o aparelho como "device" (não "unauthorized")

# 2. Compile e instale (Gradle usa o adb do SDK automaticamente)
cd ~/Projetos/euc
./gradlew :app:installDebug
```

Alternativa manual (APK já compilado):

```bash
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
```

### Opção B — sem fio (Android 11+)

```bash
# No celular: Opções do desenvolvedor → Depuração sem fio → Emparelhar com código
# O celular mostra IP:porta e um código de 6 dígitos

$ADB pair 192.168.0.10:37123        # digite o código quando pedir
$ADB connect 192.168.0.10:5555      # porta de conexão (mostrada na mesma tela)
$ADB devices                        # confirme que aparece
./gradlew :app:installDebug
```

### Opção C — sem cabo e sem depuração

Copie o APK para o celular (cabo, nuvem, `scp`...) e instale tocando no arquivo.
Será preciso permitir "instalar apps desconhecidos" para o app que abre o APK.

```bash
ls -la app/build/outputs/apk/debug/app-debug.apk   # 16 MB
```

## 4. Abrir e acompanhar os logs

```bash
# Limpa o log e abre o app
$ADB logcat -c
$ADB shell am start -n world.euc.app/.MainActivity

# Logs do app em tempo real (erros e avisos)
$ADB logcat --pid=$($ADB shell pidof -s world.euc.app) '*:W'

# Alternativa: filtrar por tag/processo
$ADB logcat | grep -iE "euc|bluetooth|gatt"
```

Para desinstalar (limpa dados e permissões):

```bash
$ADB uninstall world.euc.app
```

## 5. Roteiro de teste com a V11y

> Objetivo: validar detecção, inicialização, telemetria e comandos.

| # | Passo | Resultado esperado |
|---|---|---|
| 1 | Ligue a roda (de pé, sem carga) | Roda anunciando BLE |
| 2 | Abra o app e conceda as permissões | Botão "Buscar rodas" habilitado |
| 3 | Toque em **Buscar rodas** | A V11y aparece na lista com "Inmotion (V11 ou posterior) (HIGH)" |
| 4 | Toque na roda para conectar | Tela muda para o painel; em segundos o modelo deve virar **V11Y** |
| 5 | Confira os valores no painel | Ver tabela de validação abaixo |
| 6 | Toque em **Buzina** | A roda apita |
| 7 | Toque em **Farol** | O farol liga/desliga |
| 8 | Afaste o celular ~10 m | Painel marca "Sem dados há N s" em vermelho |
| 9 | Volte para perto | Dados voltam a atualizar |

### Validação cruzada dos valores

Compare o painel com a **tela da própria roda** (ou com o app oficial):

| Campo do painel | Onde conferir na roda | Tolerância aceitável |
|---|---|---|
| Velocidade | velocímetro da roda (parado = 0) | ±0,5 km/h |
| Bateria % | indicador da roda | ±2% |
| Tensão | tela de info (se houver) | ±0,5 V |
| Temperaturas | diagnóstico da roda | ±3 °C |
| Carga (PWM) | tiltback/limite | — |

**Se algum valor vier zerado ou absurdo**, anote: o offset pode variar por firmware e a correção é rápida (o parser está em `core/protocol/src/main/kotlin/world/euc/protocol/inmotion/InmotionV2TelemetryParser.kt`).

## 6. Diagnóstico de problemas

### Xiaomi / POCO / Redmi (MIUI / HyperOS)

| Sintoma | Causa | Solução |
|---|---|---|
| `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` ao instalar | "Instalar via USB" desligado nas opções de desenvolvedor (ou confirmação não aceita na tela) | **Opções do desenvolvedor → Instalar via USB** (pode exigir conta Mi + chip + internet). Depois repita o comando — na primeira tentativa o sistema mostra um diálogo; aceite. Se persistir, ative também **Depuração USB (Configurações de segurança)** |
| `SecurityException: grantRuntimePermission` no `adb shell pm grant` | MIUI bloqueia concessão de permissões via shell | Conceda na própria tela do app (botão "Conceder permissões de Bluetooth") ou ative **Depuração USB (Configurações de segurança)** |

### Outros

| Sintoma | Causa provável | Ação |
|---|---|---|
| A roda não aparece na busca | Permissão de Bluetooth negada, Bluetooth desligado, ou a roda já está conectada a outro app | Conceda permissões, ligue o Bluetooth, feche o app oficial da Inmotion |
| "Falha: Protocolo ainda não suportado" | A detecção escolheu outro protocolo | Anote o nome exibido e o serviço; ajuste em `WheelProtocolDetector` |
| Conecta mas não sai de "Aguardando telemetria" | Resposta de init não reconhecida | Rode `adb logcat` e procure por `CommandTimedOut`; me envie a saída |
| Valores congelados | Notificações não habilitadas (CCCD) | Desconecte/reconecte; verifique se outra instância está conectada |
| Buzina/farol sem efeito | Opcode difere por firmware | Anote a versão de firmware mostrada no painel |
| App fecha ao conectar | Exceção de permissão | `adb logcat --pid=... '*:E'` e me envie |

> **Atenção (BLE):** apenas **um** app pode estar conectado à roda por vez. Antes de testar, force a parada do app oficial:
> ```bash
> $ADB shell am force-stop net.lastowski.eucworld
> ```


## 7. Comandos úteis de referência

```bash
# Estado da conexão Bluetooth do celular
$ADB shell dumpsys bluetooth_manager | grep -iE "name|address" | head -20

# Verificar se o app tem as permissões concedidas
$ADB shell dumpsys package world.euc.app | grep -A 3 "runtime permissions"

# Reiniciar o app sem reinstalar
$ADB shell am force-stop world.euc.app && $ADB shell am start -n world.euc.app/.MainActivity

# Capturar um log completo para enviar em caso de problema
$ADB logcat -d > /tmp/euc-logcat.txt

# Passeios gravados e logs CSV (dentro do sandbox do app)
$ADB shell run-as world.euc.app ls -l files/tours files/logs
$ADB exec-out run-as world.euc.app cat files/tours/2026-10-18-143012.tour > /tmp/opencode/tour.tour
$ADB exec-out run-as world.euc.app cat files/logs/euc-log-20261018-143012.csv > /tmp/opencode/log.csv
```

### Passeios e log CSV

- Permissões: a gravação pede **localização** (GPS) e, no Android 13+, **notificações**
  (a notificação do serviço em primeiro plano).
- Com a tela apagada o app mantém um **serviço em primeiro plano** — no MIUI/HyperOS vale
  liberar "Iniciar automaticamente" e "Sem restrições" de bateria para o app, senão o
  sistema pode encerrar a gravação em segundo plano.
- O GPS precisa estar ligado; com o provedor desligado a tela avisa e o passeio fica sem
  pontos (a telemetria continua no log CSV, se estiver ligada).
- Os arquivos ficam em `files/tours` e `files/logs` (privados do app). Dá para tirar do
  celular com `run-as` (acima) ou compartilhar pelo próprio app (Sobre e diagnóstico →
  compartilhar log; detalhe do passeio → compartilhar CSV).

## 8. Referências no projeto

| Assunto | Arquivo |
|---|---|
| Protocolo Inmotion V2 (bytes, comandos, offsets) | `docs/03-protocolo-inmotion.md` |
| Plano do app e fases | `docs/06-plano-app.md` |
| Plano multi-protocolo | `docs/07-plano-multi-protocolo.md` |
| Parser de telemetria | `core/protocol/src/main/kotlin/world/euc/protocol/inmotion/InmotionV2TelemetryParser.kt` |
| Comandos | `core/protocol/src/main/kotlin/world/euc/protocol/inmotion/InmotionV2Commands.kt` |
