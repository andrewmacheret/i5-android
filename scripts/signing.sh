#!/usr/bin/env bash
# Manages the release signing key. The key and its password are backed up in the
# macOS Keychain, and copied into GitHub Actions secrets for the release workflow.
#
#   scripts/signing.sh create         make a new key (once, ever) and back it up to the Keychain
#   scripts/signing.sh restore        write the key file back out from the Keychain
#   scripts/signing.sh github         copy the key and password into the repo's GitHub secrets
#   scripts/signing.sh run <cmd...>   run a command with the key, or the debug key if there's none
#
# The password never goes on a command line, only through env vars and stdin,
# so it doesn't show up in `ps` or shell history.
set -euo pipefail

KEYSTORE="${I5_KEYSTORE:-$HOME/.android/i5-android-release.jks}"
REPO=andrewmacheret/i5-android
PASSWORD_ITEM=i5-android-release-password
KEYSTORE_ITEM=i5-android-release-keystore
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

has_key() {
  security find-generic-password -s "$PASSWORD_ITEM" > /dev/null 2>&1
}

# `security -i` reads its command from stdin, which keeps the secret out of argv.
# It can only read lines up to about 4 KB, so check first, and never show its output
# since an error would echo the secret.
save() {
  if [ "${#2}" -gt 3800 ]; then
    echo "Value for $1 is too long for the Keychain helper." >&2
    return 1
  fi
  printf 'add-generic-password -U -a i5-android -s %s -w %s\n' "$1" "$2" | security -i > /dev/null 2>&1
  [ "$(load "$1")" = "$2" ] || { echo "Couldn't save $1 to the Keychain." >&2; return 1; }
}

load() {
  security find-generic-password -s "$1" -w
}

restore() {
  mkdir -p "$(dirname "$KEYSTORE")"
  load "$KEYSTORE_ITEM" | base64 -d > "$KEYSTORE"
  chmod 600 "$KEYSTORE"
}

case "${1:-}" in
  create)
    if has_key; then
      echo "A release key is already in the Keychain. Making a new one would break updates, so stopping." >&2
      exit 1
    fi
    mkdir -p "$(dirname "$KEYSTORE")"
    I5_KEYSTORE_PASSWORD="$(openssl rand -hex 24)"
    export I5_KEYSTORE_PASSWORD
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -alias i5 \
      -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Andrew Macheret, O=I-5 Express" \
      -storepass:env I5_KEYSTORE_PASSWORD -keypass:env I5_KEYSTORE_PASSWORD
    chmod 600 "$KEYSTORE"
    # If the backup fails, remove everything so a rerun starts clean
    if ! { save "$KEYSTORE_ITEM" "$(base64 -i "$KEYSTORE")" && save "$PASSWORD_ITEM" "$I5_KEYSTORE_PASSWORD"; }; then
      security delete-generic-password -s "$KEYSTORE_ITEM" > /dev/null 2>&1 || true
      security delete-generic-password -s "$PASSWORD_ITEM" > /dev/null 2>&1 || true
      rm -f "$KEYSTORE"
      exit 1
    fi
    echo "Made $KEYSTORE and backed it up to the Keychain."
    ;;
  restore)
    restore
    echo "Wrote $KEYSTORE from the Keychain."
    ;;
  github)
    load "$KEYSTORE_ITEM" | gh secret set I5_KEYSTORE_BASE64 --repo "$REPO"
    load "$PASSWORD_ITEM" | gh secret set I5_KEYSTORE_PASSWORD --repo "$REPO"
    ;;
  run)
    shift
    if has_key; then
      [ -f "$KEYSTORE" ] || restore
      I5_KEYSTORE="$KEYSTORE" I5_KEYSTORE_PASSWORD="$(load "$PASSWORD_ITEM")" "$@"
    else
      echo "No release key in the Keychain, so signing with the debug key." >&2
      "$@"
    fi
    ;;
  *)
    sed -n '2,10p' "$0"
    exit 1
    ;;
esac
