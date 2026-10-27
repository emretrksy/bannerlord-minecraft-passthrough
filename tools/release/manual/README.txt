Bannerlord x Minecraft Passthrough (manual install)
===================================================

Play Minecraft's Steve inside Mount & Blade II: Bannerlord battles.
Full guide: https://github.com/emretrksy/bannerlord-minecraft-passthrough
By Emre Turksoy. Psicodart Games Discord: https://discord.gg/R4qStgw9bk

This package has no installer scripts. The GitHub release has the same files with a one-click
Install.bat if you prefer that.

ENGLISH
-------
You need:
  - Mount & Blade II: Bannerlord v1.4.8 (Steam), single player, windowed or borderless.
  - Minecraft: Java Edition with the official Minecraft Launcher (minecraft.net or the Xbox app).
    Open the launcher once and sign in before installing.
  - About 5 GB of free RAM while both games run.

Install (close Bannerlord and the Minecraft Launcher first):
  0. Before extracting: right-click the downloaded .zip > Properties > tick "Unblock" > OK.
     Otherwise Windows blocks the DLLs and Bannerlord says
     "Cannot load: ...\MinecraftPassthrough.dll". Already extracted? Do the same for
     MinecraftPassthrough.dll and 0Harmony.dll in Modules\MinecraftPassthrough\bin\Win64_Shipping_Client.
  1. Bannerlord: copy everything inside this package's "Bannerlord" folder into your Bannerlord
     folder (Steam > Bannerlord > Manage > Browse local files), merging the "Modules" and "bin"
     folders.
     If you already use ReShade, do not replace your dxgi.dll, ReShade.ini or MCPassthroughPreset.ini.
     Your ReShade must be 6.x WITH add-on support; in game press Home and tick "MCPassthrough".
  2. Minecraft: press Win+R, type %APPDATA%\.minecraft and press Enter. Copy everything inside
     this package's "Minecraft" folder into that folder, merging "versions".
  3. Open the Minecraft Launcher > Installations > New installation:
       Name:             Bannerlord x Minecraft
       Version:          release {FABRIC_ID}
       Game directory:   %APPDATA%\.minecraft\bannerlord-passthrough
                         (type the full path, e.g. C:\Users\<you>\AppData\Roaming\.minecraft\bannerlord-passthrough)
       More options > JVM arguments, replace everything with:
                         {JAVA_ARGS}
     Press Create.

Play:
  1. In the Minecraft Launcher, pick the "Bannerlord x Minecraft" installation and press Play.
     Wait until Minecraft is open. Do not minimise it.
  2. Start Bannerlord, enable "Minecraft Passthrough" in the Mods tab and play Singleplayer.
     Enter any battle.
  F9 hands the controls back to Bannerlord and back again.
  F10 opens the developer panel (kits, any item) and pauses the battle.

Uninstall:
  - In Bannerlord's folder delete Modules\MinecraftPassthrough, and in bin\Win64_Shipping_Client
    delete MCPassthrough.addon64, MCPassthroughPreset.ini and reshade-shaders\Shaders\MCPassthrough.fx.
    Also delete dxgi.dll and ReShade.ini there if they came from this package.
  - Delete the "Bannerlord x Minecraft" installation in the Minecraft Launcher, and
    %APPDATA%\.minecraft\versions\{FABRIC_ID} if no other installation uses it.

Single player only. Do not use the ReShade add-on build (dxgi.dll) in online games with anti-cheat.

TÜRKÇE
------
Gerekenler:
  - Mount & Blade II: Bannerlord v1.4.8 (Steam), tek oyunculu, pencereli ya da çerçevesiz pencere.
  - Resmi Minecraft Launcher'lı Minecraft: Java Edition (minecraft.net ya da Xbox uygulaması).
    Kurmadan önce launcher'ı bir kez açıp giriş yap.
  - İki oyun birlikte açıkken yaklaşık 5 GB boş RAM.

Kurulum (önce Bannerlord'u ve Minecraft Launcher'ı kapat):
  0. Çıkarmadan önce: indirdiğin .zip'e sağ tık > Özellikler > "Engellemeyi kaldır"ı işaretle > Tamam.
     Yoksa Windows DLL'leri engeller ve Bannerlord
     "Cannot load: ...\MinecraftPassthrough.dll" der. Zaten çıkardıysan aynısını
     Modules\MinecraftPassthrough\bin\Win64_Shipping_Client içindeki MinecraftPassthrough.dll ve
     0Harmony.dll için yap.
  1. Bannerlord: bu paketteki "Bannerlord" klasörünün içindeki her şeyi Bannerlord klasörüne
     kopyala (Steam > Bannerlord > Yönet > Yerel dosyalara göz at), "Modules" ve "bin"
     klasörlerini birleştir.
     Zaten ReShade kullanıyorsan dxgi.dll, ReShade.ini ve MCPassthroughPreset.ini dosyalarının
     üzerine yazma. ReShade'in add-on destekli 6.x olmalı; oyunda Home'a basıp "MCPassthrough"u işaretle.
  2. Minecraft: Win+R'ye bas, %APPDATA%\.minecraft yazıp Enter'a bas. Bu paketteki "Minecraft"
     klasörünün içindeki her şeyi oraya kopyala, "versions" klasörünü birleştir.
  3. Minecraft Launcher > Kurulumlar > Yeni kurulum:
       Ad:               Bannerlord x Minecraft
       Sürüm:            release {FABRIC_ID}
       Oyun dizini:      %APPDATA%\.minecraft\bannerlord-passthrough
                         (tam yolu yaz, örn. C:\Users\<sen>\AppData\Roaming\.minecraft\bannerlord-passthrough)
       Diğer seçenekler > JVM argümanları, hepsini sil ve şunu yaz:
                         {JAVA_ARGS}
     Oluştur'a bas.

Oynamak:
  1. Minecraft Launcher'da "Bannerlord x Minecraft" kurulumunu seçip Oyna'ya bas.
     Minecraft açılana kadar bekle. Simge durumuna küçültme.
  2. Bannerlord'u başlat, Mods sekmesinde "Minecraft Passthrough"u aç ve Tek Oyunculu oyna.
     Herhangi bir savaşa gir.
  F9 kontrolü Bannerlord'a verir ve geri alır.
  F10 geliştirici panelini (kitler, her eşya) açar ve savaşı durdurur.

Kaldırma:
  - Bannerlord klasöründe Modules\MinecraftPassthrough'u, bin\Win64_Shipping_Client içinde
    MCPassthrough.addon64, MCPassthroughPreset.ini ve reshade-shaders\Shaders\MCPassthrough.fx'i sil.
    Oradaki dxgi.dll ve ReShade.ini bu paketten geldiyse onları da sil.
  - Minecraft Launcher'da "Bannerlord x Minecraft" kurulumunu sil; başka kurulum kullanmıyorsa
    %APPDATA%\.minecraft\versions\{FABRIC_ID} klasörünü de sil.

Yalnızca tek oyunculu. ReShade'in add-on sürümünü (dxgi.dll) anti-cheat'li çevrimiçi oyunlarda kullanma.
