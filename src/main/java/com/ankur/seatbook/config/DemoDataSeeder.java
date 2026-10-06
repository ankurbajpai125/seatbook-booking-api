package com.ankur.seatbook.config;

import com.ankur.seatbook.domain.Movie;
import com.ankur.seatbook.domain.Theatre;
import com.ankur.seatbook.repo.MovieRepository;
import com.ankur.seatbook.service.AdminService;
import com.ankur.seatbook.web.Dtos.MovieRequest;
import com.ankur.seatbook.web.Dtos.ShowRequest;
import com.ankur.seatbook.web.Dtos.TheatreRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fills an empty database with sample movies, theatres and shows so the app is usable right away.
 * Runs only when there are no movies yet. Turn it off with SEED_DEMO_DATA=false (do this in production).
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AdminService admin;
    private final MovieRepository movies;

    public DemoDataSeeder(AdminService admin, MovieRepository movies) {
        this.admin = admin;
        this.movies = movies;
    }

    @Override
    public void run(String... args) {
        if (movies.count() > 0) {
            return;
        }

        List<Movie> created = List.of(
                admin.createMovie(new MovieRequest("The Last Lighthouse", "English", 128,
                        "A keeper on a remote coast discovers the light has been guiding something other than ships.")),
                admin.createMovie(new MovieRequest("Monsoon Express", "Hindi", 141,
                        "Six strangers share one delayed night train across a flooded country.")),
                admin.createMovie(new MovieRequest("Orbit Nine", "English", 117,
                        "A small crew on a failing station has nine orbits to fix the one thing that can bring them home.")),
                admin.createMovie(new MovieRequest("Paper Cities", "English", 104,
                        "An architect rebuilds her childhood town from memory, one cardboard street at a time.")),
                admin.createMovie(new MovieRequest("Night Market", "Hindi", 133,
                        "A food stall, a missing recipe, and one very long night in a city that never closes.")));

        Theatre small = admin.createTheatre(new TheatreRequest("PVR Noida", "Noida", 6, 10));
        Theatre large = admin.createTheatre(new TheatreRequest("Cinepolis Sector 18", "Noida", 8, 12));

        LocalDate today = LocalDate.now();
        int shows = 0;
        for (int m = 0; m < created.size(); m++) {
            for (int day = 1; day <= 3; day++) {
                for (int slot = 0; slot < 2; slot++) {
                    LocalDateTime start = today.plusDays(day).atTime(slot == 0 ? 18 : 21, 30);
                    Theatre theatre = (m + day + slot) % 2 == 0 ? small : large;
                    BigDecimal price = BigDecimal.valueOf(180 + 40L * ((m + slot) % 4));
                    admin.createShow(new ShowRequest(created.get(m).getId(), theatre.getId(), start, price));
                    shows++;
                }
            }
        }
        log.info("Seeded demo data: {} movies, 2 theatres, {} shows over the next 3 days", created.size(), shows);
    }
}
