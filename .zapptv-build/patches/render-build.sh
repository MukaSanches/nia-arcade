#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
TOOLS="$ROOT/.render-tools"
JDK_DIR="$TOOLS/jdk"
GRADLE_DIR="$TOOLS/gradle"
SDK_DIR="$TOOLS/android-sdk"
PUBLIC_DIR="$ROOT/public"
RELEASE_DIR="$ROOT/.release"
KEYSTORE="$RELEASE_DIR/sanchestv-release.jks"

mkdir -p "$TOOLS" "$PUBLIC_DIR" "$RELEASE_DIR"

echo "== SANCHESTV 2.9.1 signed release build =="

: "${SANCHESTV_KEYSTORE_B64:?Missing SANCHESTV_KEYSTORE_B64}"
: "${SANCHESTV_KEYSTORE_PASSWORD:?Missing SANCHESTV_KEYSTORE_PASSWORD}"
: "${SANCHESTV_KEY_PASSWORD:?Missing SANCHESTV_KEY_PASSWORD}"
: "${SANCHESTV_KEY_ALIAS:?Missing SANCHESTV_KEY_ALIAS}"

printf '%s' "$SANCHESTV_KEYSTORE_B64" | base64 -d > "$KEYSTORE"
chmod 600 "$KEYSTORE"
export SANCHESTV_KEYSTORE_PATH="$KEYSTORE"

rm -rf "$JDK_DIR"
mkdir -p "$JDK_DIR"
curl -fL --retry 4 --retry-delay 3   "https://corretto.aws/downloads/latest/amazon-corretto-17-x64-linux-jdk.tar.gz"   -o "$TOOLS/jdk17.tar.gz"
tar -xzf "$TOOLS/jdk17.tar.gz" -C "$JDK_DIR" --strip-components=1
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

rm -rf "$GRADLE_DIR"
curl -fL --retry 4 --retry-delay 3   "https://services.gradle.org/distributions/gradle-8.13-bin.zip"   -o "$TOOLS/gradle.zip"
unzip -q "$TOOLS/gradle.zip" -d "$TOOLS"
mv "$TOOLS/gradle-8.13" "$GRADLE_DIR"
export PATH="$GRADLE_DIR/bin:$PATH"
gradle --version

rm -rf "$SDK_DIR" "$TOOLS/android-cmd"
mkdir -p "$SDK_DIR/cmdline-tools"
curl -fL --retry 4 --retry-delay 3   "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"   -o "$TOOLS/android-tools.zip"
unzip -q "$TOOLS/android-tools.zip" -d "$TOOLS/android-cmd"
mv "$TOOLS/android-cmd/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$SDK_DIR/cmdline-tools/latest/bin:$SDK_DIR/platform-tools:$PATH"

yes | sdkmanager --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true
sdkmanager --sdk_root="$SDK_DIR"   "platforms;android-36"   "build-tools;35.0.0"   "platform-tools"

echo "== IPTV first-run bootstrap =="
BOOTSTRAP_DIR="$ROOT/app/src/main/assets/bootstrap"
rm -rf "$BOOTSTRAP_DIR"
mkdir -p "$BOOTSTRAP_DIR"

