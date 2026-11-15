package com.example.util;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.time.LocalDateTime;
import java.util.logging.Logger;

public class DataInitializer {

    private static final Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());

    private final EntityManager em;

    public DataInitializer(EntityManager em) {
        this.em = em;
    }

    public void initData() {
        // Évite les doublons (email/nom uniques) si on relance l'application
        Long nb = em.createQuery("SELECT COUNT(u) FROM Utilisateur u", Long.class).getSingleResult();
        if (nb > 0) {
            LOGGER.info("Données déjà présentes (" + nb + " utilisateurs) : initialisation ignorée.");
            return;
        }

        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();

            Utilisateur alice = new Utilisateur("Alice Dupont", "alice.dupont@example.com");
            Utilisateur bob = new Utilisateur("Bob Martin", "bob.martin@example.com");
            Utilisateur chloe = new Utilisateur("Chloé Bernard", "chloe.bernard@example.com");
            Utilisateur david = new Utilisateur("David Leroy", "david.leroy@example.com");
            em.persist(alice);
            em.persist(bob);
            em.persist(chloe);
            em.persist(david);

            Salle salleA = new Salle("Salle A", 20);
            Salle salleB = new Salle("Salle B", 8);
            Salle amphi = new Salle("Amphithéâtre", 150);
            em.persist(salleA);
            em.persist(salleB);
            em.persist(amphi);

            // Dates dynamiques : toujours dans le futur, à heure fixe
            LocalDateTime demain9h = LocalDateTime.now().plusDays(1)
                    .withHour(9).withMinute(0).withSecond(0).withNano(0);

            em.persist(new Reservation(alice, salleA, demain9h, demain9h.plusHours(2), "Réunion projet"));
            em.persist(new Reservation(bob, salleA, demain9h.plusHours(3), demain9h.plusHours(4), "Entretien"));
            em.persist(new Reservation(chloe, salleB, demain9h, demain9h.plusHours(1), "Point d'équipe"));
            em.persist(new Reservation(david, amphi, demain9h.plusDays(1), demain9h.plusDays(1).plusHours(3), "Conférence"));
            em.persist(new Reservation(alice, salleB, demain9h.plusDays(2), demain9h.plusDays(2).plusHours(2), "Formation"));

            tx.commit();
            LOGGER.info("Jeu de données inséré : 4 utilisateurs, 3 salles, 5 réservations.");
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            LOGGER.severe("Échec de l'initialisation des données : " + e.getMessage());
            throw e;
        }
    }
}