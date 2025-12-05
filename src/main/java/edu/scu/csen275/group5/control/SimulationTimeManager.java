package edu.scu.csen275.group5.control;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages simulation time tracking for the garden.
 * Handles hours elapsed and slices within each hour.
 */
public class SimulationTimeManager {
    private static final int SLICES_PER_HOUR = 6; // 6 slices = 1 hour = 144 slices/day
    
    private final AtomicInteger hoursElapsed;
    private final AtomicInteger slicesProcessedThisHour;
    
    public SimulationTimeManager() {
        this.hoursElapsed = new AtomicInteger(0);
        this.slicesProcessedThisHour = new AtomicInteger(0);
    }
    
    /**
     * Reset time to hour 0
     */
    public void reset() {
        hoursElapsed.set(0);
        slicesProcessedThisHour.set(0);
    }
    
    /**
     * Advance to next hour
     */
    public void advanceHour() {
        hoursElapsed.incrementAndGet();
        slicesProcessedThisHour.set(0);
    }
    
    /**
     * Process N slices for the current hour
     * @param count number of slices to process
     * @return actual number of slices processed (may be capped)
     */
    public int processSlices(int count) {
        int currentSlices = slicesProcessedThisHour.get();
        int remaining = SLICES_PER_HOUR - currentSlices;
        int toProcess = Math.min(count, remaining);
        
        slicesProcessedThisHour.addAndGet(toProcess);
        return toProcess;
    }
    
    /**
     * Get remaining slices for current hour
     */
    public int getRemainingSlices() {
        return SLICES_PER_HOUR - slicesProcessedThisHour.get();
    }
    
    /**
     * Check if current hour is complete
     */
    public boolean isHourComplete() {
        return slicesProcessedThisHour.get() >= SLICES_PER_HOUR;
    }
    
    /**
     * Get current hour of day (0-23)
     */
    public int getCurrentHourOfDay() {
        return hoursElapsed.get() % 24;
    }
    
    /**
     * Get total hours elapsed
     */
    public int getHoursElapsed() {
        return hoursElapsed.get();
    }
    
    /**
     * Get slices processed this hour
     */
    public int getSlicesProcessedThisHour() {
        return slicesProcessedThisHour.get();
    }
    
    /**
     * Get total slices per hour
     */
    public static int getSlicesPerHour() {
        return SLICES_PER_HOUR;
    }
}
