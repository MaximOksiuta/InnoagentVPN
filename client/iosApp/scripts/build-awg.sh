#!/bin/bash
set -euo pipefail
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:$PATH"
CHECKOUT_ROOT="${BUILD_DIR%Build/*}SourcePackages/checkouts/amneziawg-apple"
GO_BRIDGE="$CHECKOUT_ROOT/Sources/WireGuardKitGo"
test -d "$GO_BRIDGE" || { echo "error: AmneziaWG Swift Package checkout is missing"; exit 1; }
cd "$GO_BRIDGE"
# Go's auto-downloaded toolchains are read-only in the module cache. Only the
# private DerivedData copy is made writable for the upstream runtime patch.
REAL_GOROOT="$(go env GOROOT)"
WORK_GOROOT="$CONFIGURATION_TEMP_DIR/wireguard-go-bridge/goroot"
if [ ! -f "$WORK_GOROOT/.prepared" ]; then
    mkdir -p "$WORK_GOROOT"
    rsync -a --delete --exclude=pkg/obj/go-build "$REAL_GOROOT/" "$WORK_GOROOT/"
    chmod -R u+w "$WORK_GOROOT"
    cat goruntime-*.diff | /usr/bin/patch -p1 -f -N -r- -d "$WORK_GOROOT"
    touch "$WORK_GOROOT/.prepared"
fi
# The upstream Makefile defines iphoneos and macosx but omits the simulator.
/usr/bin/make GOOS_iphonesimulator=ios
