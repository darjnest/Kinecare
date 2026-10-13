#!/usr/bin/env bash
# Interruptor remoto del certificate pinning: genera la clave y firma el override que se publica
# en Firebase Remote Config. Ver docs/ARCHITECTURE.md#seguridad (incluye cuando usarlo).
#
#   scripts/pinning-override.sh generar-clave <archivo-clave-privada.pem>
#       Crea la clave privada (ECDSA P-256, permisos 600) y imprime la CLAVE PUBLICA que hay que pegar
#       en CLAVE_PUBLICA_OVERRIDE_PINNING (core/network/.../security/PinningOverride.kt).
#       Guarda el archivo FUERA del repositorio y respaldado (gestor de contrasenas / boveda): quien
#       la tenga puede apagar el pinning de todas las apps durante hasta 30 dias. Si se pierde,
#       el interruptor queda inutilizable hasta publicar una version con otra clave.
#
#   scripts/pinning-override.sh publica <archivo-clave-privada.pem>
#       Vuelve a imprimir la clave publica.
#
#   scripts/pinning-override.sh firmar <archivo-clave-privada.pem> <dias 1..30>
#       Firma un override valido desde AHORA durante <dias> dias e imprime el JSON que va como valor
#       del parametro `cf_pinning_override` de Remote Config.
set -euo pipefail

CLAVE_RC="cf_pinning_override"
MAX_DIAS=30
VERSION=1

uso() { sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
requerir() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: falta '$1'" >&2; exit 1; }; }
requerir openssl

publica() { openssl ec -in "$1" -pubout -outform DER 2>/dev/null | openssl base64 -A; echo; }

fecha_legible() { # epoch -> fecha UTC legible (macOS o GNU)
  date -u -r "$1" '+%Y-%m-%d %H:%M UTC' 2>/dev/null || date -u -d "@$1" '+%Y-%m-%d %H:%M UTC'
}

cmd="${1:-}"; shift || true
case "$cmd" in
  generar-clave)
    [ "$#" -eq 1 ] || uso
    archivo="$1"
    [ ! -e "$archivo" ] || { echo "ERROR: $archivo ya existe; no lo piso" >&2; exit 1; }
    case "$(cd "$(dirname "$archivo")" && pwd)/" in
      "$(git rev-parse --show-toplevel 2>/dev/null || echo /nonexistent)"/*)
        echo "ERROR: ese archivo quedaria dentro del repositorio. Guardalo fuera." >&2; exit 1;;
    esac
    umask 077
    openssl ecparam -name prime256v1 -genkey -noout -out "$archivo"
    chmod 600 "$archivo"
    echo "Clave privada creada: $archivo (respaldala; NO la subas al repositorio)"
    echo
    echo "Clave PUBLICA para CLAVE_PUBLICA_OVERRIDE_PINNING:"
    publica "$archivo"
    ;;
  publica)
    [ "$#" -eq 1 ] && [ -f "$1" ] || uso
    publica "$1"
    ;;
  firmar)
    [ "$#" -eq 2 ] && [ -f "$1" ] || uso
    clave="$1"; dias="$2"
    case "$dias" in ''|*[!0-9]*) echo "ERROR: <dias> debe ser un entero" >&2; exit 1;; esac
    if [ "$dias" -lt 1 ] || [ "$dias" -gt "$MAX_DIAS" ]; then
      echo "ERROR: <dias> debe estar entre 1 y $MAX_DIAS (la app rechaza ventanas mayores)" >&2; exit 1
    fi
    desde="$(date +%s)"
    hasta=$((desde + dias * 86400))
    mensaje="kinecare-pinning-override|v${VERSION}|${desde}|${hasta}"
    firma="$(printf '%s' "$mensaje" | openssl dgst -sha256 -sign "$clave" | openssl base64 -A)"
    echo "Override firmado: pinning DESACTIVADO desde $(fecha_legible "$desde") hasta $(fecha_legible "$hasta")" >&2
    echo "Pegalo como valor del parametro \"$CLAVE_RC\" en Remote Config y publica:" >&2
    printf '{"v":%s,"desde":%s,"hasta":%s,"firma":"%s"}\n' "$VERSION" "$desde" "$hasta" "$firma"
    ;;
  *) uso;;
esac
