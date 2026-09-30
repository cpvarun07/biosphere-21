package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Producer lifecycle:
 * P = young plant. Herbivores may eat P.
 * T = mature tree. No organism can eat T.
 */
public class Plant extends Organism {
    private static final int MAX_ENERGY = 100;
    private static final int STARTING_ENERGY = 20;
    private static final int GROWTH_PER_TICK = 5;
    private static final int TREE_THRESHOLD = 60;
    private static final int REPRODUCTION_THRESHOLD = 45;
    private static final int REPRODUCTION_COST = 25;
    private static final long TICK_INTERVAL_MS = 1000L;

    private volatile boolean tree;
    private int ageTicks;
    private static final int TREE_MATURATION_TICKS = 20;

    public Plant(Point startPosition, GridManager gridManager, OffspringListener listener) {
        super(startPosition, STARTING_ENERGY, MAX_ENERGY, gridManager, listener);
    }

    @Override
    public void act() throws InterruptedException {
        while (isAlive()) {
            adjustEnergy(GROWTH_PER_TICK);
            ageTicks++;

            // A young P becomes a mature T after several growth cycles.
            // This is age-based so reproduction cannot keep resetting the plant
            // before it reaches the tree stage.
            if (!tree && (ageTicks >= TREE_MATURATION_TICKS || getEnergy() >= TREE_THRESHOLD)) {
                tree = true;
            }

            if (!tree && getEnergy() >= REPRODUCTION_THRESHOLD) {
                trySpread();
            }

            Thread.sleep(TICK_INTERVAL_MS);
        }
    }

    public boolean isTree() { return tree; }
    public boolean isEdible() { return isAlive() && !tree; }

    private void trySpread() {
        GridManager grid = getGrid();
        List<Point> empty = grid.neighborsOf(getPosition()).stream()
            .filter(p -> grid.peek(p) == null)
            .toList();
        if (empty.isEmpty()) return;

        Point target = empty.get(ThreadLocalRandom.current().nextInt(empty.size()));
        Plant offspring = (Plant) clone();
        offspring.tree = false;
        offspring.ageTicks = 0;
        offspring.setEnergyDirect(STARTING_ENERGY);

        if (grid.tryOccupy(target, offspring)) {
            adjustEnergy(-REPRODUCTION_COST);
            getOffspringListener().onOffspringCreated(offspring);
        }
    }

    @Override
    public char glyph() { return tree ? 'T' : 'P'; }
}
