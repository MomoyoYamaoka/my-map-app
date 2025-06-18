package com.example.demo.model;

import java.util.List;

public class StreetData {
    private String streetId;
    private String streetName;
    private List<Point> coordinates;
    private double averageCrimeScore;
    private String color;
    private List<CrimeData> crimePoints;

    // Getter & Setter
    public String getStreetId() {
        return streetId;
    }

    public void setStreetId(String streetId) {
        this.streetId = streetId;
    }

    public String getStreetName() {
        return streetName;
    }

    public void setStreetName(String streetName) {
        this.streetName = streetName;
    }

    public List<Point> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<Point> coordinates) {
        this.coordinates = coordinates;
    }

    public double getAverageCrimeScore() {
        return averageCrimeScore;
    }

    public void setAverageCrimeScore(double averageCrimeScore) {
        this.averageCrimeScore = averageCrimeScore;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public List<CrimeData> getCrimePoints() {
        return crimePoints;
    }

    public void setCrimePoints(List<CrimeData> crimePoints) {
        this.crimePoints = crimePoints;
    }

    // 内部クラス: Point（緯度・経度）
    public static class Point {
        private double latitude;
        private double longitude;

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }
    }
}
