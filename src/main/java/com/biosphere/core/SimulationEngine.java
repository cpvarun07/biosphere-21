package com.biosphere.core;

import com.biosphere.entities.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class SimulationEngine implements OffspringListener {
    private final GridManager grid;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Organism> organisms = new CopyOnWriteArrayList<>();
    private final AtomicLong births = new AtomicLong();
    private volatile boolean running;

    public SimulationEngine(int width, int height) { grid = new GridManager(width, height); }
    public GridManager getGrid() { return grid; }
    public List<Organism> getLivingOrganisms() { return organisms.stream().filter(Organism::isAlive).toList(); }

    public void seed(int plants, int herbivores, int carnivores) {
        seedSpecies(plants, () -> new Plant(randomEmptyPoint(), grid, this));
        seedSpecies(herbivores, () -> new Herbivore(randomEmptyPoint(), grid, this));
        seedSpecies(carnivores, () -> new Carnivore(randomEmptyPoint(), grid, this));
    }

    private void seedSpecies(int count, Supplier<Organism> factory) {
        for (int i = 0; i < count; i++) {
            Organism organism = factory.get();
            if (grid.tryOccupy(organism.getPosition(), organism)) registerAndLaunch(organism);
        }
    }

    private Point randomEmptyPoint() {
        int attempts = 0;
        while (attempts++ < grid.getWidth() * grid.getHeight() * 4) {
            Point p = new Point(ThreadLocalRandom.current().nextInt(grid.getWidth()),
                                ThreadLocalRandom.current().nextInt(grid.getHeight()));
            if (grid.peek(p) == null) return p;
        }
        throw new IllegalStateException("No empty grid cell available");
    }

    private void registerAndLaunch(Organism organism) {
        organisms.add(organism);
        births.incrementAndGet();
        executor.submit(() -> {
            try { organism.act(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (Exception e) {
                System.err.println("Organism " + organism.getId() + " terminated: " + e.getMessage());
                organism.die();
            }
        });
    }

    @Override public void onOffspringCreated(Organism offspring) { registerAndLaunch(offspring); }

    public PopulationStats getPopulationStats() {
        int p=0, youngPlants=0, trees=0, h=0, c=0, energy=0, metabolism=0;
        int plantEnergy=0, treeEnergy=0, herbivoreEnergy=0, carnivoreEnergy=0;
        for (Organism o : getLivingOrganisms()) {
            energy += o.getEnergy();
            if (o instanceof Animal) metabolism += 2;
            if (o instanceof Plant plant) {
                p++;
                if (plant.isTree()) { trees++; treeEnergy += o.getEnergy(); }
                else { youngPlants++; plantEnergy += o.getEnergy(); }
            } else if (o instanceof Herbivore) { h++; herbivoreEnergy += o.getEnergy(); }
            else if (o instanceof Carnivore) { c++; carnivoreEnergy += o.getEnergy(); }
        }
        int total = p+h+c;
        long created = births.get();
        return new PopulationStats(total, youngPlants, trees, h, c, energy,
            plantEnergy, treeEnergy, herbivoreEnergy, carnivoreEnergy,
            metabolism, created, Math.max(0, created-total));
    }

    public boolean isRunning() { return running; }
    public void start() { running = true; }

    public void shutdown() throws InterruptedException {
        running = false;
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    public record PopulationStats(int total, int plants, int trees, int herbivores,
                                   int carnivores, int totalEnergy, int plantEnergy, int treeEnergy,
                                   int herbivoreEnergy, int carnivoreEnergy, int metabolismPerTick,
                                   long births, long deaths) {
        public int plantStageTotal() { return plants + trees; }
        public double averageEnergy() { return total == 0 ? 0 : (double) totalEnergy / total; }
    }
}
