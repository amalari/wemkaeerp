# 🎓 Modul Pembelajaran: Kalkulasi Jalur Anti-Tumpang-Tindih (Anti-Collision Routing) & Kurva Bézier ala n8n di Factory Flow

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: UI/UX Canvas Mathematics, Directed Graph Routing, Cubic Bézier (S-Curves), Multi-Lane Collision Prevention, Quadratic Fillet Corners  
> **Prasyarat**: Pemahaman dasar Compose Canvas (Vector Paths, `moveTo`, `cubicTo`, `quadraticTo`), geometri 2D, dan struktur Graph (Nodes & Edges)  
> **Referensi File**: `SwimlaneConnectionOverlay.kt`, `PipelineNodeGraphCanvas.kt`, `SwimlaneRoutingCollisionTest.kt`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata: Garis Diagram yang Saling Menumpuk ("Nyatu")
Pernahkah Anda membuka diagram alur proses kerja (*pipeline/workflow*) yang memiliki banyak cabang dan loop balik, lalu melihat beberapa garis berjalan di atas koordinat piksel yang sama persis sehingga tampak seperti satu garis tebal yang membingungkan?

Inilah yang sebelumnya terjadi di halaman `http://localhost:3000/factory-flow`:
1. **Garis Maju Antar Kolom (Forward Edges)**:
   Modul dari Tahap 2 (Teknis) memiliki 2 output yang menuju ke Tahap 3 (Rantai Pasok). Karena sistem sebelumnya menggunakan routing siku (*orthogonal stepped polyline*) dengan titik belok horizontal statis `laneX = (from.right + to.left) / 2f`, **kedua garis berbelok pada koordinat X yang persis sama**. Akibatnya, segmen vertikal kedua garis menumpuk 100% menjadi satu garis lurus yang tumpang tindih.
2. **Jalur Balik / Feedback Loops (QC Rejek ke Hulu)**:
   Ketika modul QC akhir di Tahap 5 menerbitkan 2 rute penanganan (Retur Kain ke Gudang dan Rework ke Meja Jahit), kedua garis tersebut keluar menggunakan `exitX = from.right + laneOffset`. Karena `laneOffset` bernilai konstan (13dp), **kedua garis turun ke koridor bawah di sumbu X yang sama persis**, lalu menyusuri koridor di sumbu Y yang hampir berhimpitan.
3. **Penyebab Mental**:
   Developer pemula sering memperlakukan garis sebagai "garis lurus patah 90 derajat yang melewati titik tengah". Padahal dalam graf yang padat, titik tengah bersama adalah jaminan terjadinya tabrakan (*collision bottleneck*).

### Solusi Dunia Nyata: Mengadopsi Logika n8n & Alokasi Multi-Lane
Alat otomasi diagram modern seperti **n8n**, **React Flow**, atau **Node-RED** tidak menggunakan garis patah siku kaku untuk aliran maju. Mereka menggunakan **Smooth Cubic Bézier Curves (S-Curves)**:
- Setiap port output dan input memiliki koordinat $Y$ uniknya sendiri.
- Garis mengalir secara organik dengan vektor tangen horizontal: keluar ke kanan dari modul sumber, melengkung anggun di ruang kosong, dan masuk ke kiri modul tujuan.
- Karena setiap pasang port memiliki koordinat $(Y_{\text{start}}, Y_{\text{end}})$ yang berbeda, kurva Bézier memiliki lintasan (*trajectory*) yang independen dan **tidak akan pernah menumpuk menjadi satu garis vertikal**.
- Untuk jalur balik (loop/koridor), diterapkan sistem **Multi-Lane Channel Allocation**: setiap kawat (*wire*) dialokasikan lajur keluar unik ($X_{\text{exit}}$), ketinggian koridor unik ($Y_{\text{corridor}}$), dan lajur masuk unik ($X_{\text{entry}}$) dengan sudut belok melengkung halus (*quadratic fillet*).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda diminta merancang kalkulasi routing graf anti-tabrakan dari nol, ikuti urutan langkah berikut:

