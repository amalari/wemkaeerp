#!/usr/bin/env bash

# ==============================================================================
# WeMade ERP — Change-Based Test Runner (test selektif)
#
# Menjalankan HANYA test yang berhubungan dengan file yang berubah, supaya loop
# pengembangan kembali dalam hitungan detik, bukan puluhan detik.
#
# Cara kerja: untuk setiap file yang berubah, script mengumpulkan nama kelas /
# fungsi / nama file resource-nya, lalu mencarinya di SELURUH sumber test lintas
# modul. Test class yang menyebut nama itu yang dijalankan.
#
# Pencarian sengaja dilakukan lintas modul, bukan hanya folder yang sama, supaya
# regresi seperti "kolom elements seed berubah bentuk" tetap menangkap test di
# server (`InvoiceTemplateSeedDecodeTest`) walaupun yang diubah ada di `core/`.
#
# Peringatan penting: sesudah menjalankan script ini, folder build/test-results/
# HANYA berisi test yang tadi dipilih. Jangan pernah membacanya sebagai bukti
# "seluruh suite hijau". Jalankan `./dev.sh test` untuk bukti penuh sebelum push.
# ==============================================================================

set -uo pipefail

BOLD="\033[1m"
GREEN="\033[0;32m"
CYAN="\033[0;36m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
DIM="\033[2m"
RESET="\033[0m"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

# .env wajib dimuat di sini.
#
# Gradle TIDAK membaca .env sendiri: `DatabaseFactory` jatuh ke default
# localhost:5432, sedangkan database proyek ini ada di 5435. Tanpa baris ini,
# :server:test gagal dengan HikariPool$PoolInitializationException yang terlihat
# seperti bug kode padahal cuma salah alamat database.
if [ -f "$PROJECT_DIR/.env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "$PROJECT_DIR/.env"
    set +a
fi

# Source set test per modul (dipakai untuk mencari referensi & menghitung jumlah).
CORE_SRC="core/src"
SHARED_SRC="app/shared/src"
SERVER_SRC="server/src/test"
ALL_TEST_ROOTS="core/src app/shared/src server/src"
# Hanya berkas KelasTest.kt yang dianggap test.
TEST_GLOB="*Test.kt"
# Di atas jumlah kelas ini, menjalankan modul penuh lebih murah & lebih tenang.
WHOLE_MODULE_THRESHOLD=20

MODE="selective"   # selective | list | full
BASE_REF=""

usage() {
    echo -e "${BOLD}Pemilih test berdasarkan perubahan — WeMade ERP${RESET}"
    echo
    echo "  ./tools/test-changed.sh              Test untuk perubahan yang belum di-commit (vs HEAD)"
    echo "  ./tools/test-changed.sh origin/main  Test untuk seluruh perubahan sejak merge-base dengan main"
    echo "  ./tools/test-changed.sh --list       Hanya tampilkan rencana, tidak menjalankan apa pun"
    echo "  ./tools/test-changed.sh --full       Paksa seluruh suite (sama dengan ./dev.sh test)"
    echo
    echo -e "${DIM}Atau lewat pintu utama: ./dev.sh test-changed [ref]${RESET}"
}

for arg in "$@"; do
    case "$arg" in
        --full|-f)       MODE="full" ;;
        --list|-n)       MODE="list" ;;
        --help|-h)       usage; exit 0 ;;
        -*)              echo -e "${RED}Opsi tidak dikenal: $arg${RESET}"; usage; exit 2 ;;
        *)               BASE_REF="$arg" ;;
    esac
done

