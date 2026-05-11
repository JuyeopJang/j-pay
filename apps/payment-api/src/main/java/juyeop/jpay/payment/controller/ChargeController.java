package juyeop.jpay.payment.controller;

import jakarta.validation.Valid;
import juyeop.jpay.payment.dto.ChargeRequest;
import juyeop.jpay.payment.dto.ChargeResponse;
import juyeop.jpay.payment.service.ChargeFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/charges")
@RequiredArgsConstructor
public class ChargeController {

    private final ChargeFacadeService chargeFacadeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChargeResponse charge(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChargeRequest request) {
        return chargeFacadeService.charge(idempotencyKey, userId, request);
    }
}
