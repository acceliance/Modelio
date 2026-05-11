package org.modelio.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Manages the .modelio user-level configuration folder.
 *
 * Replicates the behavior of org.modelio.platform.core.ModelioEnv:
 * <pre>
 *   ~/.modelio/{majorVersion}.{minorVersion}/
 *   ├── modules/          ← module catalog (downloaded/installed JMDAC files)
 *   ├── macros/           ← user macro scripts
 *   ├── ramcs/            ← reusable archive model component cache
 *   └── preferences.properties  ← user preferences (replaces Eclipse InstanceScope)
 * </pre>
 *
 * All paths are configurable via application.yml properties under {@code modelio.env.*}.
 */
@Service
public class ModelioEnv {

    private static final Logger log = LoggerFactory.getLogger(ModelioEnv.class);

    private static final String MODULES_DIR = "modules";
    private static final String MACROS_DIR = "macros";
    private static final String RAMCS_DIR = "ramcs";
    private static final String PREFERENCES_FILE = "preferences.properties";

    // ------------------------------------------------------------------
    // Configuration from application.yml
    // ------------------------------------------------------------------

    @Value("${modelio.version.major:5}")
    private int majorVersion;

    @Value("${modelio.version.minor:4}")
    private int minorVersion;

    @Value("${modelio.env.path:}")
    private String envPathOverride;

    @Value("${modelio.modules.catalog:}")
    private String moduleCatalogOverride;

    // ------------------------------------------------------------------
    // Resolved paths
    // ------------------------------------------------------------------

    /** Root .modelio path: ~/.modelio/{major}.{minor}/ */
    private Path runtimeDataPath;

    /** Module catalog: ~/.modelio/{major}.{minor}/modules/ */
    private Path moduleCatalogPath;

    /** Macro catalog: ~/.modelio/{major}.{minor}/macros/ */
    private Path macroCatalogPath;

    /** RAMC cache: ~/.modelio/{major}.{minor}/ramcs/ */
    private Path ramcCachePath;

    /** User preferences file */
    private Path preferencesPath;

    /** In-memory preferences (loaded from preferences.properties) */
    private final Properties preferences = new Properties();

    // ------------------------------------------------------------------
    // Initialization
    // ------------------------------------------------------------------

    @PostConstruct
    public void init() throws IOException {
        String versionDir = majorVersion + "." + minorVersion;

        if (envPathOverride != null && !envPathOverride.isBlank()) {
            runtimeDataPath = Path.of(envPathOverride).toAbsolutePath();
        } else {
            runtimeDataPath = Path.of(System.getProperty("user.home"), ".modelio-web", versionDir).toAbsolutePath();
        }

        if (moduleCatalogOverride != null && !moduleCatalogOverride.isBlank()) {
            moduleCatalogPath = Path.of(moduleCatalogOverride).toAbsolutePath();
        } else {
            moduleCatalogPath = runtimeDataPath.resolve(MODULES_DIR);
        }
        macroCatalogPath = runtimeDataPath.resolve(MACROS_DIR);
        ramcCachePath = runtimeDataPath.resolve(RAMCS_DIR);
        preferencesPath = runtimeDataPath.resolve(PREFERENCES_FILE);

        // Create all directories
        Files.createDirectories(runtimeDataPath);
        Files.createDirectories(moduleCatalogPath);
        Files.createDirectories(macroCatalogPath);
        Files.createDirectories(ramcCachePath);

        // Load preferences
        if (Files.exists(preferencesPath)) {
            try (InputStream is = Files.newInputStream(preferencesPath)) {
                preferences.load(is);
            }
            log.info(".modelio preferences loaded from {}", preferencesPath);
        }

        log.info(".modelio environment initialized at {}", runtimeDataPath);
        log.info("  modules:  {}", moduleCatalogPath);
        log.info("  macros:   {}", macroCatalogPath);
        log.info("  ramcs:    {}", ramcCachePath);
    }

    // ------------------------------------------------------------------
    // Path getters
    // ------------------------------------------------------------------

    /** Root .modelio path: ~/.modelio/{major}.{minor}/ */
    public Path getRuntimeDataPath() {
        return runtimeDataPath;
    }

    /** Module catalog: ~/.modelio/{major}.{minor}/modules/ */
    public Path getModuleCatalogPath() {
        return moduleCatalogPath;
    }

    /** Macro catalog: ~/.modelio/{major}.{minor}/macros/ */
    public Path getMacroCatalogPath() {
        return macroCatalogPath;
    }

    /** RAMC cache: ~/.modelio/{major}.{minor}/ramcs/ */
    public Path getRamcCachePath() {
        return ramcCachePath;
    }

    /** Modelio version string */
    public String getVersionString() {
        return majorVersion + "." + minorVersion;
    }

    // ------------------------------------------------------------------
    // Preferences (replaces Eclipse InstanceScope prefs)
    // ------------------------------------------------------------------

    /**
     * Get a preference value.
     */
    public String getPreference(String key) {
        return preferences.getProperty(key);
    }

    /**
     * Get a preference value with default.
     */
    public String getPreference(String key, String defaultValue) {
        return preferences.getProperty(key, defaultValue);
    }

    /**
     * Set a preference value and persist to disk.
     */
    public void setPreference(String key, String value) {
        preferences.setProperty(key, value);
        savePreferences();
    }

    /**
     * Remove a preference and persist.
     */
    public void removePreference(String key) {
        preferences.remove(key);
        savePreferences();
    }

    /**
     * Get all preferences as a read-only copy.
     */
    public Properties getAllPreferences() {
        Properties copy = new Properties();
        copy.putAll(preferences);
        return copy;
    }

    private void savePreferences() {
        try (OutputStream os = Files.newOutputStream(preferencesPath)) {
            preferences.store(os, "Modelio Web user preferences");
        } catch (IOException e) {
            log.error("Failed to save preferences to {}: {}", preferencesPath, e.getMessage());
        }
    }
}
