package com.courseqa.db;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
@Configuration
public class DatabaseConfig {
    @Bean
    public DataSource dataSource(@Value("${app.home}") String appHome,@Value("${app.data-dir}") String dataDir) throws Exception {
        Path root=Path.of(appHome).toAbsolutePath().normalize();
        Path data=root.resolve(dataDir).normalize();Files.createDirectories(data);
        SQLiteConfig sqlite=new SQLiteConfig();sqlite.enforceForeignKeys(true);sqlite.setBusyTimeout(5000);
        HikariConfig config=new HikariConfig();config.setJdbcUrl("jdbc:sqlite:"+data.resolve("courseqa.db"));
        config.setDataSourceProperties(sqlite.toProperties());config.setMaximumPoolSize(4);
        HikariDataSource source=new HikariDataSource(config);
        try(var conn=source.getConnection();var statement=conn.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            int version;try(var result=statement.executeQuery("PRAGMA user_version")){result.next();version=result.getInt(1);}
            if(version>1)throw new IllegalStateException("数据库版本超出当前启动包支持范围");
            if(version==0) {
                try(var result=statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                    result.next();if(result.getInt(1)>0)throw new IllegalStateException("未知数据库结构，拒绝覆盖");
                }
                conn.setAutoCommit(false);
                try {ScriptUtils.executeSqlScript(conn,new ClassPathResource("db/migration/V001__init.sql"));statement.execute("PRAGMA user_version=1");conn.commit();}
                catch(Exception ex){conn.rollback();throw ex;}
                finally{conn.setAutoCommit(true);}
            }
        } catch(Exception ex){source.close();throw ex;}
        return source;
    }
}
