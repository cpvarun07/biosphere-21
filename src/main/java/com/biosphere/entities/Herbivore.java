package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A primary consumer. Herbivores wander the grid and, when a Plant is
 * adjacent, consume it for energy. Above an energy threshold, a
 * Herbivore reproduces via clone() into an empty neighboring cell, the
 * same pattern Plant uses.
 *
 * Predation here is deliberately "eat in place" — a Herbivore does not
 * move into the cell the Plant occupied, it just consumes it and stays
 * put. (Carnivore, by contrast, advances into its prey's cell on a kill
 * — see Carnivore for that design choice.)
 */
public class Herbivore extends Animal {

    private static final int MAX_ENERGY = 80;
    private static final int STARTING_ENERGY = 30;
    private static final int ENERGY_FROM_PLANT = 25;
    private static final int REPRODUCTION_THRESHOLD = 60;
    private static final int REPRODUCTION_COST = 30;

    public Herbivore(Point startPosition, GridManager gridManager, OffspringListener offspringListener) {
        super(startPosition, STARTING_ENERGY, MAX_ENERGY, gridManager, offspringListener);
    }

    /**
     * Per-tick behavior: try to eat an adjacent Plant first (feeding takes
     * priority over movement, since a Herbivore that can eat this tick
     * shouldn't wander away from food). If nothing to eat, reproduce if
     * energy allows, otherwise wander.
     */
    @Override
    protected void decideAction() {
        if (tryEatAdjacentPlant()) {
            return;
        }
        if (getEnergy() >= REPRODUCTION_THRESHOLD) {
            tryReproduce();
            return;
        }
        wander();
    }

    /**
     * Scans neighboring cells for the first Plant found and attempts to
     * consume it atomically via GridManager.resolveInteraction(). Uses
     * pattern matching over the neighbor's occupant type (Plant vs.
     * anything else vs. empty) to decide whether this neighbor is even a
     * valid target before attempting the interaction — avoiding a wasted
     * lock/resolve call on cells that obviously aren't food.
     *
     * Returns true if a Plant was successfully consumed this tick.
     */
    private boolean tryEatAdjacentPlant() {
        GridManager grid = getGrid();
        List<Point> neighbors = grid.neighborsOf(getPosition());

        for (Point neighborPoint : neighbors) {
            Organism occupant = grid.peek(neighborPoint);

            boolean isEdiblePlant = switch (occupant) {
                case Plant plant when plant.isAlive() -> true;
                case null -> false;
                default -> false;
            };

            if (!isEdiblePlant) {
                continue;
            }

            GridManager.InteractionResult result = grid.resolveInteraction(
                getPosition(),
                neighborPoint,
                this,
                occupant,
                (attacker, target) -> {
                    target.die();
                    ((Herbivore) attacker).adjustEnergy(ENERGY_FROM_PLANT);
                    return GridManager.InteractionResult.consumeInPlace();
                }
            );

            if (!result.stale() && result.targetConsumed()) {
                return true;
            }
            // Stale result means another thread already claimed this
            // Plant (e.g. a competing Herbivore got there first) —
            // simply move on and check the next neighbor rather than
            // treating it as an error.
        }
        return false;
    }

    /**
     * Attempts to place a new Herbivore instance in a random empty
     * neighboring cell, at the cost of REPRODUCTION_COST energy. Mirrors
     * Plant.trySpread()'s clone()-based approach.
     */
    private void tryReproduce() {
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

        Herbivore offspring = (Herbivore) clone();
        offspring.setEnergyDirect(STARTING_ENERGY);
        if (grid.tryOccupy(target, offspring)) {
            getOffspringListener().onOffspringCreated(offspring);
        }
    }

    @Override
    public char glyph() {
        return 'H';
    }
}
