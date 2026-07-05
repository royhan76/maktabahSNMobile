# Spesifikasi Teknis Aplikasi Android MAI Reader

## Gambaran Umum

MAI Reader adalah aplikasi Android khusus untuk membaca kitab berformat **.mai**. Aplikasi ini **bukan** PDF Reader, EPUB Reader, HTML Reader, maupun aplikasi AI. Fokus utama aplikasi adalah memberikan pengalaman membaca kitab yang cepat, nyaman, modern, dan memiliki sistem pencarian yang jauh lebih presisi dibandingkan aplikasi pembaca kitab pada umumnya.

Seluruh engine pembacaan dikembangkan secara khusus agar mampu membaca file berformat **.mai** dengan performa tinggi, penggunaan memori yang rendah, dan dapat dikembangkan di masa depan tanpa mengubah struktur aplikasi.

---

# Teknologi

* IDE : Android Studio (versi terbaru)
* Bahasa Pemrograman : Java
* Minimum SDK : Android 8 (API 26)
* Target SDK : Terbaru
* UI : Material Design 3
* Arsitektur : MVVM (Java)
* Penyimpanan : Assets + Internal Storage
* Database : Tidak diperlukan (kecuali jika digunakan untuk bookmark atau indexing)
* Format Kitab : `.mai`

---

# Konsep Dasar

Aplikasi hanya mengenali file dengan ekstensi **.mai**.

Format `.mai` merupakan format internal aplikasi sehingga pengguna umum tidak dapat langsung membuka atau memanfaatkannya menggunakan aplikasi lain. Implementasi internal format `.mai` sepenuhnya ditentukan oleh developer (binary, custom serialization, kompresi, atau metode lainnya), namun seluruh proses pembacaan harus dilakukan melalui Reader Engine milik aplikasi.

---

# Struktur Folder

```
app/
 ├── assets/
 │    ├── books/
 │    │     ├── index.mai
 │    │     ├── fathul_muin.mai
 │    │     ├── ianatut_thalibin.mai
 │    │     ├── fathul_wahhab.mai
 │    │     └── ...
 │    ├── fonts/
 │    │     ├── Amiri-Regular.ttf
 │    │     ├── Amiri-Bold.ttf
 │    │     └── KFGQPC.ttf
 │    └── images/
 │
 ├── data/
 ├── repository/
 ├── reader/
 ├── search/
 ├── ui/
 └── utils/
```

---

# Tampilan Aplikasi

Desain harus modern, minimalis, bersih, dan fokus pada kenyamanan membaca.

Referensi desain:

* Google Play Books
* Kindle
* ReadEra
* Moon+ Reader

Hindari desain yang terlalu ramai, penuh warna, atau banyak ornamen. Prioritaskan ruang baca yang luas, tipografi yang nyaman, dan navigasi yang sederhana.

---

# Halaman Perpustakaan

Halaman awal menampilkan daftar kitab.

Fitur:

* Daftar seluruh kitab
* Pencarian nama kitab
* Cover kitab (opsional)
* Informasi penulis (opsional)
* Scroll yang ringan
* Material Design 3

---

# Halaman Membaca

Merupakan halaman utama aplikasi.

Komponen:

* Tombol kembali
* Judul kitab
* Tombol pencarian
* Menu lainnya
* Area membaca fullscreen
* Nomor halaman

Fokus utama adalah teks kitab sehingga seluruh elemen selain isi kitab dibuat seminimal mungkin.

---

# Font Arab

Gunakan font yang nyaman dibaca.

Prioritas:

* Amiri
* KFGQPC

Jangan menggunakan font Arab bawaan Android.

Pengguna dapat mengubah:

* ukuran font
* line spacing
* margin
* jenis font
* brightness
* dark mode

Seluruh perubahan dilakukan secara realtime.

---

# Dukungan RTL

Seluruh teks Arab wajib menggunakan tata letak RTL.

Implementasi meliputi:

* textDirection RTL
* layoutDirection RTL
* gravity RIGHT

Tidak boleh ada teks Arab yang ditampilkan rata kiri.

---

# Reader Engine

Jangan pernah memuat seluruh isi kitab ke dalam memori.

Gunakan lazy loading.

Contoh:

Jika pengguna sedang membaca halaman 250 maka engine cukup memuat:

* halaman 249
* halaman 250
* halaman 251

Halaman lain dibaca saat diperlukan.

Target penggunaan RAM harus serendah mungkin.

Gunakan RecyclerView atau sistem paging.

Jangan menggunakan WebView sebagai media pembacaan kitab.

---

# Bookmark

