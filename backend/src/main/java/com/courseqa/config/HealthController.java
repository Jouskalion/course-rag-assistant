package com.courseqa.config;
import com.courseqa.contracts.dto.Health;
import com.courseqa.providers.ModelHttpClient;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
@RestController
@RequestMapping("/api/v1")
public class HealthController {
    private final DataSource source;
    private final ModelHttpClient models;
    public HealthController(DataSource source, ModelHttpClient models) { this.source=source; this.models=models; }
    @GetMapping("/health")
    public Health getHealth() throws Exception {
        try (var connection=source.getConnection()) {
            return new Health("ok","0.1.0","skeleton",connection.isValid(1),models.configured());
        }
    }
}
