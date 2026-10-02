package com.springmfg.ims.common.numbering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "number_sequences", schema = "ims")
@IdClass(NumberSequenceId.class)
public class NumberSequence {

    @Id
    @Column(length = 20)
    private String prefix;

    @Id
    @Column(name = "seq_year")
    private int seqYear;

    @Column(name = "last_value", nullable = false)
    private long lastValue = 0;

    protected NumberSequence() {}

    public NumberSequence(String prefix, int seqYear) {
        this.prefix = prefix;
        this.seqYear = seqYear;
    }

    public String getPrefix() { return prefix; }
    public int getSeqYear() { return seqYear; }
    public long getLastValue() { return lastValue; }
    public void setLastValue(long lastValue) { this.lastValue = lastValue; }
}
