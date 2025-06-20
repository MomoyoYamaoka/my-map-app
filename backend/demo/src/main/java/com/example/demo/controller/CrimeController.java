package com.example.demo.controller;

import com.example.demo.model.CrimeData;
import com.example.demo.model.StreetData;
import com.example.demo.service.CrimeDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = {"http://localhost:3000","https://my-map-app.vercel.app"})
@RestController
@RequestMapping("/api")
public class CrimeController {

    private final CrimeDataService service;

    public CrimeController(CrimeDataService service) {
        this.service = service;
    }

    @GetMapping("/streets")
    public List<StreetData> getStreetScores() {
        List<CrimeData> crimes = service.loadCrimeData();
        return service.calculateStreetScores(crimes);
    }
}
