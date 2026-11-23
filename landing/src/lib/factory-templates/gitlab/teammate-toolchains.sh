set -euo pipefail
toolchain_root="${DMTOOLS_TOOLCHAINS_DIR:-$HOME/.dmtools-toolchains}"
mkdir -p "$toolchain_root/bin"
case "$(uname -m)" in
  x86_64|amd64) toolchain_arch=amd64; node_arch=x64 ;;
  aarch64|arm64) toolchain_arch=arm64; node_arch=arm64 ;;
  *) echo "Unsupported runner architecture for agent toolchains: $(uname -m)" >&2; exit 1 ;;
esac
toolchain_fetch() { curl --silent --show-error --fail --location --retry 3 --connect-timeout 15 "$@"; }
if [ -n "$DMTOOLS_NODE_VERSION" ]; then
  node_release="$(toolchain_fetch https://nodejs.org/dist/index.json |
    jq -r --arg major "v$DMTOOLS_NODE_VERSION." 'first(.[] | select(.version | startswith($major)) | .version) // empty')"
  [ -n "$node_release" ] || { echo "Node.js $DMTOOLS_NODE_VERSION is not available" >&2; exit 1; }
  mkdir -p "$toolchain_root/node"
  toolchain_fetch "https://nodejs.org/dist/$node_release/node-$node_release-linux-$node_arch.tar.gz" |
    tar -xz --no-same-owner -C "$toolchain_root/node" --strip-components=1
  export PATH="$toolchain_root/node/bin:$PATH"
  node --version
fi
if [ -n "$DMTOOLS_PYTHON_VERSION" ]; then
  toolchain_fetch https://astral.sh/uv/install.sh | env UV_INSTALL_DIR="$toolchain_root/uv" UV_NO_MODIFY_PATH=1 sh
  export UV_PYTHON_INSTALL_DIR="$toolchain_root/python" UV_PYTHON_BIN_DIR="$toolchain_root/bin" UV_PYTHON_PREFERENCE=only-managed
  "$toolchain_root/uv/uv" python install "$DMTOOLS_PYTHON_VERSION"
  python_bin="$("$toolchain_root/uv/uv" python find "$DMTOOLS_PYTHON_VERSION")"
  export PATH="$(dirname "$python_bin"):$toolchain_root/uv:$PATH"
  python --version
  pip --version
fi
if [ -n "$DMTOOLS_GO_VERSION" ]; then
  go_archive="$(toolchain_fetch 'https://go.dev/dl/?mode=json&include=all' |
    jq -r --arg version "go$DMTOOLS_GO_VERSION" --arg arch "$toolchain_arch" \
      'first(.[] | select(.stable and (.version == $version or (.version | startswith($version + ".")))) |
        .files[] | select(.os == "linux" and .arch == $arch and .kind == "archive") | .filename) // empty')"
  [ -n "$go_archive" ] || { echo "Go $DMTOOLS_GO_VERSION is not available for linux/$toolchain_arch" >&2; exit 1; }
  rm -rf "$toolchain_root/go"
  toolchain_fetch "https://go.dev/dl/$go_archive" | tar -xz --no-same-owner -C "$toolchain_root"
  export GOPATH="${GOPATH:-$HOME/go}"
  export PATH="$toolchain_root/go/bin:$GOPATH/bin:$PATH"
  go version
fi
if [ -n "$DMTOOLS_FLUTTER_VERSION" ]; then
  apt-get -o Acquire::Retries=3 install -y --no-install-recommends xz-utils
  rm -rf "$toolchain_root/flutter"
  if [ "$toolchain_arch" = amd64 ]; then
    flutter_releases="$(toolchain_fetch https://storage.googleapis.com/flutter_infra_release/releases/releases_linux.json)"
    flutter_archive="$(jq -r --arg channel "$DMTOOLS_FLUTTER_VERSION" \
      '.current_release[$channel] as $hash | first(.releases[] | select(.hash == $hash and .channel == $channel) | .archive) // empty' \
      <<< "$flutter_releases")"
    [ -n "$flutter_archive" ] || { echo "Flutter channel $DMTOOLS_FLUTTER_VERSION is not available" >&2; exit 1; }
    toolchain_fetch "$(jq -r .base_url <<< "$flutter_releases")/$flutter_archive" |
      tar -xJ --no-same-owner -C "$toolchain_root"
  else
    git clone --quiet --branch "$DMTOOLS_FLUTTER_VERSION" https://github.com/flutter/flutter.git "$toolchain_root/flutter"
  fi
  git config --global --add safe.directory "$toolchain_root/flutter"
  export PATH="$toolchain_root/flutter/bin:$toolchain_root/flutter/bin/cache/dart-sdk/bin:$HOME/.pub-cache/bin:$PATH"
  flutter --disable-analytics >/dev/null
  flutter --version
fi
