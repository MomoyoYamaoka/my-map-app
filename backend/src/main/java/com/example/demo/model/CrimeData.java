package com.example.demo.model;

public class CrimeData {
    private double latitude;
    private double longitude;
    private double crimeScore;

    // --- Getter と Setter ---

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

    public double getCrimeScore() {
        return crimeScore;
    }

    public void setCrimeScore(double crimeScore) {
        this.crimeScore = crimeScore;
    }
}
