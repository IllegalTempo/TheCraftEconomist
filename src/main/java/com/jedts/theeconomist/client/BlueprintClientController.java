package com.jedts.theeconomist.client;

/** Client-only controller placeholder; rendering/input is added by the blueprint packet task. */
public final class BlueprintClientController {
    private static boolean designing;
    private static boolean placing;

    private BlueprintClientController() { }

    public static void startDesign() { designing = true; placing = false; }
    public static void startPlacement() { placing = true; designing = false; }
    public static void cancel() { designing = false; placing = false; }
    public static boolean designing() { return designing; }
    public static boolean placing() { return placing; }
}
