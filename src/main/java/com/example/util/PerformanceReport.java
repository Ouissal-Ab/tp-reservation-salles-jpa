package com.example.util;

import org.hibernate.Session;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;

public class PerformanceReport {

    private final EntityManager em;

    public PerformanceReport(EntityManager em) {
        this.em = em;
    }

    public void printReport() {
        Session session = em.unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();

        System.out.println("=== RAPPORT DE PERFORMANCE ===");
        System.out.println("Requêtes exécutées : " + stats.getQueryExecutionCount());
        System.out.println("Requête la plus lente (ms) : " + stats.getQueryExecutionMaxTime());
        System.out.println("Requête la plus lente : " + stats.getQueryExecutionMaxTimeQueryString());
        System.out.println("Entités chargées : " + stats.getEntityLoadCount());
        System.out.println("Entités insérées : " + stats.getEntityInsertCount());
        System.out.println("Cache de second niveau hits : " + stats.getSecondLevelCacheHitCount());
        System.out.println("Cache de second niveau misses : " + stats.getSecondLevelCacheMissCount());
        System.out.println("Cache de second niveau mises à jour : " + stats.getSecondLevelCachePutCount());
        System.out.println("Cache de requêtes hits : " + stats.getQueryCacheHitCount());
        System.out.println("Transactions : " + stats.getTransactionCount());
        System.out.println("Conflits optimistes : " + stats.getOptimisticFailureCount());
        System.out.println("===============================");
    }
}