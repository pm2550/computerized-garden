package edu.scu.csen275.group5.modules;

import edu.scu.csen275.group5.core.Garden;
import java.util.Map;


public interface GardenModule {
    void activate();
    void deactivate();
    boolean isActive();
    String getModuleName();
    void update();
}
