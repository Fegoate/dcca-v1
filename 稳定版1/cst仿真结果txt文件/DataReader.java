import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataReader {
    private static final String DATA_DIRECTORY = "cst仿真结果txt文件";

    private static class IncidentAngles {
        final double elevation;
        final double azimuth;

        IncidentAngles(double elevation, double azimuth) {
            this.elevation = elevation;
            this.azimuth = azimuth;
        }
    }

    public List<RCSData> readAllData() {
        List<RCSData> allData = new ArrayList<>();

        // 遍历方向文件夹（方向1到方向8）
        for (int direction = 1; direction <= 8; direction++) {
            String directionPath = DATA_DIRECTORY + File.separator + "方向" + direction;
            File directionDir = new File(directionPath);

            if (directionDir.exists() && directionDir.isDirectory()) {
                IncidentAngles incidentAngles = resolveIncidentAngles(direction);

                System.out.println("检查文件夹: 方向" + direction);
                File[] files = directionDir.listFiles((dir, name) -> name.endsWith(".txt"));

                if (files != null) {
                    System.out.println("文件夹 方向" + direction + " 包含 " + files.length + " 个TXT文件");

                    for (File file : files) {
                        // 从文件名中提取频率
                        String fileName = file.getName();
                        String freqStr = fileName.replace(".txt", "");
                        // 处理"方向1 10"这样的文件名格式，提取数字部分
                        freqStr = freqStr.replaceAll("[^0-9. ]", "").trim();
                        freqStr = freqStr.split(" ")[1]; // 取第二个部分作为频率
                        double frequency = Double.parseDouble(freqStr);

                        // 读取文件内容
                        List<RCSData> fileData = readFile(file, frequency, incidentAngles);
                        allData.addAll(fileData);
                    }
                }
            }
        }

        System.out.println("总共读取 " + allData.size() + " 个数据点");
        return allData;
    }

    private List<RCSData> readFile(File file, double frequency, IncidentAngles incidentAngles) {
        List<RCSData> dataList = new ArrayList<>();

        // 入射俯仰角在当前数据集中恒为0°，但仍作为独立参数存储，方便未来扩展
        double incidentElevation = 0.0;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int lineCount = 0;

            while ((line = br.readLine()) != null) {
                lineCount++;
                line = line.trim();

                if (line.isEmpty() || line.startsWith("Theta")) {
                    // 跳过空行和表头
                    if (lineCount == 1 && line.startsWith("Theta")) {
                        System.out.println("行1解析失败: " + line);
                    }
                    continue;
                }

                try {
                    // 按空格分割数据
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        double theta = Double.parseDouble(parts[0]);
                        double phi = Double.parseDouble(parts[1]);
                        double rcsValue = Double.parseDouble(parts[2]);

                        RCSData data = new RCSData(
                                frequency,
                                incidentAngles.elevation,
                                incidentAngles.azimuth,
                                AngleUtils.normalize360(theta),
                                AngleUtils.normalize360(phi),
                                rcsValue
                        );
                        dataList.add(data);
                    }
                } catch (NumberFormatException e) {
                    // 跳过格式错误的行
                    continue;
                }
            }

            System.out.println("文件 " + file.getName() + " 包含 " + dataList.size() + " 个数据点");
        } catch (IOException e) {
            System.err.println("读取文件 " + file.getName() + " 失败: " + e.getMessage());
        }

        return dataList;
    }

    private IncidentAngles resolveIncidentAngles(int direction) {
        // 已知方向1的传播方向为 (0, -1, 0)，电场方向为 (0, 0, 1)
        if (direction == 1) {
            double x = 0.0;
            double y = -1.0;
            double z = 0.0;

            double r = Math.sqrt(x * x + y * y + z * z);
            double elevation = Math.toDegrees(Math.acos(z / r));
            double azimuth = Math.toDegrees(Math.atan2(y, x));

            return new IncidentAngles(
                    AngleUtils.normalize360(elevation),
                    AngleUtils.normalize360(azimuth)
            );
        }

        // 其他方向暂无明确的传播矢量信息，沿用旧逻辑的占位角度
        return new IncidentAngles(0.0, AngleUtils.normalize360(direction));
    }
}
