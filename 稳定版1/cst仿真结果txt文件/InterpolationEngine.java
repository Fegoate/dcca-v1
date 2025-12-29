import java.util.List;
import java.util.stream.Collectors;

public class InterpolationEngine {
    private List<RCSData> rcsDataList;

    public InterpolationEngine(List<RCSData> rcsDataList) {
        this.rcsDataList = rcsDataList;
    }

    public double calculateRCS(double frequency, double incidentDirection, double theta, double phi) {
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

        // 找到最接近的入射方向点
        List<Double> incidentDirections = rcsDataList.stream()
                .map(RCSData::getIncidentDirection)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        double closestDir1 = incidentDirections.get(0);
        double closestDir2 = incidentDirections.get(0);

        for (double d : incidentDirections) {
            if (d <= incidentDirection) {
                closestDir1 = d;
            }
            if (d >= incidentDirection) {
                closestDir2 = d;
                break;
            }
        }

        // 对四个角落点进行插值
        double f1 = closestFreq1;
        double f2 = closestFreq2;
        double d1 = closestDir1;
        double d2 = closestDir2;

        double rcs11 = getClosestRCS(f1, d1, theta, phi);
        double rcs12 = getClosestRCS(f1, d2, theta, phi);
        double rcs21 = getClosestRCS(f2, d1, theta, phi);
        double rcs22 = getClosestRCS(f2, d2, theta, phi);

        // 二维线性插值
        double rcsFreq1 = interpolate(rcs11, rcs12, d1, d2, incidentDirection);
        double rcsFreq2 = interpolate(rcs21, rcs22, d1, d2, incidentDirection);
        double finalRCS = interpolate(rcsFreq1, rcsFreq2, f1, f2, frequency);

        return finalRCS;
    }

    private double getClosestRCS(double frequency, double incidentDirection, double theta, double phi) {
        // 找到最接近的theta和phi的数据点
        RCSData closestData = null;
        double minDistance = Double.MAX_VALUE;

        for (RCSData data : rcsDataList) {
            if (Math.abs(data.getFrequency() - frequency) < 0.1 && Math.abs(data.getIncidentDirection() - incidentDirection) < 0.1) {
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
}