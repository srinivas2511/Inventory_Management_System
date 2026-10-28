package com.mfg.ims.doccore.transporter;

import com.mfg.ims.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "transporters", schema = "ims")
public class Transporter extends BaseEntity {

    @Column(name = "transporter_code", length = 20)
    private String transporterCode;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "gst_transporter_id", length = 15)
    private String gstTransporterId;

    @Column(name = "contact", length = 100)
    private String contact;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Transporter() {}

    public String getTransporterCode() { return transporterCode; }
    public void setTransporterCode(String v) { this.transporterCode = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getGstTransporterId() { return gstTransporterId; }
    public void setGstTransporterId(String v) { this.gstTransporterId = v; }
    public String getContact() { return contact; }
    public void setContact(String v) { this.contact = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
