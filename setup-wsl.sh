#!/bin/bash
# =============================================================================
# setup-wsl.sh
#
# One-time setup of a WSL Ubuntu environment for building legacy Modelio
# (Tycho / Eclipse RCP), e.g. to cross-build the macOS product on Linux.
#
# Installs: base tools, SDKMAN, JDK 17 (runs Maven/Tycho) + JDK 11 (toolchain), Maven 3.9.x
# Clones:   the Modelio repo into ~/modelio (Linux filesystem, NOT /mnt/c)
#
# Usage (inside Ubuntu):
#   bash setup-wsl.sh
#
# Overrides:
#   REPO_URL=git@github.com:acceliance/Modelio.git bash setup-wsl.sh
#   REPO_DIR=$HOME/src/modelio bash setup-wsl.sh
#   JAVA_VERSION=11.0.25-tem MAVEN_VERSION=3.9.9 bash setup-wsl.sh
#   SKIP_APT=1 bash setup-wsl.sh      # packages already installed (no sudo needed)
# =============================================================================

set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/acceliance/Modelio.git}"
REPO_DIR="${REPO_DIR:-$HOME/modelio}"
JAVA_VERSION="${JAVA_VERSION:-17.0.13-tem}"   # runs Maven/Tycho 4 (needs 17+)
JAVA11_VERSION="${JAVA11_VERSION:-11.0.25-tem}" # compile toolchain (JavaSE-1.8/JavaSE-11 bundles)
MAVEN_VERSION="${MAVEN_VERSION:-3.9.9}"

if [[ "$PWD" == /mnt/* ]]; then
    echo "NOTE: running from $PWD (Windows drive). That is fine for this script,"
    echo "      but the repo will be cloned to $REPO_DIR on the Linux filesystem."
fi

echo "== 1/5 Base packages =="
if [ "${SKIP_APT:-0}" = "1" ]; then
    echo "SKIP_APT=1: assuming curl zip unzip git build-essential xz-utils are installed."
else
    sudo apt-get update
    sudo apt-get install -y curl zip unzip git ca-certificates build-essential xz-utils dos2unix
fi

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

sdk install java "$JAVA11_VERSION" < /dev/null || true

echo "== 4/5 Maven $MAVEN_VERSION =="
sdk install maven "$MAVEN_VERSION" < /dev/null || true
sdk default maven "$MAVEN_VERSION"
set -u

# Toolchains: bundles declare JavaSE-1.8 / JavaSE-11 (useJDK=BREE)
J11="$SDKMAN_DIR/candidates/java/$JAVA11_VERSION"
J17="$SDKMAN_DIR/candidates/java/$JAVA_VERSION"
mkdir -p "$HOME/.m2"
cat > "$HOME/.m2/toolchains.xml" <<TC
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain><type>jdk</type><provides><id>JavaSE-1.8</id><version>11</version><vendor>Temurin</vendor></provides><configuration><jdkHome>$J11</jdkHome></configuration></toolchain>
  <toolchain><type>jdk</type><provides><id>JavaSE-11</id><version>11</version><vendor>Temurin</vendor></provides><configuration><jdkHome>$J11</jdkHome></configuration></toolchain>
  <toolchain><type>jdk</type><provides><id>JavaSE-17</id><version>17</version><vendor>Temurin</vendor></provides><configuration><jdkHome>$J17</jdkHome></configuration></toolchain>
</toolchains>
TC

"$J17/bin/java" -version
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