# ── 1. File yang dianggap "menyentuh banyak hal" → seluruh suite ───────────────
# Perubahan di berkas ini tidak punya daftar test yang bisa dipersempit dengan
# jujur: konfigurasi build, util yang dipakai lintas domain, dan serialisasi JSON.
#
# Kenapa `shared/common/`, `shared/json/`, dan `domain/common/` masuk daftar ini
# padahal pencarian referensi sudah berjalan? Karena pencarian referensi hanya
# menangkap test yang MENYEBUT nama file/kelasnya. Bukti nyatanya:
#   `MeasureCodec` dipakai oleh 17 file di 4 modul, tetapi 0 test menyebut namanya;
#   `DateTimeCodec` dipakai 21 file, juga 0 test menyebut namanya.
# Berkas seperti itu rusak secara senyap kalau hanya mengandalkan pencarian
# referensi, jadi untuk golongan ini seluruh suite memang jawaban yang benar.
is_foundational() {
    case "$1" in
        settings.gradle.kts|build.gradle.kts|gradle.properties) return 0 ;;
        gradle/libs.versions.toml) return 0 ;;
        */build.gradle.kts) return 0 ;;
        core/src/commonMain/kotlin/com/eventverse/app/shared/common/*) return 0 ;;
        core/src/commonMain/kotlin/com/eventverse/app/shared/json/*) return 0 ;;
        core/src/commonMain/kotlin/com/eventverse/app/domain/common/*) return 0 ;;
    esac
    return 1
}

# ── 2. File yang punya test → nama kelasnya, plus nama berkasnya sendiri ──────
# Nama berkas ikut dikumpulkan karena ada test yang mengikat ke file, bukan ke
# kelas — mis. `InvoiceTemplateSeedDecodeTest` menyebut nama migrasi V32.
#
# HANYA deklarasi top-level (kolom 0) yang diambil. Kalau anggota kelas ikut
# diambil, nama generik seperti `x`, `y`, `width`, `align`, atau `tint` akan
# menjadi token dan polanya cocok ke SEMUA file test — pemilihan jadi tidak
# menyaring apa pun. Jadi kedalaman indentasi dipakai sebagai penanda batas.
tokens_of_file() {
    local file="$1"
    basename "$file" | sed -E 's/\.[A-Za-z0-9]+$//'

    [ -f "$file" ] || return 0

    grep -oE '^(public |private |internal |expect |actual |abstract |open |sealed |data |value |inline |suspend |operator |override |tailrec |external |annotation |enum |const )*(class|object|interface|fun|val|var|typealias) +[A-Za-z0-9_]+' "$file" \
        | sed -E 's/.*[[:space:]]+([A-Za-z0-9_]+)$/\1/' \
        | grep -E '^[A-Za-z0-9_]{3,}$'
}

# ── 3. Pencarian referensi lintas modul ───────────────────────────────────────
# Satu grep untuk semua token (alternation), bukan N grep terpisah.
#
# Pencocokan sengaja memakai SUBSTRING, bukan batas kata. Alasannya bukan
# kemalasan, tapi recall: konvensi repo ini menaruh satuan sebagai AKHIRAN nama
# (`marginMm10`, `widthMm10`, `heightMm10`). Test yang menulis `marginMm10` tidak
# menyebut kata `Mm10` secara utuh, jadi pencocokan batas-kata akan melewatkannya
# — padahal properti itu bertipe `Mm10` dan ikut rusak kalau value class-nya
# berubah. Dalam pemilihan test, melewatkan satu test yang rusak jauh lebih mahal
# daripada menjalankan beberapa test tambahan yang ternyata masih hijau.
test_classes_referencing() {
    local file="$1"
    local pattern
    pattern="$(tokens_of_file "$file" | sort -u | grep -v '^$' | paste -sd'|' -)"
    [ -n "$pattern" ] || return 0

    # shellcheck disable=SC2086
    grep -rlE --include="$TEST_GLOB" "($pattern)" $ALL_TEST_ROOTS 2>/dev/null
}

# ── 4. Path test → FQN → task Gradle ─────────────────────────────────────────
# Konvensi paket Kotlin di repo ini mengikuti struktur folder setelah /kotlin/.
fqn_of_test_file() {
    local p="$1"
    case "$p" in
        */kotlin/*.kt) ;;
        *) return 0 ;;
    esac
    p="${p#*/kotlin/}"
    p="${p%.kt}"
    echo "$p" | tr '/' '.'
}

