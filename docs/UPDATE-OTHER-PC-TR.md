# Diğer Windows bilgisayarda WorldPainter V2 güncellemesi

Aşağıdaki görev metni diğer bilgisayardaki yapay zekaya verilebilir.

WorldPainter V2'yi bu bilgisayarda güncelle ve çalışır masaüstü uygulaması olarak hazırla.

- Repo: https://github.com/rahmibabapro/WorldPainter-v2.git
- Hedef branch: worldpainter-v2
- Güncelleme, Nehir 3-mod UI + waypoint path birleştirme commit'ini içermeli (Tools → Nehir...).
- PowerShell kullan. Mevcut V2 kurulumunu ve masaüstü kısayolunun hedefini bul.
- Açık dünyayı kaydettirip uygulamayı normal kapat; zorla sonlandırma. Mevcut .world dosyalarını, custom brush'ları ve `%APPDATA%\WorldPainter [V2]` profilini koru. Uygulama kapandıktan sonra profil ve launcher ayarlarını tarihli bir konuma yedekle.
- Mevcut kaynak klasöründe remote, branch ve `git status --short` kontrolü yap. Yerel değişiklik varsa silme/resetleme; güncel branch'i ayrı bir klasöre klonla. Temiz ve doğru branch'te ise `git fetch origin` ardından `git pull --ff-only origin worldpainter-v2` kullan. Farklı/detached veya ayrışmış checkout durumunda mevcut çalışmayı koruyup ayrı klon kullan.
- Kaynak yoksa Documents altında boş bir klasöre `git clone -b worldpainter-v2 https://github.com/rahmibabapro/WorldPainter-v2.git` ile klonla.
- JDK 21'i (jpackage dahil) ve Maven 3.9+ yolunu bu bilgisayarda bul; önceki PC'nin JDK yolunu kullanma. Maven varsayılan konumu `%USERPROFILE%\.maven\maven-3.9.12`.
- JIDE eksikse repo kökünden `install-jide-from-worldpainter.ps1` ile kurulu resmi WorldPainter'ın lib klasöründen yükle; gerekirse `-WorldPainterLib` yolunu belirt. Alternatif: lisanslı/evaluation jar'larla `install-jide.ps1 -JideDir ...`. Ticari jar'ları Git'e ekleme.
- Repo kökünden `build-v2.ps1 -JdkHome "BU-PC-JDK-YOLU" -MavenHome "BU-PC-MAVEN-YOLU"` çalıştır. İlk kurulumda tam exe üret; `-SkipExe` kullanma. Her adımın çıkış kodunu kontrol et. Script toolchains.xml dosyasını üretir ve fat JAR plugin tanımını onarır. PowerShell `NativeCommandError` (jpackage ikon uyarısı) yüzünden durursa: jar zaten üretildiyse `WorldPainter\WPGUI\target\WorldPainter-v2.jar` dosyasını `dist\WorldPainter v2\app\WorldPainter-v2.jar` üzerine kopyala ve exe'nin varlığını doğrula.
- Çıktı: `dist\WorldPainter v2\WorldPainter v2.exe`. Script dist dizinini yeniden üretebilir; orada kullanıcı verisi bulunmadığını önceden kontrol et. Script launcher Java seçeneklerini de yeniler; önceki RAM ayarlarını aynen varsayma, bu PC'nin RAM'ine göre değerlendir.
- Masaüstündeki “WorldPainter V2” / “WorldPainter v2” kısayolunu bu exe'ye bağla; çalışma klasörü exe'nin klasörü olsun. Mevcut doğru kısayolu güncelle, gereksiz kopya oluşturma.
- Axiom doku işlemi için ayrıca `%USERPROFILE%\Downloads\dag.bp` ve `%USERPROFILE%\Desktop\terrain.bp` gerekir. Bunlar Git deposunda bulunmaz; kullanıcıdan ayrıca aktarılmalıdır. Eksik olmaları uygulama derlemesini engellemez. `river.bp` inceleme referansıdır, nehir scriptinin çalışma bağımlılığı değildir. Custom brush dosyaları da ayrıca aktarılmalıdır.
- Exe ve app/WorldPainter-v2.jar dosyalarını doğrula, uygulamayı aç, **Tools → Nehir...** (3 mod: Otomatik / Nokta yolu / Kaynak) ve Axiom global işlem girişlerini kontrol et. Kullanıcının dünyasında otomatik script çalıştırma.
- Nehir değişiklikleri derlenmiş UI + script yoludur; Minecraft görsel kabul testi tamamlanmış sayılmaz. Bozuk nehirli kullanıcı dünyasında otomatik tamir çalıştırma. Ayrı küçük test dünyası kullan.
- İş bitince kaynak klasörünü, tam commit SHA'sını, exe ve kısayol yolunu, build sonucunu ve eksik blueprint/brush dosyalarını bildir. `dist`, `dist-staging`, `jpackage-input`, kişisel profil veya dünyaları commit etme. Force-push/reset kullanma.
