# WorldPainter MCP — Antigravity

WorldPainter v2 açıkken gömülü köprü `http://127.0.0.1:8765` dinler. Bu Node sunucusu stdio MCP ile Antigravity’ye araçları verir.

## Kurulum

```powershell
cd C:\Users\Admin\Documents\WorldPainter-v2-updated\tools\mcp-server
npm install
```

## Antigravity MCP kaydı

Örnek yapılandırma (yolları kendi makinenize göre düzeltin):

```json
{
  "mcpServers": {
    "worldpainter": {
      "command": "node",
      "args": [
        "C:\\Users\\Admin\\Documents\\WorldPainter-v2-updated\\tools\\mcp-server\\index.js"
      ],
      "env": {
        "WP_BRIDGE_URL": "http://127.0.0.1:8765"
      }
    }
  }
}
```

İsteğe bağlı token (köprü ve MCP aynı değeri kullanmalı):

```text
WP_BRIDGE_TOKEN=secret
```

Java tarafı: ortam değişkeni `WP_BRIDGE_TOKEN` veya sistem özelliği `wp.bridge.token`. İsteklerde header: `X-WP-Token`.

## Başlatma sırası

1. WorldPainter v2’yi aç ve bir dünya yükle.
2. `GET http://127.0.0.1:8765/api/status` → `online: true`.
3. Antigravity’yi yeniden başlat / MCP sunucusunu bağla.
4. Sohbette `wp_get_world_info` ile dene.

## Araçlar

| Araç | Köprü |
|------|--------|
| `wp_get_world_info` | `GET /api/world` |
| `wp_inspect_region` | `GET /api/inspect` |
| `wp_create_river` | `POST /api/river/carve` |
| `wp_sculpt_terrain` | `POST /api/terrain/sculpt` |
| `wp_apply_biome_layer` | `POST /api/terrain/paint` (eğim **derece**) |
| `wp_run_script` | `POST /api/script` |
| `wp_undo` / `wp_redo` | `POST /api/undo` / `redo` |
| `wp_save_world` | `POST /api/save` (önce UI’dan bir kez kaydet) |
