package com.healthdocs.backend.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "case_reference_counter")
public class CaseReferenceCounterEntity {
    @Id
    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    protected CaseReferenceCounterEntity() { }

    public CaseReferenceCounterEntity(LocalDate referenceDate, long currentValue) {
        this.referenceDate = referenceDate;
        this.currentValue = currentValue;
    }

    public long next() { return ++currentValue; }
    public long getCurrentValue() { return currentValue; }
}
