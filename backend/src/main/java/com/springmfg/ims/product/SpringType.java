package com.springmfg.ims.product;

/** The seven spring types (DESIGN.md section 1.2). {@code CUSTOM} has no fixed attributes (free-form). */
public enum SpringType {

    COMPRESSION("Compression"),
    EXTENSION("Extension / tension"),
    TORSION("Torsion"),
    CONICAL("Conical"),
    DISC_BELLEVILLE("Disc / Belleville"),
    WIRE_FORM("Wire form"),
    CUSTOM("Custom");

    private final String label;

    SpringType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
