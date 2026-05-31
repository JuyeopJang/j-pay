package juyeop.jpay.payment.controller;

import jakarta.validation.Valid;
import juyeop.jpay.common.idempotency.Idempotent;
import juyeop.jpay.payment.dto.TransferRequest;
import juyeop.jpay.payment.dto.TransferResponse;
import juyeop.jpay.payment.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @Idempotent
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(userId, request);
    }
}