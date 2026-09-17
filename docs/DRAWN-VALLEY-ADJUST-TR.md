# Çizime uyumlu nehir ve doğal vadi düzenlemesi

Yalnız **çizim yolu** (`DrawnRiverSession` / DrawnRiverDialog). Otomatik arama bu motoru henüz paylaşmaz.

## Aşamalar

1. Mevcut araziyi koruyan shallow carve  
2. Koridor araması (±8, sonra ±16)  
3. Hafif `TerrainAdjustmentPlan` (kazı ≤2, dolgu ≤0.5)  
4. **Güçlü arazi önerisi** düğmesi (kazı ≤6, dolgu ≤2, 5 dk) — sessizce normal plana eklenmez  

`enableTerrainAdaptation()` / yalnız `maxCut` yükseltme **kullanılmaz**. Arazi düzenlemesi kanal kazısından ayrıdır.

## Kurallar

- Kaynak uçları sabit; birleşimler taşınabilir (paylaşılan yeni nokta).  
- Otomatik göl yok; çıkış araması 128→256 (güçlüde 512).  
- Koridor dışı delta = 0; eğim hedefi ≤ 1:3.  
- Dünya yalnız **Uygula**; tek Undo (arazi + kanal + dirt + waterline).  
- Önizleme yazmaz.

## Önizleme renkleri

Gri çizim, cyan önerilen hat, turuncu kazı/dolgu, mavi kanal, kahverengi dirt −1, yeşil üst kıyı.