Pengguna dapat menyimpan:

* halaman
* bab
* posisi terakhir

Bookmark tersimpan otomatis.

---

# Riwayat Bacaan

Saat kitab dibuka kembali, aplikasi langsung membuka halaman terakhir yang dibaca.

---

# Mesin Pencarian (Fitur Utama)

Pencarian merupakan fitur utama aplikasi.

Bukan sekadar pencarian satu kata, tetapi menggunakan sistem **Multi Search Filter** yang memungkinkan pengguna menentukan beberapa kata kunci sekaligus sehingga hasil menjadi jauh lebih presisi.

Pengguna dapat menambahkan filter sesuai kebutuhan.

Contoh tampilan:

```
Kolom 1
[________________]

+ Tambah Filter
```

Setelah ditambah:

```
Kolom 1
[________________]

Kolom 2
[________________]
```

Kemudian:

```
Kolom 1
[________________]

Kolom 2
[________________]

Kolom 3
[________________]
```

Jumlah filter bersifat dinamis.

Minimal:

1 filter

Maksimal:

5 filter atau lebih.

---

# Cara Kerja Multi Search

Setiap kolom menggunakan logika **AND**, bukan OR.

Contoh isi kitab:

```
إذا دخل المسجد
```

Pengguna mengisi:

Kolom 1

```
إذا
```

Kolom 2

```
دخل
```

Kolom 3

```
المسجد
```

Hasil pencarian hanya menampilkan baris yang mengandung ketiga kata tersebut secara bersamaan.

Contoh lain:

Kolom 1

```
باب
```

Kolom 2

```
الطهارة
```

Kolom 3

```
الماء
```

Engine hanya mengembalikan hasil yang memenuhi seluruh filter.

Dengan metode ini, pencarian menjadi jauh lebih akurat dibanding pencarian satu kata.

---

# Opsi Advanced Search

Tambahkan beberapa opsi pencarian:

* Urutan kata harus sama
* Case Sensitive
* Cocokkan harakat
* Abaikan harakat
* Cari pada kitab aktif
* Cari pada seluruh kitab

---

# Hasil Pencarian

Setiap hasil pencarian menampilkan:

* Nama kitab
* Nama bab (jika tersedia)
* Nomor halaman
* Potongan kalimat
* Highlight pada kata yang ditemukan

Saat hasil dipilih, aplikasi langsung membuka halaman yang sesuai.

---

# Search Index

Pada saat kitab pertama kali dibuka, engine dapat membuat Search Index di internal storage.

Index digunakan agar pencarian berikutnya berlangsung hampir instan meskipun kitab berukuran besar.

---

# Menu Aplikasi

Menu utama terdiri dari:

* Perpustakaan
* Bookmark
* Riwayat Bacaan
* Pengaturan
* Tentang

---

# Pengaturan

Minimal menyediakan:

* Ukuran font
* Jenis font
* Line spacing
* Margin
* Brightness
* Dark mode
* Reset pengaturan

---

# Animasi

Gunakan Material Motion.

Animasi harus ringan dan halus.

Hindari animasi yang berlebihan.

---

# Target Performa

Target performa aplikasi:

* Waktu membuka aplikasi kurang dari 1 detik.
* Membuka kitab kurang dari 500 ms.
* Perpindahan halaman tanpa lag.
* Scrolling sangat halus.
* Pencarian satu kata berlangsung kurang dari 100 ms pada kitab besar.
* Multi Search tetap cepat meskipun menggunakan hingga lima filter.
* Penggunaan RAM rendah melalui lazy loading dan cache halaman di sekitar posisi baca.

---

# Reader Engine

Seluruh proses pembacaan harus dipisahkan ke dalam Reader Engine.

Reader Engine bertanggung jawab terhadap:

* membuka file `.mai`
* membaca struktur file
* parsing data
* membuat search index
* menyediakan halaman
* melakukan pencarian
* menyimpan bookmark
* menyimpan posisi terakhir membaca

Seluruh bagian aplikasi berkomunikasi melalui Reader Engine sehingga perubahan format `.mai` di masa depan tidak memerlukan perubahan pada tampilan aplikasi.

---

# Tujuan Akhir

MAI Reader harus menjadi aplikasi pembaca kitab digital yang ringan, cepat, modern, nyaman digunakan, dan memiliki sistem pencarian paling presisi melalui fitur **Multi Search Filter**, sehingga pengguna dapat menemukan kalimat dalam kitab dengan akurat menggunakan kombinasi beberapa kata kunci tanpa bergantung pada pencarian satu kata seperti aplikasi pembaca kitab lainnya.
