package com.courseqa.config;
import com.courseqa.contracts.dto.Health;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import javax.sql.DataSource;
@RestController
@RequestMapping("/api/v1")
public class HealthController {
    private final DataSource source;private final boolean configured;
    public HealthController(DataSource source,@Value("${app.model.base-url}")String url,@Value("${app.model.api-key}")String key){this.source=source;this.configured=!url.isBlank()&&!key.isBlank();}
    @GetMapping("/health")
    public Health getHealth()throws Exception{try(var connection=source.getConnection()){return new Health("ok","0.1.0","skeleton",connection.isValid(1),configured);}}
}
