package org.modelio.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.modelio.web.service.ModelioEnv;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes the Modelio runtime environment configuration.
 * Mirrors the .modelio folder structure managed by ModelioEnv.
 */
@RestController
@RequestMapping("/api/env")
@Tag(name = "Environment", description = "Modelio runtime environment (.modelio folder, preferences)")
public class EnvironmentController {

    private final ModelioEnv env;

    public EnvironmentController(ModelioEnv env) {
        this.env = env;
    }

    @GetMapping
    @Operation(summary = "Get environment info",
            description = "Returns Modelio version, .modelio folder paths, and runtime configuration.")
    public ResponseEntity<Map<String, Object>> getEnvironment() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", env.getVersionString());
        info.put("runtimeDataPath", env.getRuntimeDataPath().toString());
        info.put("moduleCatalogPath", env.getModuleCatalogPath().toString());
        info.put("macroCatalogPath", env.getMacroCatalogPath().toString());
        info.put("ramcCachePath", env.getRamcCachePath().toString());
        return ResponseEntity.ok(info);
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get all preferences",
            description = "Returns all user preferences from .modelio preferences.properties.")
    public ResponseEntity<Map<String, String>> getPreferences() {
        Map<String, String> result = new LinkedHashMap<>();
        env.getAllPreferences().forEach((k, v) -> result.put(k.toString(), v.toString()));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/preferences/{key}")
    @Operation(summary = "Get a preference value")
    public ResponseEntity<Map<String, String>> getPreference(@PathVariable String key) {
        String value = env.getPreference(key);
        if (value == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    @PutMapping("/preferences/{key}")
    @Operation(summary = "Set a preference value")
    public ResponseEntity<Void> setPreference(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null) return ResponseEntity.badRequest().build();
        env.setPreference(key, value);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/preferences/{key}")
    @Operation(summary = "Remove a preference")
    public ResponseEntity<Void> removePreference(@PathVariable String key) {
        env.removePreference(key);
        return ResponseEntity.noContent().build();
    }
}
