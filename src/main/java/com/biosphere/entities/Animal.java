package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public abstract class Animal extends Organism implements Movable {
    private static final long TICK_INTERVAL_MS = 800L;
    protected static final int METABOLISM_COST_PER_TICK = 2;
    protected static final int MOVEMENT_COST = 1;

    protected Animal(Point startPosition, int initialEnergy, int maxEnergy,
                     GridManager gridManager, OffspringListener listener) {
        super(startPosition, initialEnergy, maxEnergy, gridManager, listener);
    }

    @Override
    public final void act() throws InterruptedException {
        while (isAlive()) {
            adjustEnergy(-METABOLISM_COST_PER_TICK);
            if (isAlive()) decideAction();
            Thread.sleep(TICK_INTERVAL_MS);
        }
    }

    protected abstract void decideAction();

    protected final void wander() {
        List<Point> empty = getGrid().neighborsOf(getPosition()).stream()
            .filter(p -> getGrid().peek(p) == null)
            .toList();
        if (empty.isEmpty()) return;
        moveTo(empty.get(ThreadLocalRandom.current().nextInt(empty.size())), getGrid());
    }

    @Override
    public boolean moveTo(Point target, GridManager gridManager) {
        boolean moved = gridManager.tryMove(getPosition(), target, this);
        if (moved) adjustEnergy(-MOVEMENT_COST);
        return moved;
    }

    @Override
    public int movementRange() { return 1; }
}
