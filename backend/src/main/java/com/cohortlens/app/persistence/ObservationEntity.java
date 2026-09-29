package com.cohortlens.app.persistence;

import com.cohortlens.core.EconomicStatus;
import com.cohortlens.core.Observation;
import com.cohortlens.core.Term;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "observations")
public class ObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "observation_seq")
    @SequenceGenerator(name = "observation_seq", sequenceName = "observation_seq", allocationSize = 50)
    private Long id;

    @Column(name = "import_job_id", nullable = false)
    private Long importJobId;

    @Column(name = "student_key", nullable = false)
    private String studentKey;

    @Column(nullable = false)
    private String school;

    @Column(nullable = false)
    private int grade;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Term term;

    @Column(name = "term_index", nullable = false)
    private int termIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "economic_status", nullable = false)
    private EconomicStatus economicStatus;

    @Column(name = "english_learner", nullable = false)
    private boolean englishLearner;

    @Column(name = "math_score")
    private Double mathScore;

    @Column(name = "reading_score")
    private Double readingScore;

    @Column(name = "attendance_rate")
    private Double attendanceRate;

    @Column(name = "discipline_incidents", nullable = false)
    private int disciplineIncidents;

    protected ObservationEntity() {
        // required by JPA
    }

    public ObservationEntity(Observation o, Long importJobId) {
        this.importJobId = importJobId;
        this.studentKey = o.studentKey();
        this.school = o.school();
        this.grade = o.grade();
        this.academicYear = o.academicYear();
        this.term = o.term();
        this.termIndex = o.termIndex();
        this.economicStatus = o.economicStatus();
        this.englishLearner = o.englishLearner();
        this.mathScore = o.mathScore();
        this.readingScore = o.readingScore();
        this.attendanceRate = o.attendanceRate();
        this.disciplineIncidents = o.disciplineIncidents();
    }

    public Observation toRecord() {
        return new Observation(studentKey, school, grade, academicYear, term, economicStatus, englishLearner,
                mathScore, readingScore, attendanceRate, disciplineIncidents);
    }
}
