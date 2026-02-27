package com.example.demo.service;

import com.example.demo.model.CrimeData;
import com.example.demo.model.StreetData;
import com.opencsv.CSVReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

@Service
public class CrimeDataService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> overpassEndpoints = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.openstreetmap.ru/api/interpreter"
    );

    public List<CrimeData> loadCrimeData() {
        List<CrimeData> list = new ArrayList<>();

        // 1. まず Street View 解析結果があればそちらを優先して読む
        // 1-1. analyzer/yolo/outputs の seattle_analysis_*.csv（最新）を探す
        try {
            File dir = new File("../analyzer/yolo/outputs");
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) ->
                        name.startsWith("seattle_analysis_") && name.endsWith(".csv"));
                if (files != null && files.length > 0) {
                    File latest = Arrays.stream(files)
                            .max(Comparator.comparingLong(File::lastModified))
                            .orElse(null);
                    if (latest != null) {
                        try (Reader r = new FileReader(latest);
                             CSVReader reader = new CSVReader(r)) {
                            parseStreetViewCsv(reader, list);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("StreetView CSV 読込失敗 (filesystem): " + e.getMessage());
            e.printStackTrace();
        }
        if (!list.isEmpty()) {
            System.out.println("StreetView スコア件数 (filesystem): " + list.size());
            return list;
        }

        // 1-2. クラスパス上の StreetViewScore.csv
        ClassPathResource streetViewCsv = new ClassPathResource("data/StreetViewScore.csv");
        if (streetViewCsv.exists()) {
            try (CSVReader reader = new CSVReader(
                    new InputStreamReader(streetViewCsv.getInputStream()))) {
                parseStreetViewCsv(reader, list);
            } catch (Exception e) {
                System.err.println("StreetView CSV 読込失敗 (classpath): " + e.getMessage());
                e.printStackTrace();
            }
            if (!list.isEmpty()) {
                System.out.println("StreetView スコア件数 (classpath): " + list.size());
                return list;
            }
        }

        // 2. どちらもなければ従来の CrimeScore.csv を読む
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new ClassPathResource("data/CrimeScore.csv").getInputStream()))) {

            String[] row;
            reader.readNext(); // ヘッダーをスキップ
            while ((row = reader.readNext()) != null) {
                if (row.length < 3) continue;
                try {
                    CrimeData d = new CrimeData();
                    d.setLatitude(Double.parseDouble(row[0]));
                    d.setLongitude(Double.parseDouble(row[1]));
                    d.setCrimeScore(Double.parseDouble(row[2]));
                    list.add(d);
                } catch (NumberFormatException ignore) {
                }
            }
        } catch (Exception e) {
            System.err.println("CSV 読込失敗: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("読み込んだ件数: " + list.size());
        return list;
    }

    /**
     * Street View 解析CSVを読み込み、normalized_discomfort（なければ overall_discomfort）をスコアとして使う。
     */
    private void parseStreetViewCsv(CSVReader reader, List<CrimeData> out) throws Exception {
        String[] header = reader.readNext(); // ヘッダー
        if (header == null) {
            return;
        }

        int latIdx = -1, lonIdx = -1, scoreIdx = -1;
        for (int i = 0; i < header.length; i++) {
            switch (header[i]) {
                case "latitude" -> latIdx = i;
                case "longitude" -> lonIdx = i;
                case "normalized_discomfort" -> scoreIdx = i;
                case "overall_discomfort" -> {
                    // normalized が無ければ overall を代用
                    if (scoreIdx == -1) scoreIdx = i;
                }
                default -> {
                }
            }
        }

        if (latIdx < 0 || lonIdx < 0 || scoreIdx < 0) {
            return;
        }

        String[] row;
        while ((row = reader.readNext()) != null) {
            if (row.length <= Math.max(Math.max(latIdx, lonIdx), scoreIdx)) continue;
            try {
                CrimeData d = new CrimeData();
                d.setLatitude(Double.parseDouble(row[latIdx]));
                d.setLongitude(Double.parseDouble(row[lonIdx]));
                d.setCrimeScore(Double.parseDouble(row[scoreIdx]));
                out.add(d);
            } catch (NumberFormatException ignore) {
            }
        }
    }

    public List<StreetData> calculateStreetScores(List<CrimeData> crimes) {
        final double centerLat = 47.6062;   // Seattle
        final double centerLon = -122.3321;
        final double radiusDeg = 0.01;
        final double threshold = 200;

        String query = String.format(
                "[out:json][timeout:60];(" +
                        "way[\"highway\"](%f,%f,%f,%f);>;" +
                        ");out body;",
                centerLat - radiusDeg, centerLon - radiusDeg,
                centerLat + radiusDeg, centerLon + radiusDeg);

        Map<String, Object> body;
        Exception lastError = null;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("data", query);  // Overpass API が期待するキーは "data"

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        body = null;
        for (String endpoint : overpassEndpoints) {
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);
                body = response.getBody();
                if (body != null) break;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (body == null) {
            if (lastError != null) lastError.printStackTrace();
            return Collections.emptyList();
        }

        List<StreetData> result = new ArrayList<>();
        try {
            JsonNode root = mapper.convertValue(body, JsonNode.class);

            Map<String, double[]> nodes = new HashMap<>();
            root.path("elements").forEach(el -> {
                if ("node".equals(el.path("type").asText())) {
                    nodes.put(el.path("id").asText(),
                            new double[]{el.path("lat").asDouble(), el.path("lon").asDouble()});
                }
            });

            root.path("elements").forEach(el -> {
                if (!"way".equals(el.path("type").asText())) return;

                List<StreetData.Point> pts = new ArrayList<>();
                el.path("nodes").forEach(id -> {
                    double[] xy = nodes.get(id.asText());
                    if (xy != null) {
                        StreetData.Point p = new StreetData.Point();
                        p.setLatitude(xy[0]);
                        p.setLongitude(xy[1]);
                        pts.add(p);
                    }
                });

                if (pts.size() < 2) return;

                double sum = 0;
                int n = 0;
                List<CrimeData> near = new ArrayList<>();
                for (CrimeData c : crimes) {
                    if (distanceToStreet(c.getLatitude(), c.getLongitude(), pts) < threshold) {
                        sum += c.getCrimeScore();
                        n++;
                        near.add(c);
                    }
                }

                if (n == 0) return;

                double avg = sum / n;

                StreetData street = new StreetData();
                street.setStreetId(el.path("id").asText());
                street.setStreetName(el.path("tags").path("name").asText("未命名の道路"));
                street.setCoordinates(pts);
                street.setAverageCrimeScore(avg);
                street.setCrimePoints(near);

                result.add(street);
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
        // パーセンタイルに基づいて色分け（上位20%: 赤, 次の20%: オレンジ, ...）
        if (!result.isEmpty()) {
            List<Double> scores = new ArrayList<>();
            for (StreetData s : result) {
                scores.add(s.getAverageCrimeScore());
            }
            Collections.sort(scores); // 昇順
            int n = scores.size();
            if (n > 1) {
                double tRed = scores.get((int) Math.floor(0.8 * (n - 1)));   // 上位20%の境目
                double tOrange = scores.get((int) Math.floor(0.6 * (n - 1)));
                double tYellow = scores.get((int) Math.floor(0.4 * (n - 1)));
                double tLightGreen = scores.get((int) Math.floor(0.2 * (n - 1)));

                for (StreetData s : result) {
                    double v = s.getAverageCrimeScore();
                    if (v >= tRed) {
                        s.setColor("#ff0000");      // 赤
                    } else if (v >= tOrange) {
                        s.setColor("#ffa500");      // オレンジ
                    } else if (v >= tYellow) {
                        s.setColor("#ffff00");      // 黄色
                    } else if (v >= tLightGreen) {
                        s.setColor("#90ee90");      // 黄緑
                    } else {
                        s.setColor("#00ff00");      // 緑
                    }
                }
            } else {
                // 1件しかない場合は中間色にしておく
                result.get(0).setColor("#ffff00");
            }
        }
        return result;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double φ1 = Math.toRadians(lat1), φ2 = Math.toRadians(lat2);
        double dφ = Math.toRadians(lat2 - lat1), dλ = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dφ / 2) * Math.sin(dφ / 2) +
                Math.cos(φ1) * Math.cos(φ2) *
                        Math.sin(dλ / 2) * Math.sin(dλ / 2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double distanceToStreet(double lat, double lon, List<StreetData.Point> poly) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < poly.size() - 1; i++) {
            StreetData.Point a = poly.get(i), b = poly.get(i + 1);
            double dAB = haversine(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
            if (dAB == 0) continue;

            double t = ((lat - a.getLatitude()) * (b.getLatitude() - a.getLatitude()) +
                    (lon - a.getLongitude()) * (b.getLongitude() - a.getLongitude())) /
                    (Math.pow(b.getLatitude() - a.getLatitude(), 2) +
                            Math.pow(b.getLongitude() - a.getLongitude(), 2));

            double cand = (t < 0) ? haversine(lat, lon, a.getLatitude(), a.getLongitude())
                    : (t > 1) ? haversine(lat, lon, b.getLatitude(), b.getLongitude())
                    : haversine(lat, lon,
                    a.getLatitude() + t * (b.getLatitude() - a.getLatitude()),
                    a.getLongitude() + t * (b.getLongitude() - a.getLongitude()));
            best = Math.min(best, cand);
        }
        return best;
    }

    // 旧 colorFor は現在未使用だが、必要なら残しておく
    private String colorFor(double s) {
        if (s >= 0.8) return "#ff0000";
        if (s >= 0.6) return "#ffa500";
        if (s >= 0.4) return "#ffff00";
        if (s >= 0.2) return "#90ee90";
        return "#00ff00";
    }
}
