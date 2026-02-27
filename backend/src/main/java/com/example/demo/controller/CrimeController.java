package com.example.demo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.demo.model.StreetData;
import com.example.demo.service.CrimeDataService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CrimeController {

    @Autowired
    private CrimeDataService crimeDataService;

    @GetMapping("/streets")
    public ResponseEntity<List<StreetData>> getDynamicStreetData() {
        try {
            List<StreetData> result = crimeDataService.calculateStreetScores(
                    crimeDataService.loadCrimeData()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }
}