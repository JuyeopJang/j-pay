package juyeop.jpay.ledger.contract;

import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.V4Interaction;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import juyeop.jpay.common.event.ChargeCompletedEvent;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.common.event.UserTransferCompletedEvent;
import juyeop.jpay.ledger.consumer.ChargeCompletedEventConsumer;
import juyeop.jpay.ledger.consumer.PaymentCompletedEventConsumer;
import juyeop.jpay.ledger.consumer.UserTransferCompletedEventConsumer;
import juyeop.jpay.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-api", providerType = ProviderType.ASYNCH)
class LedgerEventContractConsumerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private LedgerService ledgerService;
    private ChargeCompletedEventConsumer chargeConsumer;
    private PaymentCompletedEventConsumer paymentConsumer;
    private UserTransferCompletedEventConsumer transferConsumer;

    @BeforeEach
    void setUp() {
        ledgerService = mock(LedgerService.class);
        chargeConsumer = new ChargeCompletedEventConsumer(OBJECT_MAPPER, ledgerService);
        paymentConsumer = new PaymentCompletedEventConsumer(OBJECT_MAPPER, ledgerService);
        transferConsumer = new UserTransferCompletedEventConsumer(OBJECT_MAPPER, ledgerService);
    }

    // -------------------------------------------------------------------------
    // charge.completed
    // -------------------------------------------------------------------------

    @Pact(consumer = "ledger-service", provider = "payment-api")
    V4Pact chargeCompletedPact(PactBuilder builder) {
        return builder
                .expectsToReceiveMessageInteraction("a charge completed event", mb -> mb
                        .withContents(cb -> cb
                                .withContent(new PactDslJsonBody()
                                        .numberType("chargeId", 1001L)
                                        .numberType("userId", 1L)
                                        .numberType("amount", 50000L)
                                        .stringType("occurredAt", "2024-01-01T00:00:00Z"))))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "chargeCompletedPact")
    void consumeChargeCompleted(V4Interaction.AsynchronousMessage message) throws Exception {
        chargeConsumer.consume(message.contentsAsString());
        verify(ledgerService).record(any(ChargeCompletedEvent.class));
    }

    // -------------------------------------------------------------------------
    // payment.completed
    // -------------------------------------------------------------------------

    @Pact(consumer = "ledger-service", provider = "payment-api")
    V4Pact paymentCompletedPact(PactBuilder builder) {
        return builder
                .expectsToReceiveMessageInteraction("a payment completed event", mb -> mb
                        .withContents(cb -> cb
                                .withContent(new PactDslJsonBody()
                                        .numberType("paymentId", 2001L)
                                        .numberType("userId", 1L)
                                        .stringType("merchantId", "merchant-1")
                                        .numberType("amount", 10000L)
                                        .stringType("occurredAt", "2024-01-01T00:00:00Z"))))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "paymentCompletedPact")
    void consumePaymentCompleted(V4Interaction.AsynchronousMessage message) throws Exception {
        paymentConsumer.consume(message.contentsAsString());
        verify(ledgerService).record(any(PaymentCompletedEvent.class));
    }

    // -------------------------------------------------------------------------
    // user-transfer.completed
    // -------------------------------------------------------------------------

    @Pact(consumer = "ledger-service", provider = "payment-api")
    V4Pact userTransferCompletedPact(PactBuilder builder) {
        return builder
                .expectsToReceiveMessageInteraction("a user transfer completed event", mb -> mb
                        .withContents(cb -> cb
                                .withContent(new PactDslJsonBody()
                                        .numberType("transferId", 3001L)
                                        .numberType("fromUserId", 1L)
                                        .numberType("toUserId", 2L)
                                        .numberType("amount", 10000L)
                                        .stringType("occurredAt", "2024-01-01T00:00:00Z"))))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "userTransferCompletedPact")
    void consumeUserTransferCompleted(V4Interaction.AsynchronousMessage message) throws Exception {
        transferConsumer.consume(message.contentsAsString());
        verify(ledgerService).record(any(UserTransferCompletedEvent.class));
    }
}
