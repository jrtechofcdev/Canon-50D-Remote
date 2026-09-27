# Jr_Link

App Android **Jr_Link** (marca Jr_Tech_OFC, Kotlin + Jetpack Compose) de captura vinculada USB para a Canon EOS 50D.
Identidade: `DESIGN.md` e `branding/` (cores, fonte Exo 2, símbolo).
Código em `app/src/main/java/com/example/`. Protocolo PTP em `core/ptp`, sessão USB em `core/camera`,
auto-save em `core/tether` + `core/storage`, telas em `ui/`.

- Testes: `gradle :app:testDebugUnitTest`
- Capturas de tela das telas: `gradle :app:testDebugUnitTest --tests com.example.ui.ScreenshotTest` → `app/build/screens/`
- APK: `gradle :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

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
