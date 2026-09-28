# Marca Jr_Tech_OFC no app

Fonte da verdade da identidade usada no JR C50D Sync.

| Item | Valor |
|---|---|
| Verde menta | `#00FCA8` |
| Ciano | `#1CDAFF` |
| Grafite | `#292929` |
| Fonte | Exo 2 (SIL OFL, `app/src/main/assets/licenses/Exo2-OFL.txt`) |
| Símbolo | 4 blocos inclinados ~16°, degradê menta → ciano na diagonal |

Scripts (rodar na raiz do repositório, precisam de `pip install pillow`):

- `python3 branding/symbol.py`: gera os vetores do símbolo da marca (`ic_brand_symbol` e ícone de notificação).
- `python3 branding/icon.py`: gera o ícone do app JR C50D Sync (câmera com setas de sincronia): vetores adaptativos (`ic_launcher_foreground`, `ic_launcher_monochrome`), os WebP antigos e a prévia `icone_jr_c50d_sync.png`.
- `python3 branding/render.py` — gera as marcas d'água em `app/src/main/assets/watermarks/` e os ícones antigos (Android 7).

Os blocos do símbolo foram medidos em `Logo_Simbolo.png` (500 × 500) e estão em `symbol.py` → `TILES`.
