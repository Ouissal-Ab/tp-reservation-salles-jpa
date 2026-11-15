package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ReservationConflictException;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.MigrationExecutor;
import com.example.util.PerformanceReport;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class App {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());


    private static final String JDBC_URL =
            "jdbc:mysql://localhost:3306/reservation?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    public static void main(String[] args) {
        EntityManagerFactory emf = null;
        EntityManager em = null;
        try {

            emf = Persistence.createEntityManagerFactory("ReservationPU");


            MigrationExecutor migrationExecutor = new MigrationExecutor(JDBC_URL, DB_USER, DB_PASSWORD);
            migrationExecutor.executeMigration("db/migration/v2_0_add_description.sql");


            em = emf.createEntityManager();
            new DataInitializer(em).initData();


            ReservationService service = new ReservationService(em);

            System.out.println("\n--- Réservations futures de la Salle A (JPQL) ---");
            service.findFuturesParSalleJpql("Salle A").forEach(r ->
                    System.out.println(r + " par " + r.getUtilisateur().getNom()));

            System.out.println("\n--- Réservations futures de la Salle A (Criteria) ---");
            List<Reservation> viaCriteria = service.findFuturesParSalleCriteria("Salle A");
            viaCriteria.forEach(System.out::println);

            System.out.println("\n--- Salles de capacité >= 10 (Criteria dynamique) ---");
            service.rechercherSalles(10, null).forEach(System.out::println);


            Salle salleA = em.createQuery("SELECT s FROM Salle s WHERE s.nom = 'Salle A'", Salle.class)
                    .getSingleResult();
            Utilisateur bob = em.createQuery("SELECT u FROM Utilisateur u WHERE u.email = :e", Utilisateur.class)
                    .setParameter("e", "bob.martin@example.com").getSingleResult();
            LocalDateTime demain10h = LocalDateTime.now().plusDays(1)
                    .withHour(10).withMinute(0).withSecond(0).withNano(0);
            try {
                service.createReservation(new Reservation(bob, salleA, demain10h, demain10h.plusHours(1), "Conflit"));
            } catch (ReservationConflictException e) {
                System.out.println("\nConflit attendu : " + e.getMessage());
            }


            em.clear();
            em.find(Salle.class, salleA.getId());


            System.out.println();
            new PerformanceReport(em).printReport();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erreur fatale de l'application", e);
        } finally {
            if (em != null && em.isOpen()) em.close();
            if (emf != null && emf.isOpen()) emf.close();
        }
    }
}