fetch_bootstrap() {
  local id="$1"
  local url="$2"
  local minimum="$3"
  local required="$4"
  local target="$BOOTSTRAP_DIR/playlist-$id.m3u"
  local temp="$target.tmp"

  echo "Bootstrap $id <- $url"
  if curl -fL --retry 4 --retry-delay 2 --connect-timeout 15 --max-time 120 "$url" -o "$temp"; then
    local first_line
    first_line="$(head -n 1 "$temp" | tr -d '\r\n\357\273\277')"
    local count
    count="$(grep -c '^#EXTINF' "$temp" || true)"
    if [[ "$first_line" == \#EXTM3U* ]] && (( count >= minimum )); then
      mv "$temp" "$target"
      echo "Bootstrap $id: $count canais"
      return 0
    fi
    echo "Bootstrap $id inválido: header='$first_line' canais=$count"
  else
    echo "Bootstrap $id indisponível no build"
  fi

  rm -f "$temp"
  if [[ "$required" == "required" ]]; then
    echo "ERROR: bootstrap obrigatório $id falhou"
    exit 1
  fi
}

# Catálogo amplo com streams que passaram pelo health check upstream.
fetch_bootstrap "dearbulut-online" \
  "https://dearbulut.github.io/iptv/playlists/online.m3u" 1000 required

# Camadas regionais/lusófonas empacotadas quando disponíveis. Elas aparecem
# imediatamente e também fornecem metadados/alternativas ao catálogo global.
fetch_bootstrap "iptv-org-br" \
  "https://iptv-org.github.io/iptv/countries/br.m3u" 20 optional
fetch_bootstrap "iptv-com-br" \
  "https://github.com/iptv-com/iptv/raw/refs/heads/main/lists/brazil.m3u" 5 optional
fetch_bootstrap "pluto-br" \
  "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_br.m3u" 5 optional
fetch_bootstrap "m3upt-tv" \
  "https://m3upt.com/iptv" 20 optional
fetch_bootstrap "iptv-org-pt" \
  "https://iptv-org.github.io/iptv/languages/por.m3u" 20 optional
fetch_bootstrap "free-tv" \
  "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8" 100 optional

BOOTSTRAP_FILES="$(find "$BOOTSTRAP_DIR" -maxdepth 1 -type f -name 'playlist-*.m3u' | wc -l | tr -d ' ')"
BOOTSTRAP_CHANNELS="$(grep -h '^#EXTINF' "$BOOTSTRAP_DIR"/playlist-*.m3u | wc -l | tr -d ' ')"
echo "Bootstrap final: $BOOTSTRAP_FILES fontes • $BOOTSTRAP_CHANNELS entradas M3U"
test "$BOOTSTRAP_CHANNELS" -ge 1000

echo "== Subtitle smoke test: Wikimedia Commons TimedText =="
SUBTITLE_SMOKE="$(
  curl -fsSL --retry 3 --retry-delay 2 --get \
    --data-urlencode "action=timedtext" \
    --data-urlencode "title=File:Krazy Kat Bugologist 1916 silent.ogv" \
    --data-urlencode "lang=pt" \
    --data-urlencode "trackformat=vtt" \
    "https://commons.wikimedia.org/w/api.php"
)"
printf '%s' "$SUBTITLE_SMOKE" | grep -qi 'WEBVTT'
printf '%s' "$SUBTITLE_SMOKE" | grep -q -- '-->'
echo "Commons TimedText subtitle smoke: OK"

echo "== Infinite Cinema provider smoke tests =="
curl -fsSL --retry 3 --retry-delay 2 \
  "https://images-api.nasa.gov/search?media_type=video&page=1" \
  | grep -q '"collection"'
echo "NASA video API smoke: OK"

curl -fsSL --retry 3 --retry-delay 2 \
  "https://www.loc.gov/film-and-videos/?fo=json&c=1&sp=1" \
  | grep -q '"results"'
echo "Library of Congress film API smoke: OK"

curl -fsSL --retry 3 --retry-delay 2 \
  "https://archive.org/advancedsearch.php?q=collection%3Aopensource_movies%20AND%20mediatype%3Amovies&fl%5B%5D=identifier&rows=1&page=1&output=json" \
  | grep -q '"response"'
echo "Internet Archive discovery smoke: OK"

gradle --no-daemon --stacktrace testDebugUnitTest
gradle --no-daemon --stacktrace assembleRelease

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
test -s "$APK"

AAPT="$SDK_DIR/build-tools/35.0.0/aapt"
APKSIGNER="$SDK_DIR/build-tools/35.0.0/apksigner"

"$AAPT" dump badging "$APK" | tee "$PUBLIC_DIR/apk-badging.txt"
grep -q "package: name='com.mukasanches.zapptv'" "$PUBLIC_DIR/apk-badging.txt"
grep -q "versionCode='14'" "$PUBLIC_DIR/apk-badging.txt"
grep -q "versionName='2.9.1'" "$PUBLIC_DIR/apk-badging.txt"

if grep -q "application-debuggable" "$PUBLIC_DIR/apk-badging.txt"; then
  echo "ERROR: release APK is debuggable"
  exit 1
fi

"$APKSIGNER" verify --verbose --print-certs "$APK" | tee "$PUBLIC_DIR/signature-verification.txt"
grep -q "Verifies" "$PUBLIC_DIR/signature-verification.txt"

cp "$APK" "$PUBLIC_DIR/SANCHESTV-2.9.1-release.apk"
(
  cd "$PUBLIC_DIR"
  sha256sum "SANCHESTV-2.9.1-release.apk" > "SANCHESTV-2.9.0-release.sha256"
)

APK_SIZE="$(du -h "$PUBLIC_DIR/SANCHESTV-2.9.1-release.apk" | cut -f1)"
APK_SHA="$(cut -d' ' -f1 "$PUBLIC_DIR/SANCHESTV-2.9.0-release.sha256")"
CERT_SHA="$("$APKSIGNER" verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1)"

cat > "$PUBLIC_DIR/release-manifest.json" <<JSON
{
  "versionCode": 14,
  "versionName": "2.9.1",
  "apkUrl": "https://sanchestv-release.onrender.com/SANCHESTV-2.9.1-release.apk",
  "sha256": "$APK_SHA",
  "certificateSha256": "$CERT_SHA",
  "notes": "SANCHESTV 2.9.1: correção crítica do catálogo IPTV no primeiro uso, snapshot de canais verificados embutido no APK, fontes FAST atualizadas, deduplicação corrigida e refresh resiliente; Infinite Cinema 2.9 preservado."
}
JSON

cat > "$PUBLIC_DIR/index.html" <<HTML
<!doctype html>
<html lang="pt-BR">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>SANCHESTV 2.9.1 Release</title>
  <style>
    :root{color-scheme:dark}
    body{margin:0;background:#05070a;color:#f7fafc;font-family:system-ui,-apple-system,Segoe UI,sans-serif;min-height:100vh;display:grid;place-items:center}
    main{width:min(760px,calc(100% - 48px));background:#0d1218;border:1px solid #1a2a35;border-radius:30px;padding:40px;box-shadow:0 24px 100px #000a}
    .brand{color:#2ed3ff;font-weight:900;letter-spacing:.08em}h1{font-size:46px;margin:8px 0}.sub{color:#a8b4c2;margin:0 0 30px;font-size:18px}
    a.button{display:inline-block;background:#2ed3ff;color:#061019;text-decoration:none;font-weight:850;padding:16px 24px;border-radius:15px}
    .verified{display:inline-block;margin-left:10px;padding:8px 12px;border-radius:12px;background:#15351f;color:#8dffad;font-weight:800}
    .meta{margin-top:28px;color:#a8b4c2;font-size:14px;line-height:1.8}.hash{font-family:ui-monospace,monospace;word-break:break-all;color:#dff7ff}
  </style>
</head>
<body>
  <main>
    <div class="brand">S▶ SANCHES TV</div>
    <h1>SANCHESTV 2.9.1</h1>
    <p class="sub">Android TV / Google TV • Release assinada • R8 otimizado</p>
    <a class="button" href="./SANCHESTV-2.9.1-release.apk">Baixar APK 2.9.1</a>
    <span class="verified">ASSINATURA VERIFICADA</span>
    <div class="meta">
      <div>Tamanho: $APK_SIZE</div>
      <div>Package: com.mukasanches.zapptv</div>
      <div>VersionCode: 14 • VersionName: 2.9.1</div>
      <div>APK SHA-256:</div>
      <div class="hash">$APK_SHA</div>
      <div>Certificado SHA-256:</div>
      <div class="hash">$CERT_SHA</div>
    </div>
  </main>
</body>
</html>
HTML

rm -f "$KEYSTORE"

echo "Signed release complete: $PUBLIC_DIR/SANCHESTV-2.9.1-release.apk"
