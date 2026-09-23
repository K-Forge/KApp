package co.edu.konradlorenz.kapp.map.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Transactions for the one write that needs them: saving a floor's layout, which touches the
 * building's floor and every space on it at once.
 *
 * <p>Spring Boot does not register a MongoDB transaction manager on its own, so without this bean
 * {@code @Transactional} would do nothing at all - and a layout save cut short halfway would leave
 * a floor with half its rooms moved. Atlas and the Testcontainers MongoDB are both replica sets,
 * which is what multi-document transactions require.
 *
 * <p>Mongock keeps {@code transactional: false} in {@code application.yml}; it does not pick this
 * bean up.
 */
@Configuration
public class MapTransactionConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
