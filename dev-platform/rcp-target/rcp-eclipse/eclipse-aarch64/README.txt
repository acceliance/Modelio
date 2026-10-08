Bundles from Eclipse 4.24 (R-4.24-202206070700, https://archive.eclipse.org/eclipse/updates/4.24/)
that the Eclipse 4.18 target platform lacks for Apple Silicon: the 4.18 versions of these two
fragments have a platform filter limited to x86_64 (aarch64 support was added in later releases).

  org.eclipse.core.filesystem.macosx_1.3.300.v20210427-1937.jar
  com.sun.jna_5.8.0.v20210503-0343.jar, com.sun.jna.platform_5.8.0.v20210406-1004.jar   (JNA 4.5.1 has no darwin-aarch64 native)
  org.eclipse.equinox.launcher.cocoa.macosx.aarch64_1.2.500.v20220509-0833.jar   (aarch64 native launcher library)
  org.eclipse.equinox.security.macosx_1.101.400.v20210427-1958.jar

The native "eclipse" executable root (org.eclipse.equinox.executable feature 3.8.1700) is not vendored: generate-target.sh fetches it
from the same repository when ARM64=1.

PATCHED FEATURE (in ../eclipse/features/):
  org.eclipse.equinox.p2.core.feature_1.6.800.v20201106-1246 (folder and .jar)
  Its org.eclipse.equinox.security.macosx entry is split by architecture: x86_64 keeps 1.101.200, aarch64
  uses 1.101.400 (from this directory). Without this the 4.18 feature requires 1.101.200, whose platform
  filter excludes aarch64, so the whole p2 feature chain (p2.core/extras/rcp/user.ui, reached through the
  optional include in org.modelio.platform.feature) became unsatisfiable on aarch64 and was silently dropped,
  taking org.eclipse.equinox.p2.reconciler.dropins with it. The Eclipse signature files and manifest digests were
  removed because the content changed.
