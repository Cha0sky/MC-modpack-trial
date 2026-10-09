package com.infinite.core;

import com.infinite.core.gen.GenerationMath;

/** Independent interval fixture transcribed from the audited 1.0.0 behavior. */
public final class GenerationMathCheck {
    public static void main(String[] args) {
        int[] rates = {360, 180, 72, 36, 24, 12, 6, 1};
        int[][] expected = {
            {360,300,257,225,200,180,180,180}, {225,180,150,129,113,100,90,90},
            {120,90,72,60,51,45,40,36}, {90,60,45,36,30,26,23,20},
            {120,60,40,30,24,20,17,15}, {120,60,30,20,15,12,10,9},
            {60,60,30,15,10,8,6,5}, {10,10,10,5,3,2,1,1}
        };
        for (int machine = 0; machine < 8; machine++) {
            for (int template = 0; template < 8; template++) {
                int actual = GenerationMath.interval(rates[machine], machine, template);
                if (actual != expected[machine][template]) throw new AssertionError(machine + "/" + template + ": " + actual);
            }
        }
        if (GenerationMath.multiplier(7, 0) != 0.1 || GenerationMath.multiplier(0, 7) != 2.0) {
            throw new AssertionError("Multiplier clamps differ from baseline");
        }
        System.out.println("PASS: 64 baseline interval pairs, 0.1/2.0 clamps, Java rounding and one-tick floor.");
    }
}
