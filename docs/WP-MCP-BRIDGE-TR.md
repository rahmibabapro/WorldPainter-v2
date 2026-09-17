# WorldPainter MCP köprüsü

## Mimari

Antigravity (veya Cursor) → stdio MCP (`tools/mcp-server`) → HTTP `127.0.0.1:8765` → `WorldPainterBridgeServer` → `App` / `Dimension` (EDT).

Köprü `Main` içinde GUI gösterildikten sonra başlar. Yalnız loopback dinler.

## Güvenlik

- Bind: `127.0.0.1` only.
- İsteğe bağlı `WP_BRIDGE_TOKEN` / `X-WP-Token`.
- Script gövdesi en fazla 100.000 karakter; paint bbox en fazla 512² hücre; sculpt yarıçapı en fazla 512.

## Eğim

`Dimension.getSlope` yükselti/mesafedir. MCP `minSlope` / `maxSlope` **derece** alır; köprü `tan(radyan)` ile karşılaştırır (`DefaultFilter` ile aynı).

## Undo

Her mutasyon `rememberChanges` → işlem → `armSavePoint`. Hata veya carver reddi `undoChanges` ile geri alınır. Menü / MCP `App.performUndo` / `performRedo` kullanır.

## Kaydet

`POST /api/save` yalnızca bilinen `lastSelectedFile` varken dialogsuz kaydeder.

## Test

- `BridgeTerrainOpsTest` (eğim / sculpt / paint)
- Canlı: status → inspect → sculpt → undo
