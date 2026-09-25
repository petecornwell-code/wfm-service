package com.wfm.controller;

import com.wfm.dto.ErlangCCalculationRequest;
import com.wfm.dto.ErlangCalculationResponse;
import com.wfm.dto.ErlangXCalculationRequest;
import com.wfm.service.ErlangCalculatorService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A read-only staffing calculator: a request of numbers in, an answer of numbers out, nothing
 * stored. Deliberately NOT under {@code /desks/{deskId}} — the maths needs no desk, no timeslot and
 * no specialization, and nesting it under a desk would imply a scope it does not have and rows it
 * does not write.
 *
 * <p>Contrast {@code POST /desks/{deskId}/staffing-requirements/erlang-x}, which persists: it
 * deletes every live requirement in the date range and replaces it with its results. The two are
 * different operations and are kept at different paths for that reason.
 *
 * <p>The {@code X-Tenant-ID} header is still required, because {@code TenantFilter} exempts only
 * {@code /actuator}. Nothing here reads the tenant.
 */
@RestController
@RequestMapping("/api/v1/calc")
public class ErlangCalculatorController {

    private final ErlangCalculatorService erlangCalculatorService;

    public ErlangCalculatorController(ErlangCalculatorService erlangCalculatorService) {
        this.erlangCalculatorService = erlangCalculatorService;
    }

    @PostMapping("/erlang-c")
    public ErlangCalculationResponse calculateErlangC(@RequestBody ErlangCCalculationRequest request) {
        return erlangCalculatorService.calculateErlangC(request);
    }

    @PostMapping("/erlang-x")
    public ErlangCalculationResponse calculateErlangX(@RequestBody ErlangXCalculationRequest request) {
        return erlangCalculatorService.calculateErlangX(request);
    }
}
