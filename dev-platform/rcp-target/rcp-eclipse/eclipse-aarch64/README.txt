Bundles from Eclipse 4.24 (R-4.24-202206070700, https://archive.eclipse.org/eclipse/updates/4.24/)
that the Eclipse 4.18 target platform lacks for Apple Silicon: the 4.18 versions of these two
fragments have a platform filter limited to x86_64 (aarch64 support was added in later releases).

  org.eclipse.core.filesystem.macosx_1.3.300.v20210427-1937.jar
  org.eclipse.equinox.launcher.cocoa.macosx.aarch64_1.2.500.v20220509-0833.jar   (aarch64 native launcher library)
  org.eclipse.equinox.security.macosx_1.101.400.v20210427-1958.jar

The native "eclipse" executable root (org.eclipse.equinox.executable feature 3.8.1700) is not vendored: generate-target.sh fetches it
from the same repository when ARM64=1.
