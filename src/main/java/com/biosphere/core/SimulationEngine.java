package com.biosphere.core;

import com.biosphere.entities.Carnivore;
import com.biosphere.entities.Herbivore;
import com.biosphere.entities.OffspringListener;
import com.biosphere.entities.Organism;
import com.biosphere.entities.Plant;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Owns the simulation's lifecycle: the GridManager, the virtual-thread
 * executor every organism runs on, and the authoritative list of living
 * organisms (decoupled from the grid itself, per the architecture — the
 * grid answers "what's at this cell", this list answers "what's alive").
 *
 * This is the ONLY component permitted to submit new virtual thread
 * tasks. It implements OffspringListener and passes itself to every
 * organism it constructs (directly during seed(), and transitively to
 * every clone()-based offspring, since Organism.clone() carries the
 * listener reference over automatically). When an organism reproduces,
 * it calls getOffspringListener().onOffspringCreated(offspring) after a
 * successful GridManager.tryOccupy(), which routes straight back here —
 * keeping thread lifecycle management centralized in this one class
 * instead of scattered across every reproducing organism.
 */
public final class SimulationEngine implements OffspringListener {

    private final GridManager grid;
    private final ExecutorService executor;
    private final List<Organism> livingOrganisms;
    private volatile boolean running;

    public SimulationEngine(int width, int height) {
        this.grid = new GridManager(width, height);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.livingOrganisms = new CopyOnWriteArrayList<>();
        this.running = false;
    }

    public GridManager getGrid() {
        return grid;
    }

    /**
     * Returns a defensive snapshot of currently-tracked organisms. Note
     * this list is eventually consistent with grid state by design (see
     * architecture notes in GridManager) — an organism may appear here
     * slightly before or after it's fully reflected on the grid.
     */
    public List<Organism> getLivingOrganisms() {
        return List.copyOf(livingOrganisms);
    }

    /**
     * Seeds the initial population: randomly places the given counts of
     * Plant, Herbivore, and Carnivore across empty cells, then submits
     * each one's act() loop to the virtual thread executor. Each
     * organism is constructed with `this` as its OffspringListener, so
     * anything it later reproduces reports back here too.
     */
    public void seed(int plantCount, int herbivoreCount, int carnivoreCount) {
        seedSpecies(plantCount, () -> new Plant(randomEmptyPoint(), grid, this));
        seedSpecies(herbivoreCount, () -> new Herbivore(randomEmptyPoint(), grid, this));
        seedSpecies(carnivoreCount, () -> new Carnivore(randomEmptyPoint(), grid, this));
    }

    private void seedSpecies(int count, java.util.function.Supplier<Organism> factory) {
        for (int i = 0; i < count; i++) {
            Organism organism = factory.get();
            if (grid.tryOccupy(organism.getPosition(), organism)) {
                registerAndLaunch(organism);
            }
            // If tryOccupy failed (extremely unlikely race at seed time,
            // since seeding is single-threaded), the organism is simply
            // discarded — it was never published anywhere else.
        }
    }

    /**
     * Finds a random empty cell on the grid. Used only during seeding.
     * Since GridManager.tryOccupy is the actual atomic gate, a race
     * between this scan and the eventual tryOccupy call is possible and
     * expected — seedSpecies() already handles a failed tryOccupy
     * gracefully.
     */
    private Point randomEmptyPoint() {
        int width = grid.getWidth();
        int height = grid.getHeight();
        Point candidate;
        int attempts = 0;
        do {
            candidate = new Point(
                ThreadLocalRandom.current().nextInt(width),
                ThreadLocalRandom.current().nextInt(height)
            );
            attempts++;
        } while (grid.peek(candidate) != null && attempts < width * height * 2);
        return candidate;
    }

    /**
     * Registers an organism in the living-organisms list and submits its
     * act() loop to the virtual thread executor. Called both during
     * initial seeding and from onOffspringCreated() whenever an organism
     * reproduces.
     */
    private void registerAndLaunch(Organism organism) {
        livingOrganisms.add(organism);
        executor.submit(() -> {
            try {
                organism.act();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Expected during shutdown() — not an error.
            } catch (Exception e) {
                // Defensive: a bug in one organism's act() must not take
                // down the shared executor or silently vanish. Every
                // other organism keeps running on its own thread
                // regardless of this one's failure.
                System.err.println(
                    "Organism " + organism.getId() + " (" + organism.glyph() + ") "
                        + "terminated abnormally: " + e
                );
            }
        });
    }

    /**
     * OffspringListener implementation. Every Plant/Herbivore/Carnivore
     * reproduction method calls this (via getOffspringListener(), which
     * resolves to this SimulationEngine instance) immediately after a
     * successful GridManager.tryOccupy() for the new offspring — this is
     * what actually gives offspring their own thread and tracking, closing
     * the gap noted in earlier revisions of this class.
     */
    @Override
    public void onOffspringCreated(Organism offspring) {
        registerAndLaunch(offspring);
    }

    /**
     * Starts the simulation. Currently a marker flag — seed() already
     * launches organism threads directly, so start() exists primarily
     * as a hook for future components (EcosystemMonitor, console renderer)
     * that should only begin once the simulation is officially running.
     */
    public void start() {
        running = true;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Gracefully shuts down the simulation: stops accepting new tasks,
     * interrupts all currently-running organism threads (each act() loop
     * checks isAlive() and propagates InterruptedException, so this
     * causes a clean exit rather than an abrupt kill), and waits briefly
     * for termination.
     */
    public void shutdown() throws InterruptedException {
        running = false;
        executor.shutdownNow();
        boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
        if (!terminated) {
            System.err.println("SimulationEngine: executor did not terminate within timeout");
        }
    }
}
