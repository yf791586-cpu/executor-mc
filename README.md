# Executor Mod (Minecraft 1.21.11, Fabric)

Mod client untuk menjalankan script Lua (jalan otomatis, kamera, chat, dll).
Tombol H = buka UI, tombol K = stop script.

## Cara dapat file .jar (tanpa PC, lewat GitHub Actions)
1. Buat repo baru di GitHub, upload semua isi folder ini (termasuk folder .github).
2. Buka tab Actions, tunggu workflow "build" selesai (hijau).
3. Buka run tersebut, download artifact "executormod-jar" (isinya executormod-1.0.0.jar).
Workflow otomatis mencari versi Fabric terbaru untuk 1.21.11, jadi tidak perlu edit angka versi.

## Cara build di PC
Butuh JDK 21 dan Gradle 9.2:  gradle build  ->  hasil di build/libs/executormod-1.0.0.jar
(kalau gagal di bagian dependensi, cek angka versi terbaru di https://fabricmc.net/develop
dan isi di gradle.properties)

## Cara pakai
- Taruh jar di folder mods bersama Fabric API untuk 1.21.11 (boleh bareng Meteor Client).
- Tekan H di dalam game. Script contoh ada di .minecraft/config/executormod/scripts/
- Edit/ tambah file .lua di folder itu, lalu tekan Reload di UI.
- Pilih file di daftar kiri -> Run File. Atau ketik 1 baris di kotak bawah -> Run Line.

## API script
player.forward/back/left/right/jump/sneak/sprint/attack/use(true/false)
player.stop(), player.look(yaw, pitch), player.lookAt(x,y,z)
player.pos(), player.yaw(), player.pitch(), player.health(), player.food()
chat.send("teks atau /command")
world.block(x,y,z)
print(...), wait(detik), waitTicks(tick)

PENTING: selalu pakai wait() di dalam loop. Loop tanpa wait akan dihentikan otomatis
setelah banyak perintah, tapi loop kosong "while true do end" akan membekukan game.
