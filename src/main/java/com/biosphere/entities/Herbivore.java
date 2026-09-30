package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** H eats young P only. Mature T is never edible. */
public class Herbivore extends Animal {
    private static final int MAX_ENERGY = 100;
    private static final int STARTING_ENERGY = 45;
    private static final int ENERGY_FROM_PLANT = 30;
    private static final int REPRODUCTION_THRESHOLD = 75;
    private static final int REPRODUCTION_COST = 35;

    public Herbivore(Point startPosition, GridManager gridManager, OffspringListener listener) {
        super(startPosition, STARTING_ENERGY, MAX_ENERGY, gridManager, listener);
    }

    @Override
    protected void decideAction() {
        if (tryEatPlant()) return;
        if (getEnergy() >= REPRODUCTION_THRESHOLD) tryReproduce();
        else wander();
    }

    private boolean tryEatPlant() {
        GridManager grid = getGrid();
        for (Point p : grid.neighborsOf(getPosition())) {
            Organism target = grid.peek(p);
            if (!(target instanceof Plant plant) || !plant.isEdible()) continue;

            GridManager.InteractionResult result = grid.resolveInteraction(
                getPosition(), p, this, target,
                (attacker, prey) -> {
                    Plant plantTarget = (Plant) prey;
                    if (!plantTarget.isEdible()) return GridManager.InteractionResult.noEffect();
                    plantTarget.die();
                    ((Herbivore) attacker).adjustEnergy(ENERGY_FROM_PLANT);
                    return GridManager.InteractionResult.consumeInPlace();
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
        Herbivore offspring = (Herbivore) clone();
        offspring.setEnergyDirect(STARTING_ENERGY);
        if (grid.tryOccupy(target, offspring)) {
            adjustEnergy(-REPRODUCTION_COST);
            getOffspringListener().onOffspringCreated(offspring);
        }
    }

    @Override
    public char glyph() { return 'H'; }
}
