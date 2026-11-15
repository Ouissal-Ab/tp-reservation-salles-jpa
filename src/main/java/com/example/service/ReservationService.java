package com.example.service;

import com.example.model.Reservation;
import com.example.model.Salle;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.OptimisticLockException;
import javax.persistence.RollbackException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;


public class ReservationService {

    private static final Logger LOGGER = Logger.getLogger(ReservationService.class.getName());

    private final EntityManager em;

    public ReservationService(EntityManager em) {
        this.em = em;
    }


    public Reservation createReservation(Reservation reservation) {
        if (!reservation.getDateFin().isAfter(reservation.getDateDebut())) {
            throw new IllegalArgumentException("La date de fin doit être après la date de début.");
        }
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (existeConflit(reservation.getSalle(), reservation.getDateDebut(), reservation.getDateFin(), null)) {
                throw new ReservationConflictException("La salle " + reservation.getSalle().getNom()
                        + " est déjà réservée sur ce créneau.");
            }
            em.persist(reservation);
            tx.commit();
            LOGGER.info("Création de la réservation réussie pour utilisateur : "
                    + reservation.getUtilisateur().getEmail());
            return reservation;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            LOGGER.severe("Erreur lors de la création de la réservation : " + e.getMessage());
            throw e;
        }
    }


    public Reservation modifierCreneau(Long reservationId, LocalDateTime debut, LocalDateTime fin) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Reservation r = em.find(Reservation.class, reservationId);
            if (r == null) throw new IllegalArgumentException("Réservation introuvable : " + reservationId);
            if (existeConflit(r.getSalle(), debut, fin, r.getId())) {
                throw new ReservationConflictException("Nouveau créneau en conflit.");
            }
            r.setDateDebut(debut);
            r.setDateFin(fin);
            tx.commit();
            LOGGER.info("Réservation " + reservationId + " modifiée.");
            return r;
        } catch (OptimisticLockException | RollbackException e) {
            if (tx.isActive()) tx.rollback();
            LOGGER.warning("Conflit détecté, veuillez réessayer : " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            LOGGER.severe("Erreur lors de la modification : " + e.getMessage());
            throw e;
        }
    }


    public void supprimer(Long reservationId) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Reservation r = em.find(Reservation.class, reservationId);
            if (r != null) em.remove(r);
            tx.commit();
            LOGGER.info("Réservation " + reservationId + " supprimée.");
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            LOGGER.severe("Erreur lors de la suppression : " + e.getMessage());
            throw e;
        }
    }



    public boolean existeConflit(Salle salle, LocalDateTime debut, LocalDateTime fin, Long idExclu) {
        String jpql = "SELECT COUNT(r) FROM Reservation r WHERE r.salle = :salle "
                + "AND r.dateDebut < :fin AND r.dateFin > :debut";
        if (idExclu != null) jpql += " AND r.id <> :idExclu";

        TypedQuery<Long> q = em.createQuery(jpql, Long.class)
                .setParameter("salle", salle)
                .setParameter("debut", debut)
                .setParameter("fin", fin);
        if (idExclu != null) q.setParameter("idExclu", idExclu);
        return q.getSingleResult() > 0;
    }


    public List<Reservation> findFuturesParSalleJpql(String nomSalle) {
        String jpql = "SELECT r FROM Reservation r JOIN FETCH r.utilisateur "
                + "WHERE r.salle.nom = :nomSalle AND r.dateDebut > :date ORDER BY r.dateDebut";
        return em.createQuery(jpql, Reservation.class)
                .setParameter("nomSalle", nomSalle)
                .setParameter("date", LocalDateTime.now())
                .getResultList();
    }


    public List<Reservation> findFuturesParSalleCriteria(String nomSalle) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Reservation> cq = cb.createQuery(Reservation.class);
        Root<Reservation> root = cq.from(Reservation.class);
        Join<Reservation, Salle> salle = root.join("salle");

        Predicate predicate = cb.and(
                cb.equal(salle.get("nom"), nomSalle),
                cb.greaterThan(root.get("dateDebut"), LocalDateTime.now())
        );
        cq.select(root).where(predicate).orderBy(cb.asc(root.get("dateDebut")));
        return em.createQuery(cq).getResultList();
    }


    public List<Salle> rechercherSalles(Integer capaciteMin, String nomContient) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Salle> cq = cb.createQuery(Salle.class);
        Root<Salle> s = cq.from(Salle.class);
        Predicate p = cb.conjunction();
        if (capaciteMin != null) p = cb.and(p, cb.ge(s.get("capacite"), capaciteMin));
        if (nomContient != null) p = cb.and(p, cb.like(cb.lower(s.get("nom")), "%" + nomContient.toLowerCase() + "%"));
        cq.select(s).where(p).orderBy(cb.asc(s.get("nom")));
        return em.createQuery(cq).setHint("org.hibernate.cacheable", true).getResultList();
    }
}