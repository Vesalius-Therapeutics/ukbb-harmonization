
-- ----------------------------------------
-- serq_family_history_disease
-- ----------------------------------------
DROP TABLE IF EXISTS serq_family_history_dx;
DROP TABLE IF EXISTS serq_family_history_disease;
CREATE TABLE serq_family_history_disease (
    disease_id INTEGER NOT NULL,
    disease_name VARCHAR(50),
    UNIQUE(disease_id)
);

INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 1, 'Heart disease' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 2, 'Stroke' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 3, 'Lung cancer' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 4, 'Bowel cancer' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 5, 'Breast cancer' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 6, 'Chronic bronchitis/emphysema' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 8, 'High blood pressure' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 9, 'Diabetes' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 10, 'Alzheimer''s disease/dementia' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 11, 'Parkinson''s disease' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 12, 'Severe depression' );
INSERT INTO serq_family_history_disease ( disease_id, disease_name ) VALUES ( 13, 'Prostate cancer' );

-- ----------------------------------------
-- serq_family_history_disease
-- ----------------------------------------

CREATE TABLE serq_family_history_dx (
    eid INTEGER NOT NULL,
    parent CHAR(2), -- mother or father (M|F)
    disease_id INTEGER NOT NULL
);


--
-- Link eid as foreign key to root category
--

ALTER TABLE serq_family_history_dx
    ADD CONSTRAINT serq_fam_disease_hx_f_k1
    FOREIGN KEY (eid)
    REFERENCES UKBB_PATIENT (eid);


ALTER TABLE serq_family_history_dx
    ADD CONSTRAINT serq_fam_disease_hx_f_k2
    FOREIGN KEY (disease_id)
    REFERENCES serq_family_history_disease (disease_id);


