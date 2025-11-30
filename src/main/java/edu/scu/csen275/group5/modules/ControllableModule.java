package edu.scu.csen275.group5.modules;

public interface ControllableModule extends GardenModule {
    void setIntensity(int level);
    int getCurrentIntensity();
}
