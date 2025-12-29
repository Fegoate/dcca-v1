import java.util.List;
import java.util.stream.Collectors;

public class InterpolationEngine {
    private List<RCSData> rcsDataList;

    public InterpolationEngine(List<RCSData> rcsDataList) {
        this.rcsDataList = rcsDataList;
    }

    public double calculateRCS(double frequency, double incidentElevation, double incidentAzimuth, double theta, double phi) {
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

        // 找到最接近的入射俯仰角点
        List<Double> incidentElevations = rcsDataList.stream()
                .map(RCSData::getIncidentElevation)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        double closestElev1 = incidentElevations.get(0);
        double closestElev2 = incidentElevations.get(0);

        for (double e : incidentElevations) {
            if (e <= incidentElevation) {
                closestElev1 = e;
            }
            if (e >= incidentElevation) {
                closestElev2 = e;
                break;
            }
        }

        // 找到最接近的入射方位角点
        List<Double> incidentAzimuths = rcsDataList.stream()
                .map(RCSData::getIncidentAzimuth)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        double closestAz1 = incidentAzimuths.get(0);
        double closestAz2 = incidentAzimuths.get(0);

        for (double a : incidentAzimuths) {
            if (a <= incidentAzimuth) {
                closestAz1 = a;
            }
            if (a >= incidentAzimuth) {
                closestAz2 = a;
                break;
            }
        }

        // 三维线性插值（频率、入射俯仰、入射方位）
        double f1 = closestFreq1;
        double f2 = closestFreq2;
        double e1 = closestElev1;
        double e2 = closestElev2;
        double a1 = closestAz1;
        double a2 = closestAz2;

        double rcs_f1_e1_a1 = getClosestRCS(f1, e1, a1, theta, phi);
        double rcs_f1_e1_a2 = getClosestRCS(f1, e1, a2, theta, phi);
        double rcs_f1_e2_a1 = getClosestRCS(f1, e2, a1, theta, phi);
        double rcs_f1_e2_a2 = getClosestRCS(f1, e2, a2, theta, phi);

        double rcs_f2_e1_a1 = getClosestRCS(f2, e1, a1, theta, phi);
        double rcs_f2_e1_a2 = getClosestRCS(f2, e1, a2, theta, phi);
        double rcs_f2_e2_a1 = getClosestRCS(f2, e2, a1, theta, phi);
        double rcs_f2_e2_a2 = getClosestRCS(f2, e2, a2, theta, phi);

        double rcsFreq1 = bilinear(rcs_f1_e1_a1, rcs_f1_e1_a2, rcs_f1_e2_a1, rcs_f1_e2_a2,
                a1, a2, e1, e2, incidentAzimuth, incidentElevation);
        double rcsFreq2 = bilinear(rcs_f2_e1_a1, rcs_f2_e1_a2, rcs_f2_e2_a1, rcs_f2_e2_a2,
                a1, a2, e1, e2, incidentAzimuth, incidentElevation);

        return interpolate(rcsFreq1, rcsFreq2, f1, f2, frequency);
    }

    private double getClosestRCS(double frequency, double incidentElevation, double incidentAzimuth, double theta, double phi) {
        // 找到最接近的theta和phi的数据点
        RCSData closestData = null;
        double minDistance = Double.MAX_VALUE;

        for (RCSData data : rcsDataList) {
            if (Math.abs(data.getFrequency() - frequency) < 0.1
                    && Math.abs(data.getIncidentElevation() - incidentElevation) < 0.1
                    && Math.abs(data.getIncidentAzimuth() - incidentAzimuth) < 0.1) {
                double distance = Math.sqrt(
                        Math.pow(data.getTheta() - theta, 2) +
                        Math.pow(data.getPhi() - phi, 2)
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
}