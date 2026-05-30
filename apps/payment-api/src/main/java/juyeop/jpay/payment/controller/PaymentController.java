package juyeop.jpay.payment.controller;

import jakarta.validation.Valid;
import juyeop.jpay.common.idempotency.Idempotent;
import juyeop.jpay.payment.dto.PaymentRequest;
import juyeop.jpay.payment.dto.PaymentResponse;
import juyeop.jpay.payment.service.PaymentFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacadeService paymentFacadeService;

    @Idempotent
    @PostMapping("/optimistic")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse payOptimistic(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PaymentRequest request) {
        return paymentFacadeService.payOptimistic(idempotencyKey, userId, request);
    }

    @Idempotent
    @PostMapping("/pessimistic")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse payPessimistic(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PaymentRequest request) {
        return paymentFacadeService.payPessimistic(idempotencyKey, userId, request);
    }

    @Idempotent
    @PostMapping("/atomic")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse payAtomic(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PaymentRequest request) {
        return paymentFacadeService.payAtomic(idempotencyKey, userId, request);
    }

    @Idempotent
    @PostMapping("/redis-lock")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse payWithRedisLock(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PaymentRequest request) {
        return paymentFacadeService.payWithRedisLock(idempotencyKey, userId, request);
    }
}