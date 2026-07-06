# TODO List Migrasi Chat AI (Bahtsul Masail) ke Android Native

Dokumen ini berisi rencana langkah demi langkah migrasi fitur Chat AI dari Flutter ke Android Native. Setiap langkah akan dikerjakan satu per satu untuk memudahkan pemantauan kuota.

## Rencana Langkah Pengerjaan

- [x] **Langkah 1: Setup Dependency**
  Menambahkan library Retrofit/OkHttp (untuk API Gemini), EncryptedSharedPreferences (untuk secure API Key storage), dan Markwon (untuk me-render Markdown teks AI) ke `app/build.gradle.kts`.

- [x] **Langkah 2: Skema Database SQLite untuk Chat History**
  Memodifikasi `SearchIndexManager.java` untuk menambahkan tabel `chats` (sesi percakapan) dan `messages` (detail isi chat user dan AI).

- [x] **Langkah 3: Integrasi API Gemini (Service & Network Layer)**
  Membuat kelas REST Client/Helper menggunakan OkHttp/Retrofit untuk memanggil Gemini API dengan parameter model `gemini-2.5-flash`, temperature, dan custom system instruction (prompt).

- [x] **Langkah 4: Desain Layout XML & UI**
  Membuat layout XML untuk chat screen (bubbles, input bar, send button) dan settings screen (input API Key, custom prompt).

- [x] **Langkah 5: Logika Presenter/ViewModel & Adapter**
  Membuat Activity baru (`ChatActivity` / `ChatFragment`), adapter RecyclerView untuk bubble chat, dan logic pengiriman pesan secara realtime.

---

> [!NOTE]
> Kita akan memulai dari **Langkah 1 (Setup Dependency)**. Setelah itu selesai, saya akan berhenti sejenak dan menunggu instruksi atau konfirmasi kuota dari kamu sebelum lanjut ke Langkah 2.
