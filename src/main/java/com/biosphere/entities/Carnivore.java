package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An apex predator. Carnivores hunt Herbivores exclusively — Plants are
 * not a valid food source for a Carnivore, which the pattern-matching
 * switch in evaluateNeighbor() enforces structurally rather than via a
 * chain of instanceof checks.
 *
 * Unlike Herbivore's "eat in place" predation, a Carnivore that
 * successfully kills advances into the prey's former cell
 * (consumeAndAdvance) — a deliberate design choice made when this class
 * was built, since it gives a console renderer something visually
 * meaningful to show (the predator visibly closing the distance),
 * rather than the kill happening invisibly in place.
 */
public class Carnivore extends Animal {

    private static final int MAX_ENERGY = 120;
    private static final int STARTING_ENERGY = 40;
    private static final int ENERGY_FROM_HERBIVORE = 50;
    private static final int REPRODUCTION_THRESHOLD = 90;
    private static final int REPRODUCTION_COST = 45;

    public Carnivore(Point startPosition, GridManager gridManager, OffspringListener offspringListener) {
        super(startPosition, STARTING_ENERGY, MAX_ENERGY, gridManager, offspringListener);
    }

    /**
     * Per-tick behavior: hunt first (feeding takes priority over
     * reproduction and wandering, same rationale as Herbivore). If no
     * prey is adjacent, reproduce if energy allows, otherwise wander.
     */
    @Override
    protected void decideAction() {
        if (tryHuntAdjacentPrey()) {
            return;
        }
        if (getEnergy() >= REPRODUCTION_THRESHOLD) {
            tryReproduce();
            return;
        }
        wander();
    }

    /**
     * Scans neighboring cells for valid prey and attempts a kill via
     * GridManager.resolveInteraction(). Each neighbor's occupant is
     * classified with a pattern-matching switch — Herbivore is the only
     * huntable type; Plant, another Carnivore, or an empty cell are all
     * explicitly rejected as targets rather than silently falling
     * through, keeping the predation rule readable as one exhaustive
     * decision rather than scattered instanceof checks.
     *
     * Returns true if a Herbivore was successfully killed this tick.
     */
    private boolean tryHuntAdjacentPrey() {
        GridManager grid = getGrid();
        List<Point> neighbors = grid.neighborsOf(getPosition());

        for (Point neighborPoint : neighbors) {
            Organism occupant = grid.peek(neighborPoint);

            boolean isValidPrey = switch (occupant) {
                case Herbivore herbivore when herbivore.isAlive() -> true;
                case Plant ignoredPlant -> false;
                case Carnivore ignoredCarnivore -> false;
                case null -> false;
                default -> false;
            };

            if (!isValidPrey) {
                continue;
            }

            GridManager.InteractionResult result = grid.resolveInteraction(
                getPosition(),
                neighborPoint,
                this,
                occupant,
                (attacker, target) -> {
                    target.die();
                    ((Carnivore) attacker).adjustEnergy(ENERGY_FROM_HERBIVORE);
                    return GridManager.InteractionResult.consumeAndAdvance();
                }
            );

            if (!result.stale() && result.targetConsumed()) {
                return true;
            }
            // Stale means another Carnivore already claimed this prey
            // between our peek() and the locked resolve — move on to
            // the next neighbor rather than treating it as an error.
        }
        return false;
    }

    /**
     * Attempts to place a new Carnivore instance in a random empty
     * neighboring cell, at the cost of REPRODUCTION_COST energy. Mirrors
     * Plant/Herbivore's clone()-based reproduction.
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

        Carnivore offspring = (Carnivore) clone();
        offspring.setEnergyDirect(STARTING_ENERGY);
        if (grid.tryOccupy(target, offspring)) {
            getOffspringListener().onOffspringCreated(offspring);
        }
    }

    @Override
    public char glyph() {
        return 'C';
    }
}
