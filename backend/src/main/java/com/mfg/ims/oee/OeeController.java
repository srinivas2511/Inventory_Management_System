package com.mfg.ims.oee;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.oee.OeeDtos.DowntimeReasonDto;
import com.mfg.ims.oee.OeeDtos.OeeDto;
import com.mfg.ims.oee.OeeDtos.OeeLossesDto;
import com.mfg.ims.oee.OeeDtos.OeeTrendPointDto;

/** OEE read endpoints. Without dates the range is the last 30 days up to today. */
@RestController
@RequestMapping("/api/oee")
public class OeeController {

    private final OeeService service;

    public OeeController(OeeService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('OEE_VIEW')")
    public OeeDto summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(from, to);
    }

    @GetMapping("/machines")
    @PreAuthorize("hasAuthority('OEE_VIEW')")
    public List<OeeDto> machines(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calculateAll(from, to);
    }

    @GetMapping("/machines/{machineId}")
    @PreAuthorize("hasAuthority('OEE_VIEW')")
    public OeeDto machine(@PathVariable Long machineId,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.calculateForMachine(machineId, from, to);
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('OEE_VIEW')")
    public List<OeeTrendPointDto> trend(
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.trend(machineId, from, to);
    }

    @GetMapping("/losses")
    @PreAuthorize("hasAuthority('OEE_VIEW')")
    public OeeLossesDto losses(
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.losses(machineId, from, to);
    }

    @GetMapping("/downtime-reasons")
    @PreAuthorize("hasAuthority('OEE_VIEW')")
    public List<DowntimeReasonDto> downtimeReasons(
            @RequestParam(required = false) Long machineId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "5") int limit) {
        return service.topDowntimeReasons(machineId, from, to, Math.min(Math.max(limit, 1), 50));
    }
}
