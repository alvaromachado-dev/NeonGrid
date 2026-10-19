# ◢ NEONGRID

Launcher Android com visual cyberpunk: relógio com glitch, skyline neon, grid em perspectiva animado, ícones com moldura chanfrada e gaveta com busca estilo terminal.

Feito em Kotlin + Jetpack Compose. Alvo: Android 16 (API 36), funciona a partir do Android 8.

## Recursos

- **Home**: HUD com relógio, data, bateria e saudação; apps fixados em grid de 4 ou 5 colunas
- **Dock**: até 5 apps (na primeira execução já vem com telefone, mensagens, câmera e navegador)
- **Gaveta**: deslize pra cima (ou toque em `︽ APPS`); busca ignora acentos, Enter abre o primeiro resultado; arraste o topo pra baixo pra fechar
- **Menu do app** (segurar o ícone): fixar/remover da home, fixar/remover do dock, info, desinstalar
- **Config** (botão `[≡]` ou segurar o relógio): 4 cores neon, ícones originais ou modo holograma, 4/5 colunas, fundo animado ou estático

## Gerando o APK pelo GitHub (sem instalar nada)

1. Crie um repositório no GitHub (sugestão: `neongrid-launcher`) e suba todos os arquivos desta pasta
2. Vá em **Actions**. O workflow `Build APK` roda sozinho a cada push na `main` (ou clique em *Run workflow*)
3. Em uns 5 minutos, o APK aparece em **Releases** (`NeonGrid-N.apk`)
4. Abra a página de Releases no celular, baixe o APK e instale (permita "instalar apps desconhecidos" pro navegador)
5. Aperte o botão Home e escolha **NeonGrid** → *Sempre*. Ou: Configurações → Apps → Apps padrão → App de início

Cada build nova instala **por cima** da anterior (mesma chave de assinatura), mantendo apps fixados e configurações.

## Compilando no PC

Abra a pasta no Android Studio e rode, ou no terminal: `./gradlew assembleRelease` (Windows: `gradlew.bat assembleRelease`).

## Sobre a chave de assinatura

`keystore/neongrid.jks` (senha `neongrid`) existe pra que as atualizações instalem por cima. Ela é só deste projeto pessoal. Se o repositório for público, qualquer pessoa pode assinar um APK com essa chave, então só instale APKs vindos do seu próprio repositório.
