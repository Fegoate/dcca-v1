public class AngleUtils {
    /**
     * 将角度规范化到 [0, 360) 区间。
     */
    public static double normalize360(double angle) {
        double normalized = angle % 360.0;
        if (normalized < 0) {
            normalized += 360.0;
        }
        // 避免出现 360.0，保持闭区间左闭右开
        if (normalized == 360.0) {
            return 0.0;
        }
        return normalized;
    }

    /**
     * 返回两个角度之间的最小环向差值（单位：度）。
     */
    public static double circularDifference(double a, double b) {
        double diff = Math.abs(normalize360(a) - normalize360(b));
        return Math.min(diff, 360.0 - diff);
    }
}
