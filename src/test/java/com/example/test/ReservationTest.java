package com.example.test;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ReservationConflictException;
import com.example.service.ReservationService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


public class ReservationTest {

    private static EntityManagerFactory emf;
    private EntityManager em;
    private ReservationService service;

    private Utilisateur user;
    private Salle salle;
    private LocalDateTime debut;

    @BeforeAll
    public static void init() {
        Map<String, Object> props = new HashMap<>();
        props.put("javax.persistence.jdbc.driver", "org.h2.Driver");
        props.put("javax.persistence.jdbc.url", "jdbc:h2:mem:reservation_test;DB_CLOSE_DELAY=-1");
        props.put("javax.persistence.jdbc.user", "sa");
        props.put("javax.persistence.jdbc.password", "");
        props.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        props.put("hibernate.hbm2ddl.auto", "create-drop");
        props.put("hibernate.show_sql", "false");
        emf = Persistence.createEntityManagerFactory("ReservationPU", props);
    }

    @BeforeEach
    public void setUp() {
        em = emf.createEntityManager();
        service = new ReservationService(em);

        em.getTransaction().begin();
        user = new Utilisateur("Test User", "test.user@example.com");
        salle = new Salle("Salle Test", 10);
        em.persist(user);
        em.persist(salle);
        em.getTransaction().commit();

        debut = LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);
    }

    @AfterEach
    public void tearDown() {
        // Nettoyage des données après chaque test
        if (em.getTransaction().isActive()) em.getTransaction().rollback();
        em.getTransaction().begin();
        em.createQuery("DELETE FROM Reservation").executeUpdate();
        em.createQuery("DELETE FROM Salle").executeUpdate();
        em.createQuery("DELETE FROM Utilisateur").executeUpdate();
        em.getTransaction().commit();
        emf.getCache().evictAll();
        em.close();
    }

    @AfterAll
    public static void close() {
        if (emf != null) emf.close();
    }

    @Test
    public void testCreateReservation() {
        Reservation r = service.createReservation(
                new Reservation(user, salle, debut, debut.plusHours(1), "Test"));
        assertNotNull(r.getId());
    }

    @Test
    public void testReservationEnConflitRefusee() {
        service.createReservation(new Reservation(user, salle, debut, debut.plusHours(2), "Première"));

        // 14h30-15h30 chevauche 14h-16h
        Reservation conflit = new Reservation(user, salle, debut.plusMinutes(30), debut.plusMinutes(90), "Conflit");
        assertThrows(ReservationConflictException.class, () -> service.createReservation(conflit));

        Long nb = em.createQuery("SELECT COUNT(r) FROM Reservation r", Long.class).getSingleResult();
        assertEquals(1L, nb);
    }

    @Test
    public void testCreneauxAdjacentsAcceptes() {
        service.createReservation(new Reservation(user, salle, debut, debut.plusHours(1), "A"));
        Reservation suivante = service.createReservation(
                new Reservation(user, salle, debut.plusHours(1), debut.plusHours(2), "B"));
        assertNotNull(suivante.getId());
    }

    @Test
    public void testDatesInvalides() {
        Reservation r = new Reservation(user, salle, debut, debut.minusHours(1), "Invalide");
        assertThrows(IllegalArgumentException.class, () -> service.createReservation(r));
    }

    @Test
    public void testModifierReservation() {
        Reservation r = service.createReservation(new Reservation(user, salle, debut, debut.plusHours(1), "Test"));
        int versionAvant = r.getVersion();

        service.modifierCreneau(r.getId(), debut.plusHours(3), debut.plusHours(4));

        em.clear();
        Reservation relue = em.find(Reservation.class, r.getId());
        assertEquals(debut.plusHours(3), relue.getDateDebut());
        assertEquals(versionAvant + 1, relue.getVersion());
    }

    @Test
    public void testSupprimerReservation() {
        Reservation r = service.createReservation(new Reservation(user, salle, debut, debut.plusHours(1), "Test"));
        service.supprimer(r.getId());
        em.clear();
        assertNull(em.find(Reservation.class, r.getId()));
    }

    @Test
    public void testVerrouillageOptimiste() {
        EntityManager em1 = emf.createEntityManager();
        EntityManager em2 = emf.createEntityManager();
        try {
            Salle s1 = em1.find(Salle.class, salle.getId());
            Salle s2 = em2.find(Salle.class, salle.getId());

            em1.getTransaction().begin();
            s1.setCapacite(30);
            em1.getTransaction().commit();

            em2.getTransaction().begin();
            s2.setCapacite(40);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> em2.getTransaction().commit());
            assertTrue(ex instanceof OptimisticLockException
                            || ex.getCause() instanceof OptimisticLockException,
                    "OptimisticLockException attendue, obtenu : " + ex);
        } finally {
            if (em2.getTransaction().isActive()) em2.getTransaction().rollback();
            em1.close();
            em2.close();
        }
    }

    @Test
    public void testCacheSecondNiveau() {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        EntityManager em1 = emf.createEntityManager();
        em1.find(Salle.class, salle.getId());
        em1.close();

        EntityManager em2 = emf.createEntityManager();
        em2.find(Salle.class, salle.getId());
        em2.close();

        assertTrue(emf.getCache().contains(Salle.class, salle.getId()));
        assertTrue(stats.getSecondLevelCacheHitCount() >= 1,
                "Au moins un hit attendu, obtenu : " + stats.getSecondLevelCacheHitCount());
    }

    @Test
    public void testJpqlEtCriteriaDonnentLeMemeResultat() {
        service.createReservation(new Reservation(user, salle, debut, debut.plusHours(1), "A"));
        service.createReservation(new Reservation(user, salle, debut.plusHours(2), debut.plusHours(3), "B"));

        List<Reservation> jpql = service.findFuturesParSalleJpql("Salle Test");
        List<Reservation> criteria = service.findFuturesParSalleCriteria("Salle Test");

        assertEquals(2, jpql.size());
        assertEquals(jpql.stream().map(Reservation::getId).toList(),
                criteria.stream().map(Reservation::getId).toList());
    }
}