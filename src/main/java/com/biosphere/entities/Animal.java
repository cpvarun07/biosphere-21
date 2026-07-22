package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Abstract base for mobile organisms (Herbivore, Carnivore). Unlike
 * Plant, an Animal's energy trends downward over time — movement and
 * metabolism cost energy — so animals must actively feed to survive,
 * which is what drives predation/grazing behavior in subclasses.
 *
 * Implements Movable, giving every Animal subclass the same
 * "moveTo/movementRange" contract. The actual per-tick decision of
 * *where* to move or *whether* to feed is left to subclasses via the
 * abstract decideAction() hook — Animal itself only provides the
 * mechanics (safe movement via GridManager, energy bookkeeping, thread
 * lifecycle loop), not the species-specific behavior policy.
 *
 * Uses Organism.getGrid() for grid access rather than storing its own
 * GridManager field — Organism is the single owner of that reference
 * (set once at construction, including through clone()-based
 * reproduction), so Animal does not duplicate it.
 */
public abstract class Animal extends Organism implements Movable {

    private static final long TICK_INTERVAL_MS = 800L;
    private static final int METABOLISM_COST_PER_TICK = 3;
    private static final int MOVEMENT_COST = 2;

    protected Animal(Point startPosition, int initialEnergy, int maxEnergy, GridManager gridManager,
                      OffspringListener offspringListener) {
        super(startPosition, initialEnergy, maxEnergy, gridManager, offspringListener);
    }

    /**
     * Shared per-tick loop for all animals: pay metabolism cost, let the
     * subclass decide and execute its action for this tick (move, feed,
     * attack, or stay put), then sleep until the next tick. Runs on this
     * Animal's dedicated virtual thread until die() is called — either
     * from starvation (adjustEnergy driving energy to 0 in Organism) or
     * from being consumed by a predator.
     */
    @Override
    public final void act() throws InterruptedException {
        while (isAlive()) {
            adjustEnergy(-METABOLISM_COST_PER_TICK);

            if (isAlive()) {
                decideAction();
            }

            Thread.sleep(TICK_INTERVAL_MS);
        }
    }

    /**
     * Species-specific per-tick behavior policy. Called once per tick,
     * after metabolism cost has been deducted and only if the animal
     * survived that deduction. Implementations decide whether to wander,
     * hunt/graze, or hold position, and are responsible for invoking
     * moveTo() or subclass-specific interaction methods as appropriate.
     */
    protected abstract void decideAction();

    /**
     * Default wandering behavior available to all Animal subclasses:
     * picks a random empty neighboring cell and attempts to move there.
     * Subclasses are free to use this as-is for basic movement, or
     * override decideAction() entirely for more purposeful behavior
     * (e.g. a Carnivore scanning neighbors for prey before falling back
     * to wandering).
     */
    protected final void wander() {
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
        moveTo(target, grid);
    }

    /**
     * Movable implementation: attempts an atomic move via GridManager,
     * paying MOVEMENT_COST energy only if the move actually succeeds.
     * A failed move (destination claimed by another thread first) costs
     * nothing extra — the animal simply didn't go anywhere, so
     * metabolism is the only cost incurred that tick via act()'s
     * unconditional deduction.
     */
    @Override
    public boolean moveTo(Point target, GridManager gridManager) {
        boolean moved = gridManager.tryMove(getPosition(), target, this);
        if (moved) {
            adjustEnergy(-MOVEMENT_COST);
        }
        return moved;
    }

    /**
     * Default mobility: adjacent cells only (including diagonals).
     * Subclasses may override for species with different range.
     */
    @Override
    public int movementRange() {
        return 1;
    }
}
