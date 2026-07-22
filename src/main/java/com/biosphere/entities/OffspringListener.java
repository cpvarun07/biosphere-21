package com.biosphere.entities;

/**
 * Callback contract for reporting a newly-created offspring so it can be
 * given its own thread and tracked as a living organism.
 *
 * Deliberately a narrow single-method interface rather than passing the
 * full SimulationEngine to every Organism — an Organism only needs to
 * announce "this new offspring exists," not access executor internals,
 * shutdown control, or the full living-organisms list. Implemented by
 * SimulationEngine; injected into each Organism at construction time.
 */
public interface OffspringListener {

    /**
     * Called exactly once per successfully-placed offspring, immediately
     * after GridManager.tryOccupy() confirms the offspring has a cell.
     * Implementations are expected to register the offspring for
     * tracking and submit its act() loop to the simulation's executor.
     */
    void onOffspringCreated(Organism offspring);
}
