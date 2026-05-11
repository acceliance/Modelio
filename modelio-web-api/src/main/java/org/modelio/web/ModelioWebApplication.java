package org.modelio.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Modelio Web API - Main application entry point.
 *
 * Phase 1 of the SWT-to-React migration: exposes the Modelio core
 * (metamodel, sessions, transactions, modules) as a REST/WebSocket API.
 */
@SpringBootApplication
public class ModelioWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelioWebApplication.class, args);
    }
}
