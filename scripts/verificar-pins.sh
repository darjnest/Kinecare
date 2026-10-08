#!/usr/bin/env bash
# Verifica que la cadena de certificados que sirven hoy las Cloud Functions contiene al menos una
# clave pineada en la app (core/network/.../security/CertificatePins.kt).
#
# Un pin que ya no coincide con lo que sirve Google deja a TODAS las versiones instaladas sin poder
# llamar a las funciones (pagos, verificacion, reservas) hasta que se actualicen. Este script avisa
# antes: corre a mano, en cada PR que toca los pins y cada semana (.github/workflows/pins.yml).
#
# Uso:  scripts/verificar-pins.sh [host ...]
#       (sin argumentos: el host de QA y el de produccion)
# Requiere: openssl, awk, acceso de red a los hosts. Sale con 1 si algun host no coincide.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCHIVO_PINS="$RAIZ/core/network/src/main/kotlin/com/darjnest/kinecare/core/network/security/CertificatePins.kt"

if [ "$#" -gt 0 ]; then
  HOSTS=("$@")
else
  HOSTS=("us-central1-kinecare-cl-qa.cloudfunctions.net" "us-central1-kinecare-cl.cloudfunctions.net")
fi

# Los pins se leen del codigo (una sola fuente de verdad): "sha256/<44 caracteres base64>".
PINS="$(grep -o 'sha256/[A-Za-z0-9+/]\{43\}=' "$ARCHIVO_PINS" | sort -u)"
if [ -z "$PINS" ]; then
  echo "ERROR: no encontre pins en $ARCHIVO_PINS" >&2
  exit 1
fi
echo "Pins en la app: $(echo "$PINS" | wc -l | tr -d ' ')"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
fallos=0

for host in "${HOSTS[@]}"; do
  echo
  echo "== $host"
  rm -f "$TMP"/cert_*.pem
  if ! echo | openssl s_client -connect "$host:443" -servername "$host" -showcerts 2>/dev/null >"$TMP/salida.txt"; then
    echo "   ERROR: no pude conectar con $host" >&2
    fallos=$((fallos + 1))
    continue
  fi
  awk -v dir="$TMP" '/BEGIN CERTIFICATE/{n++; f=sprintf("%s/cert_%02d.pem", dir, n)} n>0{print > f}' "$TMP/salida.txt"

  coincidencias=0
  total=0
  for cert in "$TMP"/cert_*.pem; do
    [ -e "$cert" ] || continue
    total=$((total + 1))
    sujeto="$(openssl x509 -in "$cert" -noout -subject 2>/dev/null | sed 's/^subject= *//')"
    pin="sha256/$(openssl x509 -in "$cert" -pubkey -noout | openssl pkey -pubin -outform der 2>/dev/null | openssl dgst -sha256 -binary | openssl base64)"
    if echo "$PINS" | grep -qxF "$pin"; then
      echo "   [PIN OK] $pin  $sujeto"
      coincidencias=$((coincidencias + 1))
    else
      echo "   [      ] $pin  ${sujeto:0:60}"
    fi
  done

  if [ "$total" -eq 0 ]; then
    echo "   ERROR: $host no devolvio certificados" >&2
    fallos=$((fallos + 1))
  elif [ "$coincidencias" -eq 0 ]; then
    echo "   ERROR: ningun certificado de la cadena coincide con los pins de la app." >&2
    echo "          La app NO podria llamar a $host. Agrega el pin de la nueva CA raiz y publica una" >&2
    echo "          version ANTES de que el servidor cambie (ver docs/ARCHITECTURE.md#seguridad)." >&2
    fallos=$((fallos + 1))
  else
    echo "   OK: $coincidencias de $total certificados coinciden con un pin"
  fi
done

echo
if [ "$fallos" -gt 0 ]; then
  echo "FALLO: $fallos host(s) sin coincidencia." >&2
  exit 1
fi
echo "Todo en orden."
