package com.springmfg.ims.common.numbering;

import java.io.Serializable;
import java.util.Objects;

public class NumberSequenceId implements Serializable {
    private String prefix;
    private int seqYear;

    public NumberSequenceId() {}
    public NumberSequenceId(String prefix, int seqYear) {
        this.prefix = prefix;
        this.seqYear = seqYear;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NumberSequenceId that)) return false;
        return seqYear == that.seqYear && Objects.equals(prefix, that.prefix);
    }
    @Override public int hashCode() { return Objects.hash(prefix, seqYear); }
}
