package com.mfg.ims.doccore.transporter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveTransporterRequest(
        @Size(max = 20) String transporterCode,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 15) String gstTransporterId,
        @Size(max = 100) String contact) {
}
