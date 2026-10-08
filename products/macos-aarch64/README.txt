Apple Silicon (macosx/cocoa/aarch64) launcher assembly
=======================================================
Tycho 4.0.13 cannot build the native launcher for macosx/aarch64 from the Eclipse 4.18 target (4.18 has no
aarch64 executable root; the 4.24 executable feature was never selected by the resolver), so the product
comes out without Contents/MacOS/modelio, Contents/Info.plist and Contents/Resources/modelio.icns.

build-mac.sh --arm64 adds them after the Tycho build:
  - Contents/MacOS/modelio   <- Eclipse.app/Contents/MacOS/launcher from the vendored root artifact
        dev-platform/rcp-target/rcp-eclipse/eclipse-aarch64/binary/
        org.eclipse.equinox.executable_root.cocoa.macosx.aarch64_3.8.1700.v20220509-0833
        (Eclipse 4.24, https://archive.eclipse.org/eclipse/updates/4.24/R-4.24-202206070700/, an arm64 Mach-O)
  - Contents/Info.plist      <- Info.plist.template in this folder (@VERSION@ replaced)
  - Contents/Resources/modelio.icns <- products/icons/modelio.icns
