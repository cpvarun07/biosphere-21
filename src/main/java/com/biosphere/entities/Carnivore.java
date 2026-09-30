package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** C eats H only. Plants P and mature trees T are never edible. */
public class Carnivore extends Animal {
    private static final int MAX_ENERGY = 140;
    private static final int STARTING_ENERGY = 70;
    private static final int ENERGY_FROM_HERBIVORE = 55;
    private static final int REPRODUCTION_THRESHOLD = 105;
    private static final int REPRODUCTION_COST = 50;

    public Carnivore(Point startPosition, GridManager gridManager, OffspringListener listener) {
        super(startPosition, STARTING_ENERGY, MAX_ENERGY, gridManager, listener);
    }

    @Override
    protected void decideAction() {
        if (tryEatHerbivore()) return;
        if (getEnergy() >= REPRODUCTION_THRESHOLD) tryReproduce();
        else wander();
    }

    private boolean tryEatHerbivore() {
        GridManager grid = getGrid();
        for (Point p : grid.neighborsOf(getPosition())) {
            Organism target = grid.peek(p);
            if (!(target instanceof Herbivore herbivore) || !herbivore.isAlive()) continue;

            GridManager.InteractionResult result = grid.resolveInteraction(
                getPosition(), p, this, target,
                (attacker, prey) -> {
                    if (!(prey instanceof Herbivore) || !prey.isAlive()) {
                        return GridManager.InteractionResult.noEffect();
                    }
                    prey.die();
                    ((Carnivore) attacker).adjustEnergy(ENERGY_FROM_HERBIVORE);
                    return GridManager.InteractionResult.consumeAndAdvance();
                }
            );
            if (!result.stale() && result.targetConsumed()) return true;
        }
        return false;
    }

    private void tryReproduce() {
        GridManager grid = getGrid();
        List<Point> empty = grid.neighborsOf(getPosition()).stream()
            .filter(p -> grid.peek(p) == null).toList();
        if (empty.isEmpty()) return;

        Point target = empty.get(ThreadLocalRandom.current().nextInt(empty.size()));
        Carnivore offspring = (Carnivore) clone();
        offspring.setEnergyDirect(STARTING_ENERGY);
        if (grid.tryOccupy(target, offspring)) {
            adjustEnergy(-REPRODUCTION_COST);
            getOffspringListener().onOffspringCreated(offspring);
        }
    }

    @Override
    public char glyph() { return 'C'; }
}
