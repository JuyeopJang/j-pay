package juyeop.jpay.payment.contract;

import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import juyeop.jpay.common.event.ChargeCompletedEvent;
import juyeop.jpay.common.event.PaymentCompletedEvent;
import juyeop.jpay.common.event.UserTransferCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.List;

@Provider("payment-api")
@PactBroker(url = "http://localhost:9292", authentication = @PactBrokerAuth(username = "pact", password = "pact"))
class PaymentApiPactVerificationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp(PactVerificationContext context) {
        if (context != null) {
            context.setTarget(new MessageTestTarget(List.of("juyeop.jpay.payment.contract")));
        }
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @PactVerifyProvider("a charge completed event")
    String chargeCompletedMessage() throws Exception {
        return OBJECT_MAPPER.writeValueAsString(
                new ChargeCompletedEvent(1001L, 1L, 50000L, Instant.parse("2024-01-01T00:00:00Z")));
    }

    @PactVerifyProvider("a payment completed event")
    String paymentCompletedMessage() throws Exception {
        return OBJECT_MAPPER.writeValueAsString(
                new PaymentCompletedEvent(2001L, 1L, "merchant-1", 10000L, Instant.parse("2024-01-01T00:00:00Z")));
    }

    @PactVerifyProvider("a user transfer completed event")
    String userTransferCompletedMessage() throws Exception {
        return OBJECT_MAPPER.writeValueAsString(
                new UserTransferCompletedEvent(3001L, 1L, 2L, 10000L, Instant.parse("2024-01-01T00:00:00Z")));
    }
}