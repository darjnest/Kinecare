#!/usr/bin/env bash
# Prueba storage.rules contra los emuladores de Auth y Storage: quien puede subir, leer y borrar la
# foto de perfil del cliente y del profesional (solo el dueno, solo JPEG, < 5 MB, sin deletes) y las certificaciones del profesional.
#
# Uso:  scripts/probar-storage-rules.sh
# Requiere: node, java, curl, python3 y `npm ci` hecho en functions/ (trae firebase-tools). La
# primera vez el CLI descarga el runtime de reglas de Storage. Usa puertos propios (9199/9299) y un
# firebase.json temporal para no tocar el del repo. Sale con 1 si alguna prueba falla.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIREBASE="$RAIZ/functions/node_modules/.bin/firebase"
if [ ! -x "$FIREBASE" ]; then
  echo "ERROR: falta firebase-tools; corre 'npm ci' en functions/" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cp "$RAIZ/storage.rules" "$TMP/storage.rules"
cat > "$TMP/firebase.json" <<'JSON'
{
  "storage": { "rules": "storage.rules" },
  "emulators": {
    "auth": { "port": 9199 },
    "storage": { "port": 9299 },
    "ui": { "enabled": false },
    "singleProjectMode": true
  }
}
JSON

cat > "$TMP/pruebas.sh" <<'PRUEBAS'
#!/usr/bin/env bash
set -u
BUCKET=demo-kinecare.appspot.com
AUTH=http://127.0.0.1:9199
ST=http://127.0.0.1:9299
FALLAS=0

codificar() { python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1"; }
campo() { python3 -c 'import sys,json;print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }
registrar() { curl -s -X POST "$AUTH/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake" -H 'Content-Type: application/json' -d "{\"email\":\"$1@kinecare.test\",\"password\":\"123456\"}"; }
subir() { curl -s -o /dev/null -w '%{http_code}' -X POST "$ST/v0/b/$BUCKET/o?name=$(codificar "$2")" ${1:+-H "Authorization: Firebase $1"} -H "Content-Type: $4" --data-binary @"$3"; }
leer() { curl -s -o /dev/null -w '%{http_code}' "$ST/v0/b/$BUCKET/o/$(codificar "$2")?alt=media" ${1:+-H "Authorization: Firebase $1"}; }
borrar() { curl -s -o /dev/null -w '%{http_code}' -X DELETE "$ST/v0/b/$BUCKET/o/$(codificar "$2")" -H "Authorization: Firebase $1"; }
verificar() {
  if [ "$2" == "$3" ]; then echo "OK    $1 ($2)"; else echo "FALLA $1 (esperado $3, obtuvo $2)"; FALLAS=$((FALLAS + 1)); fi
}

A="$(registrar dueno)"; UA="$(echo "$A" | campo localId)"; TA="$(echo "$A" | campo idToken)"
B="$(registrar ajeno)"; TB="$(echo "$B" | campo idToken)"
head -c 1000 /dev/urandom > chica.jpg
head -c 5300000 /dev/urandom > grande.jpg
head -c 3000 /dev/urandom > cert.pdf
head -c 10500000 /dev/urandom > cert-grande.pdf

for carpeta in clientes profesionales; do
  F="$carpeta/$UA/foto-perfil.jpg"
  verificar "$carpeta: el dueno sube un JPEG" "$(subir "$TA" "$F" chica.jpg image/jpeg)" 200
  verificar "$carpeta: el dueno la reemplaza" "$(subir "$TA" "$F" chica.jpg image/jpeg)" 200
  verificar "$carpeta: otro usuario no sube" "$(subir "$TB" "$F" chica.jpg image/jpeg)" 403
  verificar "$carpeta: sin sesion no se sube" "$(subir "" "$F" chica.jpg image/jpeg)" 403
  verificar "$carpeta: un PNG se rechaza" "$(subir "$TA" "$F" chica.jpg image/png)" 403
  verificar "$carpeta: un JPEG de 5 MB o mas se rechaza" "$(subir "$TA" "$F" grande.jpg image/jpeg)" 403
  verificar "$carpeta: el dueno no la borra" "$(borrar "$TA" "$F")" 403
  verificar "$carpeta: otro nombre de archivo se rechaza" "$(subir "$TA" "$carpeta/$UA/otra.jpg" chica.jpg image/jpeg)" 403
done
verificar "clientes: la lee su dueno" "$(leer "$TA" "clientes/$UA/foto-perfil.jpg")" 200
verificar "clientes: no la lee otro usuario" "$(leer "$TB" "clientes/$UA/foto-perfil.jpg")" 403
verificar "clientes: no la lee un anonimo" "$(leer "" "clientes/$UA/foto-perfil.jpg")" 403
verificar "profesionales: la lectura es publica" "$(leer "" "profesionales/$UA/foto-perfil.jpg")" 200

# Certificaciones del profesional: el dueno solo CREA (PDF, JPEG o PNG, < 10 MB); no lee, reemplaza ni borra.
C="profesionales/$UA/certificaciones/c1.pdf"
verificar "certificaciones: el dueno sube un PDF" "$(subir "$TA" "$C" cert.pdf application/pdf)" 200
verificar "certificaciones: el dueno sube un JPEG" "$(subir "$TA" "profesionales/$UA/certificaciones/c2.jpg" chica.jpg image/jpeg)" 200
verificar "certificaciones: el dueno sube un PNG" "$(subir "$TA" "profesionales/$UA/certificaciones/c3.png" chica.jpg image/png)" 200
verificar "certificaciones: no se reemplaza un archivo existente" "$(subir "$TA" "$C" cert.pdf application/pdf)" 403
verificar "certificaciones: otro usuario no sube" "$(subir "$TB" "profesionales/$UA/certificaciones/c4.pdf" cert.pdf application/pdf)" 403
verificar "certificaciones: sin sesion no se sube" "$(subir "" "profesionales/$UA/certificaciones/c5.pdf" cert.pdf application/pdf)" 403
verificar "certificaciones: un tipo no permitido se rechaza" "$(subir "$TA" "profesionales/$UA/certificaciones/c6.zip" cert.pdf application/zip)" 403
verificar "certificaciones: 10 MB o mas se rechaza" "$(subir "$TA" "profesionales/$UA/certificaciones/c7.pdf" cert-grande.pdf application/pdf)" 403
verificar "certificaciones: el dueno no las lee" "$(leer "$TA" "$C")" 403
verificar "certificaciones: nadie las lee" "$(leer "" "$C")" 403
verificar "certificaciones: el dueno no las borra" "$(borrar "$TA" "$C")" 403
verificar "credenciales: la app sigue sin poder escribir" "$(subir "$TA" "profesionales/$UA/credenciales/c.pdf" cert.pdf application/pdf)" 403

[ "$FALLAS" -eq 0 ] || { echo "$FALLAS prueba(s) fallaron" >&2; exit 1; }
PRUEBAS
chmod +x "$TMP/pruebas.sh"

cd "$TMP"
"$FIREBASE" emulators:exec --only auth,storage --project demo-kinecare --config "$TMP/firebase.json" "$TMP/pruebas.sh"
