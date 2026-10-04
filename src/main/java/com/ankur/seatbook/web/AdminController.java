package com.ankur.seatbook.web;

import com.ankur.seatbook.domain.Movie;
import com.ankur.seatbook.domain.Theatre;
import com.ankur.seatbook.service.AdminService;
import com.ankur.seatbook.web.Dtos.MovieRequest;
import com.ankur.seatbook.web.Dtos.ShowRequest;
import com.ankur.seatbook.web.Dtos.ShowView;
import com.ankur.seatbook.web.Dtos.TheatreRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** ADMIN role only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public Movie createMovie(@Valid @RequestBody MovieRequest request) {
        return admin.createMovie(request);
    }

    @PostMapping("/theatres")
    @ResponseStatus(HttpStatus.CREATED)
    public Theatre createTheatre(@Valid @RequestBody TheatreRequest request) {
        return admin.createTheatre(request);
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowView createShow(@Valid @RequestBody ShowRequest request) {
        return admin.createShow(request);
    }
}
