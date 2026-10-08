#!/bin/bash
# =============================================================================
# setup-wsl.sh
#
# One-time setup of a WSL Ubuntu environment for building legacy Modelio
# (Tycho / Eclipse RCP), e.g. to cross-build the macOS product on Linux.
#
# Installs: base tools, SDKMAN, JDK 11 (Temurin), Maven 3.9.x
# Clones:   the Modelio repo into ~/modelio (Linux filesystem, NOT /mnt/c)
#
# Usage (inside Ubuntu):
#   bash setup-wsl.sh
#
# Overrides:
#   REPO_URL=git@github.com:acceliance/Modelio.git bash setup-wsl.sh
#   REPO_DIR=$HOME/src/modelio bash setup-wsl.sh
#   JAVA_VERSION=11.0.25-tem MAVEN_VERSION=3.9.9 bash setup-wsl.sh
# =============================================================================

set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/acceliance/Modelio.git}"
REPO_DIR="${REPO_DIR:-$HOME/modelio}"
JAVA_VERSION="${JAVA_VERSION:-11.0.25-tem}"
MAVEN_VERSION="${MAVEN_VERSION:-3.9.9}"

if [[ "$PWD" == /mnt/* ]]; then
    echo "NOTE: running from $PWD (Windows drive). That is fine for this script,"
    echo "      but the repo will be cloned to $REPO_DIR on the Linux filesystem."
fi

echo "== 1/5 Base packages =="
sudo apt-get update
sudo apt-get install -y curl zip unzip git ca-certificates build-essential xz-utils dos2unix

echo "== 2/5 SDKMAN =="
export SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"
if [ ! -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
    curl -s "https://get.sdkman.io" | bash
fi
# sdkman-init.sh is not nounset-safe
set +u
# shellcheck disable=SC1091
source "$SDKMAN_DIR/bin/sdkman-init.sh"

echo "== 3/5 JDK $JAVA_VERSION =="
sdk install java "$JAVA_VERSION" < /dev/null || true
sdk default java "$JAVA_VERSION"

echo "== 4/5 Maven $MAVEN_VERSION =="
sdk install maven "$MAVEN_VERSION" < /dev/null || true
sdk default maven "$MAVEN_VERSION"
set -u

java -version
mvn -version

echo "== 5/5 Repository =="
if [ -d "$REPO_DIR/.git" ]; then
    echo "Repo already present at $REPO_DIR; fetching latest."
    git -C "$REPO_DIR" fetch --all --prune
else
    git clone "$REPO_URL" "$REPO_DIR"
fi
# Keep Unix line endings and exec bits for shell scripts
git -C "$REPO_DIR" config core.autocrlf input
git -C "$REPO_DIR" config core.filemode true

# Maven settings: avoid noisy download progress in logs
mkdir -p "$HOME/.m2"
if [ ! -f "$HOME/.m2/maven.config" ]; then
    echo "--no-transfer-progress" > "$HOME/.m2/maven.config"
fi

MEM_GB=$(free -g | awk '/^Mem:/{print $2}')
if [ "${MEM_GB:-0}" -lt 10 ]; then
    echo
    echo "WARNING: WSL only sees ${MEM_GB} GB RAM. Create C:\\Users\\<you>\\.wslconfig with:"
    echo "    [wsl2]"
    echo "    memory=12GB"
    echo "  then run 'wsl --shutdown' from PowerShell and reopen Ubuntu."
fi

cat <<EOF

Setup complete.

Open a NEW shell (or run: source ~/.sdkman/bin/sdkman-init.sh), then:

    cd $REPO_DIR
    export ECLIPSE_WS="\$PWD"
    ./generate-target.sh
    export MAVEN_OPTS="-Xmx4g"
    cd AGGREGATOR && mvn clean install -Dmaven.test.skip=true

EOF
