package com.mfg.ims.production.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import com.mfg.ims.production.dto.ProductionOperationDto;
import com.mfg.ims.production.service.OperationExecutionService;

@RestController
@RequestMapping("/api/production")
public class PlanBoardController {

    private final OperationExecutionService executionService;

    public PlanBoardController(OperationExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping("/plan-board")
    @PreAuthorize("hasAuthority('PLAN_BOARD_VIEW')")
    public List<ProductionOperationDto> planBoard() {
        return executionService.getPlanBoard();
    }

    @GetMapping("/shop-floor")
    @PreAuthorize("hasAuthority('SHOP_FLOOR_VIEW')")
    public List<ProductionOperationDto> shopFloor() {
        return executionService.getShopFloor();
    }
}
