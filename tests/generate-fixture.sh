#!/bin/sh
# Experimental emulator-only PKI. Run in a newly created PRIVATE directory; never reuse in production.
set -eu
umask 077
[ "$#" -eq 1 ] || { echo "Usage: sh tests/generate-fixture.sh PRIVATE_EMPTY_DIRECTORY" >&2; exit 2; }
d=$1
[ -d "$d" ] && [ -z "$(ls -A "$d")" ] || { echo "Directory must exist and be empty" >&2; exit 2; }
chmod 700 "$d"
cd "$d"
printf '%s\n' 'client' 'dev tun' 'proto udp' 'remote 10.0.2.2 1194' 'remote-cert-tls server' 'verify-x509-name vegas-fixture-server name' > profile.conf
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 2 -subj '/CN=Vegas ephemeral test CA' -keyout ca.key -out ca.pem -addext basicConstraints=critical,CA:TRUE -addext keyUsage=critical,keyCertSign,cRLSign >/dev/null 2>&1
openssl req -new -newkey rsa:2048 -nodes -subj '/CN=vegas-fixture-server' -keyout server.key -out server.csr >/dev/null 2>&1
printf '%s\n' 'basicConstraints=critical,CA:FALSE' 'extendedKeyUsage=serverAuth' 'keyUsage=critical,digitalSignature,keyEncipherment' > server.ext
openssl x509 -req -in server.csr -CA ca.pem -CAkey ca.key -CAcreateserial -out server.pem -days 2 -sha256 -extfile server.ext >/dev/null 2>&1
openssl req -new -newkey rsa:2048 -nodes -subj '/CN=vegas-fixture-client' -keyout client-rsa.key -out client.csr >/dev/null 2>&1
printf '%s\n' 'basicConstraints=critical,CA:FALSE' 'extendedKeyUsage=clientAuth' 'keyUsage=critical,digitalSignature,keyEncipherment' > client.ext
openssl x509 -req -in client.csr -CA ca.pem -CAkey ca.key -CAcreateserial -out client.pem -days 2 -sha256 -extfile client.ext >/dev/null 2>&1
openssl pkcs8 -topk8 -nocrypt -in client-rsa.key -out client.key
printf '%s\n' 'port 1194' 'proto udp' 'dev tun' 'topology subnet' 'server 10.8.77.0 255.255.255.0' 'ca ca.pem' 'cert server.pem' 'key server.key' 'dh none' 'persist-key' 'persist-tun' 'verb 3' > server.conf
printf '%s\n' "Fixture generated in $d. TEST ONLY; valid 2 days. Do not share or commit the keys."
