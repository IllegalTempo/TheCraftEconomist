package com.jedts.theeconomist.citizen.house;

public final class HouseSiteRules {
    private HouseSiteRules() {}
    public record Site(boolean drySolidGround, int maxHeightDifference, boolean openEntrance) {}
    public static boolean accepts(Site site) {
        return site != null && site.drySolidGround() && site.maxHeightDifference() >= 0
                && site.maxHeightDifference() <= 2 && site.openEntrance();
    }
}
