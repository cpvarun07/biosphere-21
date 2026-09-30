package com.biosphere.ui;

import com.biosphere.core.SimulationEngine;

public final class SimulationLauncher {
    private SimulationLauncher() {}

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("      Initializing BioSphere-21 Engine        ");
        System.out.println("==============================================");

        SimulationEngine engine = new SimulationEngine(40, 40);
        engine.seed(80, 30, 10);
        engine.start();

        int port = Integer.getInteger("biosphere.port", 8080);
        try {
            WebSimulationServer webServer = new WebSimulationServer(port, engine);
            Thread.ofVirtual().start(webServer::start);
            System.out.println("Ecosystem core online.");
            System.out.println("Food chain: C eats H | H eats P | T is not edible.");
            System.out.println("P = young plant -> T = mature tree.");
            System.out.println("Live dashboard: http://localhost:" + port + "/");
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Critical server initialization crash: " + e.getMessage());
        } finally {
            try { engine.shutdown(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
