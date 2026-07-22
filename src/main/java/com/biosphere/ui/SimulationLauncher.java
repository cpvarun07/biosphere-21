package com.biosphere.ui;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;
import com.biosphere.entities.Organism;
import java.util.List;
import java.util.Random;

public class SimulationLauncher {
    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("      Initializing BioSphere-21 Engine        ");
        System.out.println("==============================================");
        
        // Initialize a clean 40x40 spatial grid matrix
        GridManager grid = new GridManager(40, 40);
        
        // Seeding the engine matrix with life forms
        for (int i = 0; i < 40; i++) {
            final char type = (i % 3 == 0) ? 'C' : 'H';
            
            Organism testLife = new Organism(new Point(i, i), 100, 100, grid, offspring -> {}) {
                @Override
                public void act() throws InterruptedException {
                    Random rand = new Random();
                    
                    // Keep executing as long as this organism is alive (Unit 1 Encapsulation)
                    while (isAlive()) {
                        // Rest for a random interval between 200ms and 600ms so they don't move instantly
                        Thread.sleep(200 + rand.nextInt(400));
                        
                        // Grab a defensive list of the 8 available neighbor coordinates
                        List<Point> neighbors = grid.neighborsOf(getPosition());
                        
                        if (!neighbors.isEmpty()) {
                            // Pick a random target cell from the geometric neighbors
                            Point target = neighbors.get(rand.nextInt(neighbors.size()));
                            
                            // Atomically attempt to claim the new cell coordinate in RAM
                            grid.tryMove(getPosition(), target, this);
                        }
                    }
                }
                
                @Override
                public char glyph() {
                    return type;
                }
            };
            
            grid.tryOccupy(new Point(i, i), testLife);
            
            // Crucial Step: Spin up a dedicated Java 21 Virtual Thread for this specific organism!
            Thread.ofVirtual().start(() -> {
                try {
                    testLife.act();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        try {
            // Instantiate and start the visual web server dashboard
            WebSimulationServer webServer = new WebSimulationServer(8080, grid);
            Thread.ofVirtual().start(webServer::start);
            
            System.out.println("Ecosystem core online. Keep this terminal running.");
            Thread.sleep(Long.MAX_VALUE);
            
        } catch (Exception e) {
            System.err.println("Critical server initialization crash: " + e.getMessage());
        }
    }
}