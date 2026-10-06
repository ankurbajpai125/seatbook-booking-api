package com.ankur.seatbook.web;

import com.ankur.seatbook.domain.Movie;
import com.ankur.seatbook.service.CatalogService;
import com.ankur.seatbook.web.Dtos.SeatView;
import com.ankur.seatbook.web.Dtos.ShowView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Public, read-only endpoints. */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private static final int MAX_PAGE_SIZE = 50;

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/movies")
    public Page<Movie> movies(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "12") int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return catalog.movies(PageRequest.of(Math.max(page, 0), safeSize, Sort.by("title")));
    }

    @GetMapping("/movies/{id}")
    public Movie movie(@PathVariable Long id) {
        return catalog.movie(id);
    }

    @GetMapping("/movies/{id}/shows")
    public List<ShowView> shows(@PathVariable Long id) {
        return catalog.upcomingShows(id);
    }

    @GetMapping("/shows/{id}")
    public ShowView show(@PathVariable Long id) {
        return catalog.show(id);
    }

    @GetMapping("/shows/{id}/seats")
    public List<SeatView> seats(@PathVariable Long id) {
        return catalog.seatMap(id);
    }
}