```
Step 0: Klasifikasi Topologi Edge
   ├── 1. Same-Column Drop: Source & Target di kolom yang sama (vertikal lurus)
   ├── 2. Unobstructed Forward: Target di sebelah kanan & ruang di antaranya kosong (Cubic Bézier)
   └── 3. Corridor Routes: Loop balik (feedback) atau lewati kolom terhalang (Multi-Lane Corridor)

Step 1: Distribusi Titik Soket / Attachment Ports pada Kartu
   ├── Kelompokkan edges berdasarkan fromNodeId dan toNodeId
   └── Sebarkan exitY dan entryY merata sepanjang tinggi kartu agar tidak menumpuk di 1 titik

Step 2: Alokasi Multi-Lane Jalur Koridor
   ├── Hitung slot lajur vertikal keluar: exitX = from.right + baseOffset + slot * spacing
   ├── Berikan indeks track ketinggian koridor unik: corridorY = corridorBaseY + trackIndex * trackSpacing
   └── Hitung slot lajur vertikal masuk: entryX = to.left - baseOffset - slot * spacing

Step 3: Rendering Kurva Bézier n8n (Forward S-Curves)
   ├── Hitung jarak horizontal dx = to.left - from.right
   ├── Tentukan jarak kontrol curvature = dx * 0.45f
   └── path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, target.x, target.y)

Step 4: Rendering Sudut Melengkung Halus (Quadratic Fillet Polyline)
   └── Gantikan sudut 90 derajat siku yang kaku dengan pembulatan quadraticTo pada setiap tikungan

Step 5: Verifikasi Matematika Unit Test
   └── Buat test untuk memastikan slot dan track yang teralokasi selalu unik (distinct)
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Klasifikasi Topologi & Pendistribusian Port (`SwimlaneConnectionOverlay.kt`)

```kotlin
val outCount = graph.edges.groupingBy { it.fromNodeId }.eachCount()
val inCount = graph.edges.groupingBy { it.toNodeId }.eachCount()
val outIndex = mutableMapOf<String, Int>()
val inIndex = mutableMapOf<String, Int>()

val resolved = graph.edges.mapNotNull { edge ->
    val from = bounds.cardRect(edge.fromNodeId) ?: return@mapNotNull null
    val to = bounds.cardRect(edge.toNodeId) ?: return@mapNotNull null

    val oi = outIndex.getOrElse(edge.fromNodeId) { 0 }
    val ii = inIndex.getOrElse(edge.toNodeId) { 0 }
    outIndex[edge.fromNodeId] = oi + 1
    inIndex[edge.toNodeId] = ii + 1

    val totalOut = outCount[edge.fromNodeId] ?: 1
    val totalIn = inCount[edge.toNodeId] ?: 1
    // Sebarkan titik keluar & masuk secara proporsional sepanjang tinggi kartu
    val exitY = from.top + from.height * (oi + 1f) / (totalOut + 1f)
    val entryY = to.top + to.height * (ii + 1f) / (totalIn + 1f)

    val sameCol = kotlin.math.abs(from.left - to.left) < 4f && to.top >= from.bottom - 1f
    val goesRight = to.left >= from.right - 1f

    val blocked = if (goesRight && !sameCol) {
        // Cek apakah ada kartu fisik lain yang memblokir di antara from dan to
        allRects.any { card ->
            card.right > from.right + 2f &&
            card.left < to.left - 2f &&
            card.bottom > minOf(exitY, entryY) - 10f &&
            card.top < maxOf(exitY, entryY) + 10f
        }
    } else false

    val isUnobstructed = goesRight && !sameCol && !blocked
    // ...
}
```

**Mengapa blok ini ditulis begini?**
- Jika satu kartu memiliki 2 kabel keluar, kabel pertama berada di 33% tinggi kartu dan kabel kedua di 66% tinggi kartu. Mereka tidak pernah keluar dari koordinat piksel yang sama.
- Pengecekan `blocked` mempertimbangkan koordinat 2D (X dan Y), bukan hanya sumbu X. Jika tidak ada kartu penghalang fisik di jalurnya, garis berhak menggunakan kurva Bézier langsung tanpa dipaksa masuk ke koridor bawah.

---

### Blok B: Kurva Bézier Halus ala n8n untuk Garis Maju

```kotlin
eg.isUnobstructedForward -> {
    val p0 = Offset(from.right, exitY)
    val p3 = Offset(to.left, entryY)
    val dx = p3.x - p0.x
    // Curvature menentukan seberapa 'dalam' kurva membusur secara horizontal
    val curvature = (dx * 0.45f).coerceIn(36.dp.toPx(), 180.dp.toPx())
    val cp1 = Offset(p0.x + curvature, p0.y)
    val cp2 = Offset(p3.x - curvature, p3.y)

    val path = Path().apply {
        moveTo(p0.x, p0.y)
        cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p3.x, p3.y)
    }
}
```

**Mental Model Matematika Bézier:**
- `cp1` diletakkan di koordinat $(X_0 + \text{curvature}, Y_0)$. Vektor singgung (*tangent*) saat keluar dari kartu selalu horizontal sempurna ke arah kanan ($+X$).
- `cp2` diletakkan di koordinat $(X_3 - \text{curvature}, Y_3)$. Vektor singgung saat mendarat di port target selalu horizontal sempurna dari arah kiri.
- S-Curve ini mengalir bebas di celah antar kolom tanpa segmen garis lurus vertikal statis. Tabrakan vertikal antar garis dieliminasi secara alami.

---

### Blok C: Sistem Multi-Lane Alokasi Lajur Koridor Bebas Benturan

```kotlin
// Multi-lane corridor routing
val exitSlot = corridorExitSlots.getOrElse(edge.fromNodeId) { 0 }
corridorExitSlots[edge.fromNodeId] = exitSlot + 1
// Setiap kabel keluar di jalur vertikal yang berbeda (+14dp, +26dp, dst.)
val exitX = from.right + 14.dp.toPx() + exitSlot * 12.dp.toPx()

