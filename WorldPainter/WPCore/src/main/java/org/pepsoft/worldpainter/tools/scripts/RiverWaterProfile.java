package org.pepsoft.worldpainter.tools.scripts;

/** Shared voxel water and excavation rules for routing and final carving. */
final class RiverWaterProfile {
    static final double MIN_WET_DEPTH = .65;
    private RiverWaterProfile() { }

    static int dryWaterCeiling(double height) {
        return Math.round((float) height);
    }

    static double requiredCut(double height, int water, double wetDepth) {
        return height - water + wetDepth;
    }

    static boolean exceedsCut(double height, int water, double wetDepth, double maximumCut) {
        return requiredCut(height, water, wetDepth) > maximumCut + .001;
    }

    /** Spread abrupt drops upstream, without ever raising water or changing
     * the receiving level. Chainage is in blocks, not sample indices. */
    static void spreadDrops(int[] levels, double[] chainage, double spacing) {
        int end=levels.length-1;
        for(int i=levels.length-2;i>=0;i--) {
            while(end>i && chainage[end]-chainage[i]>spacing)end--;
            if(end>i) levels[i]=Math.min(levels[i],levels[end]+1);
            levels[i]=Math.max(levels[i],levels[i+1]);
        }
    }

    static double gradeStepSpacing(double width) { return Math.max(3, width/4+1); }

    static double sectionDepth(double distance,double radius,double depth,boolean linear) {
        double wetRadius=Math.max(1.5,radius*.60);
        if(distance>radius)return 0;
        double t=distance<=wetRadius?distance/wetRadius:(distance-wetRadius)/Math.max(.001,radius-wetRadius);
        t=Math.max(0,Math.min(1,t));
        double shape=linear?t:t*t*(3-2*t);
        return distance<=wetRadius?depth-(depth-MIN_WET_DEPTH)*shape:MIN_WET_DEPTH*(1-shape);
    }
}
