import java.util.List;
import java.util.stream.Collectors;

public class InterpolationEngine {
    private List<RCSData> rcsDataList;

    public InterpolationEngine(List<RCSData> rcsDataList) {
        this.rcsDataList = rcsDataList;
    }

    public double calculateRCS(double frequency, double incidentElevation, double incidentAzimuth, double theta, double phi) {
        // 角度统一做 0~360 周期化，确保 0° 与 360° 等价
        double normalizedIncidentElevation = AngleUtils.normalize360(incidentElevation);
        double normalizedIncidentAzimuth = AngleUtils.normalize360(incidentAzimuth);
        double normalizedTheta = AngleUtils.normalize360(theta);
        double normalizedPhi = AngleUtils.normalize360(phi);

        // 找到最接近的频率点
        List<Double> frequencies = rcsDataList.stream()
                .map(RCSData::getFrequency)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        double closestFreq1 = frequencies.get(0);
        double closestFreq2 = frequencies.get(0);

        for (double f : frequencies) {
            if (f <= frequency) {
                closestFreq1 = f;
            }
            if (f >= frequency) {
                closestFreq2 = f;
                break;
            }
        }

        // 找到最接近的入射俯仰角点（周期化）
        List<Double> incidentElevations = rcsDataList.stream()
                .map(RCSData::getIncidentElevation)
                .map(AngleUtils::normalize360)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        double[] elevBounds = findBoundingAngles(incidentElevations, normalizedIncidentElevation);

        // 找到最接近的入射方位角点（周期化）
        List<Double> incidentAzimuths = rcsDataList.stream()
                .map(RCSData::getIncidentAzimuth)
                .map(AngleUtils::normalize360)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        double[] azBounds = findBoundingAngles(incidentAzimuths, normalizedIncidentAzimuth);

        // 三维线性插值（频率、入射俯仰、入射方位）
        double f1 = closestFreq1;
        double f2 = closestFreq2;
        double e1 = elevBounds[0];
        double e2 = elevBounds[1];
        double a1 = azBounds[0];
        double a2 = azBounds[1];

        // 若上界小于下界，说明跨越 360°，插值时将上界抬升 360°
        double inputElev = normalizedIncidentElevation;
        if (e2 < e1) {
            e2 += 360.0;
            if (inputElev < e1) {
                inputElev += 360.0;
            }
        }

        double inputAz = normalizedIncidentAzimuth;
        if (a2 < a1) {
            a2 += 360.0;
            if (inputAz < a1) {
                inputAz += 360.0;
            }
        }

        double rcs_f1_e1_a1 = getClosestRCS(f1, e1, a1, normalizedTheta, normalizedPhi);
        double rcs_f1_e1_a2 = getClosestRCS(f1, e1, a2, normalizedTheta, normalizedPhi);
        double rcs_f1_e2_a1 = getClosestRCS(f1, e2, a1, normalizedTheta, normalizedPhi);
        double rcs_f1_e2_a2 = getClosestRCS(f1, e2, a2, normalizedTheta, normalizedPhi);

        double rcs_f2_e1_a1 = getClosestRCS(f2, e1, a1, normalizedTheta, normalizedPhi);
        double rcs_f2_e1_a2 = getClosestRCS(f2, e1, a2, normalizedTheta, normalizedPhi);
        double rcs_f2_e2_a1 = getClosestRCS(f2, e2, a1, normalizedTheta, normalizedPhi);
        double rcs_f2_e2_a2 = getClosestRCS(f2, e2, a2, normalizedTheta, normalizedPhi);

        double rcsFreq1 = bilinear(rcs_f1_e1_a1, rcs_f1_e1_a2, rcs_f1_e2_a1, rcs_f1_e2_a2,
                a1, a2, e1, e2, inputAz, inputElev);
        double rcsFreq2 = bilinear(rcs_f2_e1_a1, rcs_f2_e1_a2, rcs_f2_e2_a1, rcs_f2_e2_a2,
                a1, a2, e1, e2, inputAz, inputElev);

        return interpolate(rcsFreq1, rcsFreq2, f1, f2, frequency);
    }

    private double getClosestRCS(double frequency, double incidentElevation, double incidentAzimuth, double theta, double phi) {
        // 找到最接近的theta和phi的数据点
        RCSData closestData = null;
        double minDistance = Double.MAX_VALUE;

        for (RCSData data : rcsDataList) {
            if (Math.abs(data.getFrequency() - frequency) < 0.1
                    && AngleUtils.circularDifference(data.getIncidentElevation(), incidentElevation) < 0.1
                    && AngleUtils.circularDifference(data.getIncidentAzimuth(), incidentAzimuth) < 0.1) {
                double deltaTheta = AngleUtils.circularDifference(data.getTheta(), theta);
                double deltaPhi = AngleUtils.circularDifference(data.getPhi(), phi);
                double distance = Math.sqrt(
                        Math.pow(deltaTheta, 2) +
                        Math.pow(deltaPhi, 2)
                );

                if (distance < minDistance) {
                    minDistance = distance;
                    closestData = data;
                }
            }
        }

        if (closestData == null) {
            // 如果找不到匹配的数据点，返回默认值
            return -50.0;
        }

        return closestData.getRcsValue();
    }

    private double interpolate(double value1, double value2, double x1, double x2, double x) {
        if (x1 == x2) {
            return value1;
        }

        return value1 + (value2 - value1) * (x - x1) / (x2 - x1);
    }

    private double bilinear(double q11, double q12, double q21, double q22,
                            double x1, double x2, double y1, double y2,
                            double x, double y) {
        if (x1 == x2 && y1 == y2) {
            return q11;
        }

        if (x1 == x2) {
            return interpolate(q11, q21, y1, y2, y);
        }

        if (y1 == y2) {
            return interpolate(q11, q12, x1, x2, x);
        }

        double r1 = interpolate(q11, q12, x1, x2, x);
        double r2 = interpolate(q21, q22, x1, x2, x);
        return interpolate(r1, r2, y1, y2, y);
    }

    /**
     * 计算周期角度的上下界，保证 0°/360° 连接处连续。
     */
    private double[] findBoundingAngles(List<Double> sortedAngles, double targetAngle) {
        if (sortedAngles.isEmpty()) {
            return new double[]{0.0, 0.0};
        }

        if (sortedAngles.size() == 1) {
            return new double[]{sortedAngles.get(0), sortedAngles.get(0)};
        }

        double target = AngleUtils.normalize360(targetAngle);
        double lower = sortedAngles.get(sortedAngles.size() - 1);
        double upper = sortedAngles.get(0) + 360.0;

        for (int i = 0; i < sortedAngles.size(); i++) {
            double current = sortedAngles.get(i);
            double next = (i == sortedAngles.size() - 1) ? sortedAngles.get(0) + 360.0 : sortedAngles.get(i + 1);

            if (target >= current && target <= next) {
                lower = current;
                upper = next;
                break;
            }
        }

        return new double[]{lower, upper};
    }
}