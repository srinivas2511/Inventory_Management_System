package com.mfg.ims.analytics.register;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registers")
public class RegisterController {

    private final RegisterService service;

    public RegisterController(RegisterService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REGISTER_VIEW')")
    public List<String> listCodes() {
        return service.listCodes();
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAuthority('REGISTER_VIEW')")
    public RegisterDto get(@PathVariable String code) {
        return service.get(code);
    }
}
