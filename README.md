# Leap Motion for Processing — fork para macOS Apple Silicon (M1/M2/M3/M4)

**Fork** da biblioteca [Leap Motion for Processing](https://github.com/nok/leap-motion-processing) v2.3.1.6
de Darius Morawiec (a versão original está incluída, sem alterações, na pasta
[`leap-motion-processing-master/`](leap-motion-processing-master/)).

A biblioteca original só funcionava em Macs Intel: dependia do software **Leap Motion V2**
(daemon `leapd`, só x86_64) e de binários nativos `libLeap.dylib` / `libLeapJava.dylib`
compilados para i386/x86_64. Este fork mantém a **mesma API** (`import de.voidplus.leapmotion.*;`)
e os mesmos exemplos, mas:

* substitui o SDK V2 por uma reimplementação em Java do pacote `com.leapmotion.leap`
  (Controller, Frame, Hand, Finger, Bone, Arm, Gesture, Image, Device, ...);
* liga ao serviço de tracking actual da Ultraleap (**Hyperion 6.x / Gemini 5.x**, API LeapC)
  através de uma pequena ponte JNI nativa (`libLeapJava.dylib`, binário universal arm64 + x86_64);
* reconhece os quatro gestos (swipe, circle, key tap, screen tap) em Java, porque o
  serviço Ultraleap já não fornece gestos;
* funciona com **Processing 4.x** (pastas nativas `macos-aarch64` / `macos-x86_64`).

Autor do fork: **Tiago Martins**. Licença MIT, como a original.

---

## Índice

1. [Como funciona](#1-como-funciona)
2. [Requisitos](#2-requisitos)
3. [Instalar o driver (serviço Ultraleap)](#3-instalar-o-driver-serviço-ultraleap)
4. [Instalar a biblioteca no Processing](#4-instalar-a-biblioteca-no-processing)
5. [Utilização](#5-utilização)
6. [Diferenças em relação à biblioteca original](#6-diferenças-em-relação-à-biblioteca-original)
7. [Compilar a partir do código](#7-compilar-a-partir-do-código)
8. [Resolução de problemas](#8-resolução-de-problemas)
9. [Estrutura do repositório](#9-estrutura-do-repositório)
10. [Licença](#10-licença)

---

## 1. Como funciona

```
 Sketch Processing 4
   import de.voidplus.leapmotion.*;        (API da biblioteca original, inalterada)
          │
   de.voidplus.leapmotion.*                LeapMotionForProcessing.jar  (fork)
          │
   com.leapmotion.leap.*                   LeapJava.jar  (reimplementação Java da API V2)
          │  JNI
   libLeapJava.dylib                       ponte nativa (arm64 + x86_64), native/leapjni.c
          │  dlopen
   libLeapC.6.dylib                        SDK LeapC da Ultraleap (instalado com o driver)
          │  WebSocket 127.0.0.1
   libtrack_server                         serviço de tracking Ultraleap Hyperion 6.2
          │  USB
   Leap Motion Controller (1.ª geração, VID 0xF182) ou Leap Motion Controller 2
```

O "driver" é o software oficial **Ultraleap Hand Tracking 6.2.0** para Apple Silicon.
Ele faz o trabalho de visão por computador (câmaras IR → esqueleto das mãos). O que este
projecto acrescenta é tudo o que está acima dele: a ponte nativa, a API Java e a biblioteca
Processing. O instalador da Ultraleap não é redistribuído neste repositório; o script
`driver/install_driver.sh` descarrega-o do site oficial e verifica a assinatura
(Developer ID *ULTRALEAP LIMITED*) e a notarização Apple antes de instalar.

## 2. Requisitos

| Componente | Versão |
|---|---|
| macOS | 11 (Big Sur) ou superior, Apple Silicon ou Intel |
| Processing | 4.x (testado com 4.4.10) |
| Dispositivo | Leap Motion Controller (original) ou Leap Motion Controller 2 |
| Driver | Ultraleap Hand Tracking **6.2.0** (Hyperion) para macOS |
| Para compilar (opcional) | Xcode ou Command Line Tools, JDK 17+ |

## 3. Instalar o driver (serviço Ultraleap)

Ligue o Leap Motion a uma porta USB e corra, a partir da pasta deste projecto
(ou da pasta `driver/` dentro da biblioteca instalada):

### Opção A — instalação oficial (recomendada, pede a password de administrador)

```bash
./driver/install_driver.sh
```

O script descarrega `tracking-software-apple-silicon-6.2.0.pkg` (≈ 480 MB) para `~/Downloads`,
verifica a assinatura e abre o instalador do macOS. Fica instalado
`/Applications/Ultraleap Hand Tracking.app` e um *LaunchDaemon* que arranca o serviço
`libtrack_server` automaticamente no boot.

### Opção B — sem password de administrador

```bash
./driver/install_driver.sh --user
```

Extrai a aplicação para `~/Applications/Ultraleap Hand Tracking.app` e instala um
*LaunchAgent* do utilizador (`~/Library/LaunchAgents/com.ultraleap.tracking.service.plist`)
que arranca o serviço no login. Tudo fica dentro da pasta do utilizador.

### Verificar

```bash
./driver/leap_service.sh status      # app instalada? serviço a correr? dispositivo no USB?
./driver/bin/leapcheck 5             # liga-se ao serviço por LeapC e imprime frames durante 5 s
./driver/leap_service.sh panel       # abre o Ultraleap Control Panel (imagem das câmaras)
```

Outros comandos: `leap_service.sh start | stop | restart | log` e `driver/uninstall_driver.sh`.

## 4. Instalar a biblioteca no Processing

### A partir da pasta `dist/` já compilada

```bash
./install_library.sh
```

Copia `dist/LeapMotionForProcessing` para `~/Documents/Processing/libraries/` (ou para a
sketchbook definida nas preferências do Processing). Se existirem as pastas antigas
`LeapJava` e `LeapMotionForProcessing` da versão Intel, são movidas para `libraries/old/`,
porque teriam classes `com.leapmotion.leap.*` duplicadas e binários incompatíveis.

### Manualmente

Copie a pasta `dist/LeapMotionForProcessing` (ou descomprima `dist/LeapMotionForProcessing.zip`)
para a pasta `libraries` da sua sketchbook e reinicie o Processing.

Estrutura instalada (formato de biblioteca do Processing 4):

```
LeapMotionForProcessing/
├── library.properties
├── library/
│   ├── LeapMotionForProcessing.jar      de.voidplus.leapmotion (fork)
│   ├── LeapJava.jar                     com.leapmotion.leap (reimplementação LeapC)
│   ├── macos-aarch64/libLeapJava.dylib  ponte JNI (universal)
│   └── macos-x86_64/libLeapJava.dylib
├── examples/   LM_0_Teste, LM_1_Basics, LM_2_Gestures, LM_3_Camera_Images
├── driver/     scripts de instalação do serviço Ultraleap
├── src/        código fonte
└── reference/
```

## 5. Utilização

A API é a da biblioteca original; consulte a referência em
[`leap-motion-processing-master/README.md`](leap-motion-processing-master/README.md).
Comece pelo exemplo **LM_0_Teste** (`File > Examples > Contributed Libraries > Leap Motion for Processing`):
mostra se o serviço está ligado, o dispositivo detectado, as mãos e a taxa de frames.

```java
import de.voidplus.leapmotion.*;

LeapMotion leap;

void setup() {
  size(800, 500);
  leap = new LeapMotion(this);          // new LeapMotion(this, true) imprime diagnóstico
}

void draw() {
  background(255);
  for (Hand hand : leap.getHands()) {
    hand.draw();
    PVector pos = hand.getPosition();   // mapeado para o ecrã (ver setWorld / moveWorld)
    PVector raw = hand.getRawPosition();// milímetros, origem no dispositivo
    float grab = hand.getGrabStrength();
    for (Finger f : hand.getOutstretchedFingers()) {
      PVector tip = f.getPosition();
    }
  }
}

// callbacks opcionais
void leapOnConnect()    { println("ligado"); }
void leapOnDisconnect() { println("desligado"); }
void leapOnFrame()      { }
```

Gestos (ver LM_2_Gestures):

```java
leap = new LeapMotion(this).withGestures("swipe, circle, screen_tap, key_tap");

void leapOnSwipeGesture(SwipeGesture g, int state) { /* 1 início, 2 actualização, 3 fim */ }
void leapOnCircleGesture(CircleGesture g, int state) { }
void leapOnKeyTapGesture(KeyTapGesture g) { }
void leapOnScreenTapGesture(ScreenTapGesture g) { }
```

Imagens das câmaras (ver LM_3_Camera_Images): `leap.allowImages()` e depois
`for (Image img : leap.getImages()) image(img, 0, 0);`.

Acesso directo à API Java (sem Processing) — `LeapJava.jar` + `libLeapJava.dylib`:

```java
Controller c = new Controller(new Listener() {
  public void onFrame(Controller c) { Frame f = c.frame(); /* f.hands() ... */ }
});
```

## 6. Diferenças em relação à biblioteca original

| Tema | Original (SDK V2) | Este fork (Ultraleap LeapC) |
|---|---|---|
| Serviço de tracking | Leap Motion V2 2.3.1 (`leapd`, Intel) | Ultraleap Hyperion 6.2 / Gemini 5 (nativo arm64) |
| Binários nativos | `libLeap.dylib` + `libLeapJava.dylib` (i386/x86_64) | `libLeapJava.dylib` universal; `libLeapC` vem com o driver |
| Gestos | reconhecidos pelo serviço | reconhecidos em Java com os mesmos limiares por omissão; configuráveis com `setGesture*()` |
| Tools (canetas) | suportadas | não existem no Ultraleap: `getTools()` devolve lista vazia |
| `getSpherePosition/Radius` | calculado pelo serviço | aproximado a partir do *grab strength* (mão aberta 100 mm, fechada 30 mm) |
| `getTouchZone/Distance` | plano de toque adaptativo | aproximação: plano em z = 0, zona de *hover* de 100 mm |
| Velocidade dos dedos, posição estabilizada da ponta | fornecidas pelo serviço | calculadas em Java a partir do frame anterior |
| `setConfig()` | configuração do serviço | chaves `Gesture.*` configuram o reconhecedor; as outras são enviadas ao serviço (melhor esforço) |
| Imagens | IR 8 bits | IR 8 bits (`allow_images` já vem activo na configuração do Hyperion) |
| Processing | 3.x | 4.x (pastas `macos-aarch64` / `macos-x86_64`) |

Tudo o resto (mãos, dedos, ossos, braço, callbacks, `setWorld`, `moveWorld`, `draw()`,
dispositivos) mantém o comportamento documentado na biblioteca original.

## 7. Compilar a partir do código

```bash
./build.sh            # compila Java (JDK 17+), ponte JNI (clang), gera dist/
./install_library.sh  # instala na sketchbook
./driver/build_leapcheck.sh
```

Testes (não precisam de hardware nem do serviço Ultraleap):

```bash
./scripts/run_tests.sh        # ApiTest: frames sintéticos, gestos, listas, vectores (73 verificações)
                              # BridgeTest: libLeapJava.dylib + mock da libLeapC (50 verificações)
./scripts/run_sketch_test.sh  # sketch Processing real, via CLI, com a biblioteca instalada e o mock
```

O mock (`native/mock/mock_leapc.c`) é uma `libLeapC` falsa que emite ligação, dispositivo,
política, frames a 100 fps com uma mão cujo indicador desenha um círculo, e imagens IR.

Diagnóstico de ponta a ponta (ponte JNI + LeapC + serviço + dispositivo):

```bash
java -Djava.library.path=dist/LeapMotionForProcessing/library/macos-aarch64 \
     -cp dist/LeapMotionForProcessing/library/LeapJava.jar:build/test-classes LeapDiag 10
```

Compilar um exemplo com o CLI do Processing:

```bash
/Applications/Processing.app/Contents/MacOS/Processing cli \
    --sketch=$HOME/Documents/Processing/libraries/LeapMotionForProcessing/examples/LM_1_Basics \
    --output=/tmp/lm1 --force --build
```

## 8. Resolução de problemas

* **"Ultraleap Hand Tracking is not installed"** na consola — falta o driver: `./driver/install_driver.sh`.
* **"the Ultraleap tracking service is not running"** — `./driver/leap_service.sh start`
  (ou abra `Ultraleap Hand Tracking` em /Applications). Verifique com `leap_service.sh status`.
* **Serviço a correr mas `isConnected()` falso / sem mãos** — confirme o dispositivo no USB
  (`leap_service.sh status` mostra "Leap Motion Controller detected"); experimente outra porta
  USB ou um cabo diferente; abra o Control Panel para ver as câmaras.
* **Duplicate library / classes `com.leapmotion.leap` em conflito** — remova da pasta `libraries`
  a antiga `LeapJava` (o `install_library.sh` faz isto automaticamente).
* **Processing Intel (x86_64)** — a dylib é universal; instale o pacote
  `tracking-software-apple-intel-6.2.0.pkg` (o script escolhe automaticamente pela arquitectura).
* Mais mensagens: `new LeapMotion(this, true)` ou `-Dleap.verbose=true`.

## 9. Estrutura do repositório

```
LeepMotionDriver/
├── README.md, LICENSE.txt, library.properties
├── build.sh                      compila tudo e gera dist/
├── install_library.sh            instala em ~/Documents/Processing/libraries
├── src/com/leapmotion/leap/      reimplementação Java da API V2 sobre LeapC (+ LeapJNI, GestureRecognizer)
├── src/de/voidplus/leapmotion/   biblioteca Processing (fork; só versões/strings alteradas)
├── native/leapjni.c, LeapC.h     ponte JNI; build_native.sh (arm64 + x86_64)
├── driver/                       install_driver.sh, leap_service.sh, uninstall_driver.sh,
│                                 leapcheck.c (diagnóstico LeapC), plist do LaunchAgent
├── examples/                     LM_0_Teste (novo) + os três exemplos originais
├── test/java/, test/sketch/      ApiTest e BridgeTest (testes sem hardware), LeapDiag, sketch de teste
├── scripts/                      run_tests.sh, run_sketch_test.sh, leap_diag.sh
└── leap-motion-processing-master/  biblioteca original (referência, sem alterações)
```

## 10. Licença

MIT — ver [LICENSE.txt](LICENSE.txt). Copyright 2013-2016 Darius Morawiec (biblioteca original);
copyright 2026 Tiago Martins (fork: reimplementação LeapC, ponte JNI, scripts do driver).

O software Ultraleap Hand Tracking e o SDK LeapC são propriedade da Ultraleap Ltd. e estão
sujeitos à licença da Ultraleap; não são redistribuídos aqui (o script de instalação
descarrega-os do site oficial). O cabeçalho `native/LeapC.h` é incluído apenas para compilar
a ponte, nos termos do Ultraleap Tracking SDK Agreement.