val entrySlot = corridorEntrySlots.getOrElse(edge.toNodeId) { 0 }
corridorEntrySlots[edge.toNodeId] = entrySlot + 1
// Setiap kabel masuk di jalur vertikal yang berbeda (-14dp, -26dp, dst.)
val entryX = to.left - 14.dp.toPx() - entrySlot * 12.dp.toPx()

// Alokasi ketinggian koridor horizontal: setiap edge mendapat rel/ketinggian sendiri
val trackIndex = corridorEdges.indexOfFirst { it.edge.id == edge.id }.coerceAtLeast(0)
val corridorBaseY = contentH - corridorReserve + 16.dp.toPx()
val corridorY = corridorBaseY + trackIndex * 14.dp.toPx()

val waypoints = listOf(
    Offset(from.right, exitY),
    Offset(exitX, exitY),
    Offset(exitX, corridorY),
    Offset(entryX, corridorY),
    Offset(entryX, entryY),
    Offset(to.left, entryY)
)
```

**Mengapa blok ini krusial?**
- **Sumbu Vertikal Keluar**: Jika QC memiliki 2 rute feedback (Defect Kain dan Rework Jahit), Rute 1 turun di $X = \text{from.right} + 14\text{dp}$, sedangkan Rute 2 turun di $X = \text{from.right} + 26\text{dp}$. Mereka tidak berhimpitan.
- **Sumbu Horizontal Koridor**: Rute 1 berjalan di $Y = \text{Base} + 0\text{dp}$, Rute 2 di $Y = \text{Base} + 14\text{dp}$. Rel kabel bawah terpisah seperti rel kereta api paralel.
- **Sumbu Vertikal Masuk**: Masing-masing target menerima kabel di koordinat $X$ yang unik.

---

### Blok D: Pembulatan Tikungan Halus (*Quadratic Fillet Polyline*)

```kotlin
private fun Path.addRoundedPolyline(points: List<Offset>, radius: Float) {
    if (points.size < 2) return
    if (points.size == 2 || radius <= 0f) {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        return
    }

    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size - 1) {
        val pPrev = points[i - 1]
        val pCurr = points[i]
        val pNext = points[i + 1]

        val vIn = pCurr - pPrev
        val lenIn = kotlin.math.hypot(vIn.x.toDouble(), vIn.y.toDouble()).toFloat()
        val vOut = pNext - pCurr
        val lenOut = kotlin.math.hypot(vOut.x.toDouble(), vOut.y.toDouble()).toFloat()

        if (lenIn < 0.01f || lenOut < 0.01f) {
            lineTo(pCurr.x, pCurr.y)
            continue
        }

        val uIn = Offset(vIn.x / lenIn, vIn.y / lenIn)
        val uOut = Offset(vOut.x / lenOut, vOut.y / lenOut)

        // Batasi radius agar tidak melebihi setengah panjang segmen
        val r = minOf(radius, lenIn / 2f, lenOut / 2f)
        val pBefore = pCurr - uIn * r
        val pAfter = pCurr + uOut * r

        lineTo(pBefore.x, pBefore.y)
        // Bentuk lengkungan mulus di sudut siku menggunakan kontrol pCurr
        quadraticTo(pCurr.x, pCurr.y, pAfter.x, pAfter.y)
    }
    lineTo(points.last().x, points.last().y)
}
```

**Mental Model Matematika Tikungan:**
- Garis lurus berjalan dari $P_{\text{prev}}$ sampai titik $P_{\text{before}}$ (sejauh $R$ sebelum sudut $P_{\text{curr}}$).
- Fungsi `quadraticTo(P_curr.x, P_curr.y, P_after.x, P_after.y)` menarik busur lengkung menuju $P_{\text{after}}$ menggunakan sudut siku sebagai titik kendali gravitasi kurva.
- Tampilan garis berubah dari "pipa kotak kaku" menjadi "kabel sirkuit modern" dengan estetika premium.

---

## ⚖️ 4. Technology & Approach ("The Why")

| Pendekatan Terpilih | Alternatif yang Ditinggalkan | Mengapa Kita Memilih Pendekatan Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Cubic Bézier (n8n Style)** | Stepped Orthogonal Lines dengan titik tengah statis | Menghilangkan garis vertikal bersama. Kurva S fleksibel dan tidak saling menutupi. | Garis bertumpuk di tengah kolom (*visual collision*), sulit dibedakan jalurnya. |
| **Multi-Lane Track Slots** | Modulo Random Jitter (±5px) | Menjamin pemisahan 100% deterministik antar jalur (pasti berjarak 12-14dp). | Jitter berbasis modulo hash sering menghasilkan collision ketika dua ID memiliki nilai modulo sama. |
| **Quadratic Fillet Polyline** | Sharp 90° Polylines | Sudut belok melengkung membuat aliran kabel terlihat alami dan mudah diikuti mata manusia. | Sudut tajam kaku memberi kesan aplikasi purba (*primitive visual*). |
| **2D Bounding Box Block Check** | 1D X-Axis Only Block Check | Hanya mengirim garis ke koridor bawah jika benar-benar terhalang kartu secara vertikal. | Garis maju yang sebenarnya bersih dipaksa memutar jauh ke koridor bawah. |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls) & Cara Menghindarinya

1. **Jebakan Pengurangan Koordinat Margin Bawah:**
   - *Masalah*: Menghitung `corridorY = contentH - CANVAS_PAD / 2f - trackIndex * 8f`. Karena dikurangi, semakin besar `trackIndex`, garis malah naik menembus area kartu terbawah!
   - *Solusi*: Selalu tambahkan ke bawah (*downwards*) di koridor, atau pastikan koordinat terikat pada basis `contentH - corridorReserve + offset + trackIndex * spacing`.
2. **Menggunakan `quadraticBezierTo` yang Deprecated di Compose Multiplatform:**
   - *Masalah*: Compose modern telah mendeprekasi `quadraticBezierTo` dan menggantinya dengan `quadraticTo` agar konsisten dengan `cubicTo`.
   - *Solusi*: Selalu gunakan `Path.quadraticTo(x1, y1, x2, y2)`.
3. **Radius Fillet Melebihi Panjang Segmen:**
   - *Masalah*: Jika jarak antar titik hanya 10px sementara radius fillet 15px, titik awal lengkungan akan melompati titik sebelumnya dan garis menjadi kusut berputar (*loop glitch*).
   - *Solusi*: Selalu batasi dengan `val r = minOf(radius, lenIn / 2f, lenOut / 2f)`.

---

## 🧪 6. Verifikasi & Tantangan Mandiri

### Cara Menguji Kebenaran Implementasi
1. **Automated Unit Tests**:
   Jalankan pengujian JVM di shared module:
   ```bash
   ./gradlew :app:shared:jvmTest
   ```
   Pastikan test `SwimlaneRoutingCollisionTest` dan `PipelineNodeGraphGeometryTest` lulus 100%.
2. **Visual Audit di Browser**:
   - Buka `http://localhost:3000/factory-flow`.
   - Perhatikan celah antara Tahap 2 (Teknis) dan Tahap 3 (Rantai Pasok): garis mengalir sebagai kurva Bézier halus yang terpisah, tanpa ada garis vertikal yang menumpuk.
   - Perhatikan Tahap 5 (QC): garis retur kain merah dan garis rework jahit keluar dengan jarak horizontal terpisah (12dp) dan menyusuri koridor bawah di dua rel ketinggian yang berbeda (14dp).
   - Klik kartu modul mana pun untuk memverifikasi efek highlight seleksi.

### Tantangan Mandiri untuk Junior Developer
- [ ] **Tantangan 1**: Tambahkan animasi titik aliran pulsa (*particle flow pulse*) yang bergerak di sepanjang kurva Bézier untuk merepresentasikan aliran data Purchase Order yang sedang diproses.
- [ ] **Tantangan 2**: Buat tooltip dinamis saat mouse melakukan hover di atas salah satu kabel koridor bawah, menampilkan teks kontrak data (misal: "Kain Susut >5% - Retur ke Rantai Pasok").
