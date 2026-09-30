package com.biosphere.entities;

import com.biosphere.core.GridManager;
import com.biosphere.core.Point;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class Organism implements Cloneable {
    private static final AtomicLong ID_GENERATOR = new AtomicLong();

    private long id;
    private AtomicReference<Point> position;
    private AtomicBoolean alive;
    private volatile int energy;
    private final int maxEnergy;
    private final GridManager grid;
    private final OffspringListener offspringListener;

    protected Organism(Point startPosition, int initialEnergy, int maxEnergy,
                       GridManager grid, OffspringListener offspringListener) {
        if (startPosition == null || grid == null || offspringListener == null) {
            throw new IllegalArgumentException("Organism requires position, grid and offspring listener");
        }
        if (initialEnergy <= 0 || maxEnergy <= 0 || initialEnergy > maxEnergy) {
            throw new IllegalArgumentException("Invalid energy configuration");
        }
        this.id = ID_GENERATOR.incrementAndGet();
        this.position = new AtomicReference<>(startPosition);
        this.alive = new AtomicBoolean(true);
        this.energy = initialEnergy;
        this.maxEnergy = maxEnergy;
        this.grid = grid;
        this.offspringListener = offspringListener;
    }

    public abstract void act() throws InterruptedException;
    public abstract char glyph();

    public final void die() {
        if (alive.compareAndSet(true, false)) {
            grid.vacate(position.get(), this);
            onDeath();
        }
    }

    protected void onDeath() { }

    public final boolean isAlive() { return alive.get(); }
    public final Point getPosition() { return position.get(); }
    public final int getEnergy() { return energy; }
    public final int getMaxEnergy() { return maxEnergy; }
    public final long getId() { return id; }

    public final void updatePosition(Point newPosition) {
        position.set(newPosition);
    }

    protected final synchronized void adjustEnergy(int delta) {
        energy = Math.max(0, Math.min(maxEnergy, energy + delta));
        if (energy == 0) die();
    }

    protected final synchronized void setEnergyDirect(int value) {
        energy = Math.max(0, Math.min(maxEnergy, value));
    }

    protected final GridManager getGrid() { return grid; }
    protected final OffspringListener getOffspringListener() { return offspringListener; }

    @Override
    public Organism clone() {
        try {
            Organism copy = (Organism) super.clone();
            copy.id = ID_GENERATOR.incrementAndGet();
            copy.position = new AtomicReference<>(getPosition());
            copy.alive = new AtomicBoolean(true);
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Organism organism && organism.id == id;
    }

    @Override
    public int hashCode() { return Long.hashCode(id); }
}
