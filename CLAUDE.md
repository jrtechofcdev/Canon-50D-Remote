# JR C50D Sync

App Android **JR C50D Sync** (antes Jr_Link; marca Jr_Tech_OFC, Kotlin + Jetpack Compose) de captura vinculada USB para a Canon EOS 50D.
Identidade: `DESIGN.md` e `branding/` (cores, fonte Exo 2, símbolo).
Código em `app/src/main/java/com/example/`. Protocolo PTP em `core/ptp`, sessão USB em `core/camera`,
auto-save em `core/tether` + `core/storage`, presets e recorte em `core/preset` + `core/render` (GL com reserva na CPU), live view em `core/live`, telas em `ui/`.
Ícone: `python3 branding/icon.py`; banner: `python3 branding/banner.py`.

**Dois apps, um código** (variantes do Gradle, plano em `docs/PLANO_REVELA.md`):
- `jr`: JR C50D Sync, `com.jrtechofc.tether50d`, versão no `defaultConfig`. Nada muda para quem já usa.
- `revela`: **Revela**, app para outros fotógrafos (`com.jrtechofc.revela`), versão em `revelaVersionName`. Identidade "Câmara escura" (`brand/BrandStyle.kt`), logo em `branding/revela/logo.py`, telas próprias em `src/revela/java/com/example/revela/`, textos pt/en em `src/revela/res/values*/strings.xml`.
- O que é de cada marca (nome, pastas, assinatura) vem de `com.example.brand.AppBrand` (`src/jr` e `src/revela`). Nunca escrever "JR C50D Sync" ou "Jr_Tech_OFC" no `src/main`.
- O tema vem de `BrandStyle.look`: as telas compartilhadas usam `Tone`, `ReadoutStyle`, `WordmarkStyle` e `DisplayStyle` e aparecem com a cara de cada app.

- Testes: `gradle :app:testJrDebugUnitTest` (tudo) e `gradle :app:testRevelaDebugUnitTest --tests 'com.example.core.*' --tests 'com.example.revela.*'`
- Capturas de tela das telas: `gradle :app:testJrDebugUnitTest --tests com.example.ui.ScreenshotTest` → `app/build/screens/`; Revela: `--tests com.example.revela.RevelaScreensTest` → `app/build/screens/revela/`
- APK: `gradle :app:assembleJrDebug` → `app/build/outputs/apk/jr/debug/app-jr-debug.apk`; `:app:assembleRevelaDebug` → `.../revela/debug/app-revela-debug.apk` (os dois assinados com `app/signing/jrlink-debug.keystore`, mantém atualização por cima)
- Capturas para o README: rodar cada teste do `ScreenshotTest` separadamente (juntos no mesmo processo alguns não carregam), redimensionar para 540 px em `docs/images/`.
- Nova versão: `versionName` em `app/build.gradle.kts` + seção no `CHANGELOG.md` + APK em `release/`. Ao chegar na `main`, o workflow `release` cria a tag `vX.Y` e publica a Release (só se ela ainda não existir).

<!-- antislop:start -->
## antislop
For UI, copy, people, mobile layout, or code comments work, read `.claude/skills/antislop/SKILL.md` (core) and then the skill for the task:
- UI / visual: `.claude/skills/antislop-ui/SKILL.md`
- Copy & text: `.claude/skills/antislop-copywriting/SKILL.md`
- People: `.claude/skills/antislop-human/SKILL.md`
- Mobile / responsive: `.claude/skills/antislop-layoutmobile/SKILL.md`
- Code comments: `.claude/skills/antislop-code/SKILL.md`
Design direction lives in `DESIGN.md`. Taste references: `.claude/skills/taste-skill/SKILL.md`.
<!-- antislop:end -->
