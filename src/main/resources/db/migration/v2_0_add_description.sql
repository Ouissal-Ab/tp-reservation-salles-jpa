
ALTER TABLE salles ADD COLUMN description VARCHAR(255);

CREATE UNIQUE INDEX idx_utilisateur_email ON utilisateurs(email);

CREATE INDEX idx_reservation_salle_dates ON reservations(salle_id, date_debut, date_fin);