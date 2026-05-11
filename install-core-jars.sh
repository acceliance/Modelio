#!/bin/bash
# =============================================================================
# install-core-jars.sh
#
# Run this AFTER building Modelio in Eclipse IDE.
# It installs the compiled core JARs into the local Maven repository
# so that modelio-web-api (Spring Boot) can use them as dependencies.
#
# Prerequisites:
#   1. Build the Modelio project in Eclipse IDE (Export > Deployable plugins)
#      OR use Eclipse headless build
#   2. Maven 3.x installed (C:\tools\apache-maven-3.9.9 or in PATH)
#   3. Java 17 for modelio-web-api (Java 11 for Modelio itself)
#
# Usage:
#   ./install-core-jars.sh
# =============================================================================

set -e

MVN="${MVN:-mvn}"
VERSION="5.4.1"
REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"

echo "============================================"
echo " Modelio Core JAR Installer"
echo " Installing to local Maven repository"
echo "============================================"
echo ""

# Function to install a JAR
install_jar() {
    local module_dir="$1"
    local artifact_id="$2"
    local jar_path

    # Try target/ directory first (Tycho build output)
    jar_path=$(find "$REPO_ROOT/modelio/$module_dir/target" -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" 2>/dev/null | head -1)

    if [ -z "$jar_path" ]; then
        echo "  SKIP: $artifact_id (no JAR found in $module_dir/target/)"
        return 1
    fi

    echo "  Installing: $artifact_id ($jar_path)"
    $MVN install:install-file \
        -Dfile="$jar_path" \
        -DgroupId=org.modelio \
        -DartifactId="$artifact_id" \
        -Dversion="$VERSION" \
        -Dpackaging=jar \
        -DgeneratePom=true \
        -q
}

echo "Phase 1: Core modules (no SWT dependency)"
echo "-------------------------------------------"
install_jar "core/core.utils"            "core.utils"
install_jar "core/core.kernel"           "core.kernel"
install_jar "core/core.metamodel.api"    "core.metamodel.api"
install_jar "core/core.metamodel.impl"   "core.metamodel.impl"
install_jar "core/core.session"          "core.session"
install_jar "core/core.store.exml"       "core.store.exml"
install_jar "core/core.project"          "core.project"
install_jar "core/core.modelshield"      "core.modelshield"

echo ""
echo "Phase 2: UML/BPMN metamodel modules"
echo "------------------------------------"
install_jar "uml/uml.metamodel.api"              "uml.metamodel.api"
install_jar "uml/uml.metamodel.contribution"     "uml.metamodel.contribution"
install_jar "uml/uml.metamodel.implementation"   "uml.metamodel.implementation"
install_jar "bpmn/bpmn.metamodel.api"             "bpmn.metamodel.api"
install_jar "bpmn/bpmn.metamodel.contribution"    "bpmn.metamodel.contribution"
install_jar "bpmn/bpmn.metamodel.implementation"  "bpmn.metamodel.implementation"

echo ""
echo "Phase 3: Platform modules (module infrastructure)"
echo "--------------------------------------------------"
install_jar "platform/platform.core"          "platform.core"
install_jar "platform/platform.api"           "platform.api"
install_jar "platform/platform.mda.infra"     "platform.mda.infra"
install_jar "platform/platform.update.repo"   "platform.update.repo"

echo ""
echo "Phase 4: Diagram model (Gm* layer — no SWT)"
echo "----------------------------------------------"
install_jar "app/app.diagram.api"         "app.diagram.api"
install_jar "app/app.diagram.elements"    "app.diagram.elements"
install_jar "app/app.diagram.persistence" "app.diagram.persistence"
install_jar "app/app.diagram.styles"      "app.diagram.styles"

echo ""
echo "============================================"
echo " Done! Core JARs installed to ~/.m2/repository/org/modelio/"
echo ""
echo " Next step: Build modelio-web-api"
echo "   cd modelio-web-api && mvn clean install"
echo "============================================"
