package my.chat.chatapp.chat;

import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.r2dbc.core.DatabaseClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@SpringBootTest
class DbDiagTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    ConnectionFactory connectionFactory;

    @Test
    void compareJdbcAndR2dbcViews() throws Exception {
        System.out.println("DIAG SpringLiquibase bean: " + context.getBeansOfType(liquibase.integration.spring.SpringLiquibase.class).keySet());
        System.out.println("DIAG DataSource beans: " + context.getBeansOfType(DataSource.class).keySet());
        DataSource dataSource = context.getBean(DataSource.class);
        System.out.println("DIAG JDBC url: " + dataSource.getConnection().getMetaData().getURL());
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT table_name FROM information_schema.tables ORDER BY table_name");
            while (rs.next()) {
                System.out.println("DIAG JDBC table: " + rs.getString(1));
            }
        }
        DatabaseClient client = DatabaseClient.create(connectionFactory);
        client.sql("SELECT table_name FROM information_schema.tables ORDER BY table_name")
                .map(row -> row.get(0, String.class))
                .all()
                .doOnNext(name -> System.out.println("DIAG R2DBC table: " + name))
                .blockLast();
        System.out.println("DIAG done");
    }
}
