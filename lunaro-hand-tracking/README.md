# LUNARO — Hand Tracking (Kotlin)

Módulo Android nativo (Kotlin) de rastreamento de mãos e toque direto com o indicador.

- Câmera frontal (CameraX) + MediaPipe HandLandmarker (21 landmarks por mão, tempo real).
- Mãos em estilo esqueleto: contorno preto, dedos cinza, articulações visíveis.
- Toque com o indicador: ponta do dedo (landmark 8) + profundidade relativa, histerese, filtro One Euro e cooldown.
- Interface espacial estilo Quest em paisagem: Biblioteca, Loja e Configurações rápidas em arco (perspectiva real), barra de apps inferior, fechar/minimizar janelas, tudo acionado pelo toque do indicador.
- Biblioteca abre YouTube, Netflix, Roblox e Spotify (app instalado, senão o site no navegador), além de Navegador e Ajustes do Android.
- Configurações rápidas mudam de fato o app: contorno das mãos, estilo claro (Quest), latência, espessura e opacidade das janelas.

## Compilar o APK
- **GitHub:** suba o repositório e rode a aba *Actions → Build APK*. O APK fica em *Artifacts*.
- **Local:** abra no Android Studio (JDK 17) e rode `assembleDebug`. O modelo `hand_landmarker.task` é baixado automaticamente no build.

## Limitações
- Não há 6DoF nem posição real das mãos: o z do MediaPipe é relativo e estimado por uma câmera só. Ajuste `enterDepth`/`exitDepth` em `PokeDetector` em aparelho real.
- Dentro de um VR Box a câmera do celular fica bloqueada: o toque direto exige câmera externa ou outro dispositivo que enxergue as mãos.
- Os ícones são originais (letras em blocos coloridos), sem assets de marcas. Loja e ambientes 3D são demonstrações; não há conteúdo real de loja.
- Se o arco das janelas parecer invertido, troque o sinal de `arc` em `WindowSystemView.kt`.
- Ainda não integrado à renderização estéreo/Unity do LUNARO VR.
