package com.example.demo.service;

import com.example.demo.model.CrimeData;
import com.example.demo.model.StreetData;
import com.opencsv.CSVReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStreamReader;
import java.util.*;

@Service
public class CrimeDataService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<CrimeData> loadCrimeData() {
        List<CrimeData> list = new ArrayList<>();

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

    public List<StreetData> calculateStreetScores(List<CrimeData> crimes) {
        final double centerLat = 47.6062;   // Seattle
        final double centerLon = -122.3321;
        final double radiusDeg = 0.01;
        final double threshold = 200;

        String query = String.format(
                "[out:json][timeout:25];(" +
                        "way[\"highway\"](%f,%f,%f,%f);>;" +
                        ");out body;",
                centerLat - radiusDeg, centerLon - radiusDeg,
                centerLat + radiusDeg, centerLon + radiusDeg);

        Map<String, Object> body;
        try {
            body = restTemplate.postForObject(
                "https://overpass-api.de/api/interpreter",
                query, Map.class);
        } catch (Exception e) {
            e.printStackTrace();
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
                street.setColor(colorFor(avg));
                street.setCrimePoints(near);

                result.add(street);
            });

        } catch (Exception e) {
            e.printStackTrace();
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

    private String colorFor(double s) {
        if (s >= 0.8) return "#ff0000";
        if (s >= 0.6) return "#ffa500";
        if (s >= 0.4) return "#ffff00";
        if (s >= 0.2) return "#90ee90";
        return "#00ff00";
    }
}
