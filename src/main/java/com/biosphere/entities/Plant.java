package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A sessile primary producer. Plants never move — they gain energy
 * passively each tick (simulating photosynthesis) and, once energy is
 * high enough, attempt to spread a new Plant instance into a random
 * empty neighboring cell. This is the simulation's only energy source:
 * Herbivores gain energy exclusively by consuming Plants.
 *
 * Deliberately does NOT implement Movable — a Plant's "action" each tick
 * is grow-then-maybe-spread, never relocation. Keeping Plant outside the
 * Movable contract means the compiler itself prevents Plant from ever
 * being passed to movement-only logic, rather than relying on an
 * instanceof check somewhere deep in the simulation loop.
 */
public class Plant extends Organism {

    private static final int MAX_ENERGY = 100;
    private static final int STARTING_ENERGY = 20;
    private static final int GROWTH_PER_TICK = 5;
    private static final int REPRODUCTION_THRESHOLD = 80;
    private static final int REPRODUCTION_COST = 40;
    private static final long TICK_INTERVAL_MS = 1000L;

    public Plant(Point startPosition, GridManager gridManager, OffspringListener offspringListener) {
        super(startPosition, STARTING_ENERGY, MAX_ENERGY, gridManager, offspringListener);
    }

    /**
     * Per-tick lifecycle: photosynthesize (gain energy), then attempt to
     * spread into a neighboring cell if energy allows it. Runs on this
     * Plant's dedicated virtual thread until die() is called.
     */
    @Override
    public void act() throws InterruptedException {
        while (isAlive()) {
            photosynthesize();

            if (getEnergy() >= REPRODUCTION_THRESHOLD) {
                trySpread();
            }

            Thread.sleep(TICK_INTERVAL_MS);
        }
    }

    /**
     * Gains a fixed amount of energy each tick, representing continuous
     * photosynthesis. Uses the protected adjustEnergy() from Organism,
     * which is synchronized to guard against concurrent energy mutation.
     */
    private void photosynthesize() {
        adjustEnergy(GROWTH_PER_TICK);
    }

    /**
     * Attempts to place a new Plant instance in a random empty
     * neighboring cell, at the cost of REPRODUCTION_COST energy taken
     * from this plant. If no empty neighbor exists, or the chosen cell
     * gets claimed by a competing thread first, the energy cost is still
     * paid — reproduction attempts are not free just because they fail.
     */
    private void trySpread() {
        GridManager grid = getGrid();
        List<Point> neighbors = grid.neighborsOf(getPosition());
        List<Point> emptyNeighbors = neighbors.stream()
            .filter(p -> grid.peek(p) == null)
            .toList();

        if (emptyNeighbors.isEmpty()) {
            return;
        }

        Point target = emptyNeighbors.get(
            ThreadLocalRandom.current().nextInt(emptyNeighbors.size())
        );

        adjustEnergy(-REPRODUCTION_COST);

        Plant offspring = (Plant) clone();
        offspring.setEnergyDirect(STARTING_ENERGY);
        if (grid.tryOccupy(target, offspring)) {
            getOffspringListener().onOffspringCreated(offspring);
        }
        // SimulationEngine (the standard OffspringListener implementation)
        // is responsible for tracking the offspring and submitting its
        // act() loop to the virtual thread executor from here on.
    }

    @Override
    public char glyph() {
        return '*';
    }
}
