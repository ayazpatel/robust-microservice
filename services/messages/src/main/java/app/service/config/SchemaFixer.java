// package app.service.config;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.boot.ApplicationArguments;
// import org.springframework.boot.ApplicationRunner;
// import org.springframework.jdbc.core.JdbcTemplate;
// import org.springframework.stereotype.Component;

// /**
//  * On startup, ensure messages.message_content is TEXT (not OID LOB).
//  * If the column is an OID from an earlier schema, convert it in-place to TEXT
//  * so that queries and admin tools show the actual message body.
//  */
// @Component
// public class SchemaFixer implements ApplicationRunner {
//     private static final Logger log = LoggerFactory.getLogger(SchemaFixer.class);

//     private final JdbcTemplate jdbc;

//     public SchemaFixer(JdbcTemplate jdbc) {
//         this.jdbc = jdbc;
//     }

//     @Override
//     public void run(ApplicationArguments args) {
//         try {
//             String udt = jdbc.query(
//                     "SELECT udt_name FROM information_schema.columns WHERE table_name = 'messages' AND column_name = 'message_content'",
//                     rs -> rs.next() ? rs.getString(1) : null
//             );
//             if (udt == null) return; // table not present yet

//             if ("oid".equalsIgnoreCase(udt)) {
//                 log.warn("Migrating messages.message_content from OID to TEXT (one-time)");
//                 // Enable LO helper functions, then convert in place
//                 try { jdbc.execute("CREATE EXTENSION IF NOT EXISTS lo"); } catch (Exception ignored) {}
//                 jdbc.execute("ALTER TABLE messages ALTER COLUMN message_content TYPE text USING convert_from(lo_get(message_content), 'UTF8')");
//                 log.info("message_content migrated to TEXT");
//             }
//         } catch (Exception e) {
//             log.error("SchemaFixer failed: {}", e.getMessage());
//         }
//     }
// }