task_of_test_file() {
    case "$1" in
        core/src/*)       echo ":core:jvmTest" ;;
        app/shared/src/*) echo ":app:shared:jvmTest" ;;
        server/src/test/*) echo ":server:test" ;;
        *)                echo "" ;;
    esac
}

count_test_files() {
    find "$1" -name "$TEST_GLOB" -path '*Test*' 2>/dev/null | wc -l | tr -d ' '
}


# ── 5. Klasifikasi file ───────────────────────────────────────────────────────
is_test_path() {
    case "$1" in
        */src/test/*|*/src/*Test/*) return 0 ;;
    esac
    return 1
}

is_ignorable() {
    case "$1" in
        docs/*|*.md|.cline/*|.agents/*|agents/*|tools/*) return 0 ;;
        *.png|*.jpg|*.txt|*.lock) return 0 ;;
        # Shell script adalah tooling pengembangan, bukan kode yang diuji. Tanpa
        # baris ini, `dev.sh` menghasilkan token "dev" yang lalu cocok dengan
        # ModuleDev*, ProspectDev*, dan bahkan kata "device" di dalam test.
        *.sh) return 0 ;;
    esac
    return 1
}

modules_of_file() {
    case "$1" in
        core/*)       echo "core" ;;
        app/shared/*) echo "shared" ;;
        server/*)     echo "server" ;;
        *)            echo "" ;;
    esac
}

# ── 6. Kumpulkan file yang berubah ────────────────────────────────────────────
collect_changed() {
    if [ -n "$BASE_REF" ]; then
        # Perubahan yang sudah di-commit sejak titik pisah dengan base…
        git diff --name-only "$BASE_REF"...HEAD 2>/dev/null
    fi
    # …plus semua yang belum di-commit (staged maupun belum), termasuk file baru.
    git diff --name-only HEAD 2>/dev/null
    git ls-files --others --exclude-standard 2>/dev/null
}

# ── 7. Jalankan Gradle ────────────────────────────────────────────────────────
FAILED=0

run_gradle() {
    local task="$1"; shift
    local args=()
    for fqn in "$@"; do
        args+=(--tests "$fqn")
    done

    [ "$MODE" = "list" ] && return 0

    if ! ./gradlew --console=plain "$task" ${args[@]+"${args[@]}"}; then
        FAILED=1
    fi
}

# Menerima: task, lalu daftar FQN (satu per argumen).
run_one_module() {
    local task="$1"; shift
    local n="$#"
    local total=""

    # Modul tanpa test terpilih tidak dipanggil sama sekali — memanggil Gradle
    # hanya untuk bilang "0 test" tetap memakan waktu satu startup daemon.
    [ "$n" -eq 0 ] && return 0

    case "$task" in
        ":core:jvmTest")       total="$(count_test_files "$CORE_SRC")" ;;
        ":app:shared:jvmTest") total="$(count_test_files "$SHARED_SRC")" ;;
        ":server:test")        total="$(count_test_files "$SERVER_SRC")" ;;
    esac

    # Kalau hampir seluruh modul ikut terpilih, satu task modul penuh lebih murah
    # dan menghindari baris perintah yang panjangnya ratusan argumen.
    if [ "$n" -ge "$WHOLE_MODULE_THRESHOLD" ]; then
        echo -e "  ${CYAN}▶${RESET} ${BOLD}$task${RESET} ${DIM}(modul penuh — $n dari $total kelas terpilih)${RESET}"
        run_gradle "$task"
        return 0
    fi

    echo -e "  ${CYAN}▶${RESET} ${BOLD}$task${RESET} ${DIM}($n dari $total kelas)${RESET}"
    for fqn in "$@"; do
        echo -e "      ${DIM}· $fqn${RESET}"
    done
    run_gradle "$task" "$@"
}

# Jatuhkan pilihan test secara berurutan; test yang menyebut file berubah dipilih.
# Menerima daftar file test yang cocok.
select_tests_from_files() {
    local hits
    hits="$(echo "$1" | sort -u | grep -v '^$')"
    local fqn task
    while IFS= read -r hit; do
        [ -n "$hit" ] || continue
        fqn="$(fqn_of_test_file "$hit")"
        task="$(task_of_test_file "$hit")"
        [ -n "$fqn" ] || continue
        [ -n "$task" ] || continue
        case "$task" in
            ":core:jvmTest")       CORE_TESTS+=("$fqn") ;;
            ":app:shared:jvmTest") SHARED_TESTS+=("$fqn") ;;
            ":server:test")        SERVER_TESTS+=("$fqn") ;;
        esac
    done <<< "$hits"
}


# Dedupe hasil gabungan dari beberapa file berubah menjadi sebuah array.
# Bash 3.2 (bawaan macOS) tidak punya associative array, jadi identitas array
# dikirim sebagai nama variabel lalu disusun ulang lewat eval — triknya terkurung
# di fungsi ini saja.
dedupe_into_array() {
    local out_name="$1"; shift
    local joined
    joined="$(printf '%s\n' "$@" 2>/dev/null | sort -u | grep -v '^$')"

    eval "$out_name=()"
    local line
    while IFS= read -r line; do
        [ -n "$line" ] || continue
        eval "$out_name+=(\"\$line\")"
    done <<< "$joined"
}

# ── 8. Alur utama ─────────────────────────────────────────────────────────────
if [ "$MODE" = "full" ]; then
    echo -e "${BOLD}${CYAN}🧪 Seluruh suite test — WeMade ERP${RESET}"
    echo -e "${DIM}.env dimuat: DB_PORT=${DB_PORT:-<tidak diset>}${RESET}\n"
    echo -e "  ${CYAN}▶${RESET} ${BOLD}:core:jvmTest${RESET} ${DIM}(modul penuh)${RESET}"
    run_gradle ":core:jvmTest"
    echo -e "  ${CYAN}▶${RESET} ${BOLD}:app:shared:jvmTest${RESET} ${DIM}(modul penuh)${RESET}"
    run_gradle ":app:shared:jvmTest"
    echo -e "  ${CYAN}▶${RESET} ${BOLD}:server:test${RESET} ${DIM}(modul penuh)${RESET}"
    run_gradle ":server:test"
    if [ "$FAILED" -eq 0 ]; then
        echo -e "\n${GREEN}${BOLD}✅ Seluruh suite lulus.${RESET}"
    else
        echo -e "\n${RED}${BOLD}❌ Ada test yang gagal.${RESET}"
    fi
    exit "$FAILED"
fi

CHANGED="$(collect_changed | sort -u | grep -v '^$')"

if [ -z "$CHANGED" ]; then
    echo -e "${BOLD}${CYAN}🧪 Test selektif — WeMade ERP${RESET}"
    echo -e "${GREEN}✅ Tidak ada file yang berubah${RESET} ${DIM}(${BASE_REF:-HEAD})${RESET}"
    echo -e "${DIM}   Tidak ada yang perlu dijalankan.${RESET}"
    exit 0
fi

CORE_TESTS=()
SHARED_TESTS=()
SERVER_TESTS=()
CODE_TOUCHED=0
FOUNDATIONAL_HIT=""
TOUCHED_MODULES=""
SKIPPED_COUNT=0

while IFS= read -r file; do
    [ -n "$file" ] || continue

    if is_ignorable "$file"; then
        SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
        continue
    fi

    # Konfigurasi build & util lintas domain: tidak ada daftar test yang bisa
    # dipersempit dengan jujur. Lebih baik seluruh suite daripada rasa aman palsu.
    if is_foundational "$file"; then
        FOUNDATIONAL_HIT="$file"
        break
    fi

    CODE_TOUCHED=1

    module_name="$(modules_of_file "$file")"
    if [ -n "$module_name" ]; then
        TOUCHED_MODULES="$TOUCHED_MODULES$module_name"$'\n'
    fi

    if is_test_path "$file"; then
        # File test yang diubah: jalankan test itu sendiri.
        select_tests_from_files "$file"
    elif [ -f "$file" ]; then
        select_tests_from_files "$(test_classes_referencing "$file")"
    fi
done <<< "$CHANGED"

dedupe_into_array CORE_TESTS   ${CORE_TESTS[@]+"${CORE_TESTS[@]}"}
dedupe_into_array SHARED_TESTS ${SHARED_TESTS[@]+"${SHARED_TESTS[@]}"}
dedupe_into_array SERVER_TESTS ${SERVER_TESTS[@]+"${SERVER_TESTS[@]}"}

TOTAL_SELECTED=$(( ${#CORE_TESTS[@]} + ${#SHARED_TESTS[@]} + ${#SERVER_TESTS[@]} ))


# ── 9. Laporkan rencana, lalu jalankan ────────────────────────────────────────
echo -e "${BOLD}${CYAN}🧪 Test selektif — WeMade ERP${RESET}"
echo -e "${DIM}Dibandingkan dengan : ${BASE_REF:-HEAD}${RESET}"
echo -e "${DIM}File berubah        : $(printf '%s\n' "$CHANGED" | grep -c .) (${SKIPPED_COUNT} diabaikan: dokumen/tooling)${RESET}"
echo

if [ -n "$FOUNDATIONAL_HIT" ]; then
    echo -e "${YELLOW}⚠️  ${FOUNDATIONAL_HIT}${RESET}"
    echo -e "${YELLOW}    File ini menyentuh konfigurasi build / util lintas domain, jadi daftar${RESET}"
    echo -e "${YELLOW}    test tidak bisa dipersempit dengan jujur. Menjalankan seluruh suite.${RESET}\n"
elif [ "$CODE_TOUCHED" -eq 0 ]; then
    echo -e "${GREEN}✅ Tidak ada file kode yang berubah — tidak ada test yang perlu dijalankan.${RESET}"
    echo -e "${DIM}   Yang berubah hanya dokumen/tooling.${RESET}"
    exit 0
elif [ "$TOTAL_SELECTED" -eq 0 ]; then
    echo -e "${YELLOW}⚠️  Tidak ada test yang menyebut file yang berubah.${RESET}"
    echo -e "${YELLOW}    Itu bisa berarti kode barunya memang belum punya test — modul yang${RESET}"
    echo -e "${YELLOW}    tersentuh tetap dijalankan penuh supaya tidak memberi rasa aman palsu.${RESET}\n"
fi

if [ -n "$FOUNDATIONAL_HIT" ]; then
    run_gradle ":core:jvmTest"
    run_gradle ":app:shared:jvmTest"
    run_gradle ":server:test"
elif [ "$TOTAL_SELECTED" -eq 0 ] && [ "$CODE_TOUCHED" -eq 1 ]; then
    # Fallback: modul yang tersentuh, dijalankan penuh.
    for m in $(printf '%s\n' "$TOUCHED_MODULES" | sort -u); do
        case "$m" in
            core)   echo -e "  ${CYAN}▶${RESET} ${BOLD}:core:jvmTest${RESET} ${DIM}(fallback modul penuh)${RESET}";  run_gradle ":core:jvmTest" ;;
            shared) echo -e "  ${CYAN}▶${RESET} ${BOLD}:app:shared:jvmTest${RESET} ${DIM}(fallback modul penuh)${RESET}"; run_gradle ":app:shared:jvmTest" ;;
            server) echo -e "  ${CYAN}▶${RESET} ${BOLD}:server:test${RESET} ${DIM}(fallback modul penuh)${RESET}";  run_gradle ":server:test" ;;
        esac
    done
else
    run_one_module ":core:jvmTest"       ${CORE_TESTS[@]+"${CORE_TESTS[@]}"}
    run_one_module ":app:shared:jvmTest" ${SHARED_TESTS[@]+"${SHARED_TESTS[@]}"}
    run_one_module ":server:test"        ${SERVER_TESTS[@]+"${SERVER_TESTS[@]}"}
fi

echo
if [ "$MODE" = "list" ]; then
    echo -e "${CYAN}${BOLD}ℹ️  Mode --list: tidak ada test yang benar-benar dijalankan.${RESET}"
    exit 0
fi

if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}${BOLD}✅ Test terpilih lulus.${RESET}"
    echo -e "${DIM}   Ini BUKAN bukti seluruh suite hijau: build/test-results/ kini hanya berisi${RESET}"
    echo -e "${DIM}   test yang tadi dipilih. Jalankan ./dev.sh test sebelum push.${RESET}"
else
    echo -e "${RED}${BOLD}❌ Ada test terpilih yang gagal.${RESET}"
fi
exit "$FAILED"

