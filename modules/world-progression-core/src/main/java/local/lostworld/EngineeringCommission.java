package local.lostworld;

import java.util.Set;

final class EngineeringCommission {
    private static final String HERITAGE = "world_progression_core:heritage/";
    static final String PUMP = "create:mechanical_pump_0";
    static final String SOURCE = "create:water_supply";
    static final String DRAIN = "create:drain";
    static final String SPOUT = "create:spout";
    static final String PROTOTYPE = HERITAGE + "controller_prototype";
    static final String REGULATED = "create:speed_controller";
    static final String HEAT = HERITAGE + "engineering_heat";
    static final String ASSEMBLY = HERITAGE + "engineering_assembly";
    static final Set<String> SOURCES = Set.of(PUMP, SOURCE, DRAIN, SPOUT, PROTOTYPE, REGULATED,
            HEAT, ASSEMBLY, "create:burner", HERITAGE + "brass_stock", "create:deployer", "create:precision_mechanism");

    private EngineeringCommission() {}

    static boolean heat(Set<String> facts) {
        return facts.contains(HEAT) || facts.contains("create:burner") && facts.contains(HERITAGE + "brass_stock");
    }

    static boolean assembly(Set<String> facts) {
        return facts.contains(ASSEMBLY) || facts.contains(PROTOTYPE)
                || facts.contains(SPOUT) && facts.contains("create:deployer") && facts.contains("create:precision_mechanism");
    }

    static boolean ready(Set<String> facts) {
        // The marked prototype can only be produced by the filling, deploying and pressing sequence.
        return heat(facts) && facts.contains(PUMP) && (facts.contains(SOURCE) || facts.contains(DRAIN))
                && facts.contains(PROTOTYPE) && facts.contains(REGULATED);
    }
}
