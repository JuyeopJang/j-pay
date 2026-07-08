import static net.grinder.script.Grinder.grinder
import net.grinder.plugin.http.HTTPPluginControl
import net.grinder.plugin.http.HTTPRequest
import net.grinder.script.Test
import net.grinder.scriptengine.groovy.junit.GrinderRunner
import net.grinder.scriptengine.groovy.junit.annotation.BeforeProcess
import net.grinder.scriptengine.groovy.junit.annotation.BeforeThread
import org.junit.Test as JTest
import org.junit.runner.RunWith
import HTTPClient.NVPair

@RunWith(GrinderRunner)
class ChargeTest {

    public static final String TARGET       = "http://172.31.24.126"
    public static final String MERCHANT_ID  = "1"
    public static final int    USER_COUNT   = 5_000_000
    public static final long   CHARGE_AMT   = 1000L
    public static final long   PAY_AMT      = 100L
    public static final long   TRANSFER_AMT = 100L

    public static final int THREADS_PER_PROCESS = 38

    // 228 VU 역할 분배: 충전 30% / 결제 60% / 이체 10%
    public static final int CHARGE_VU_END  = 68   // VU 0~67
    public static final int PAYMENT_VU_END = 204  // VU 68~203

    public static Test        chargeTest
    public static Test        payTest
    public static Test        transferTest
    public static HTTPRequest chargeRequest
    public static HTTPRequest payRequest
    public static HTTPRequest transferRequest

    private static final Random rnd = new Random()

    private String role
    private long   userId
    private String bankAccountId
    private long   toUserId

    @BeforeProcess
    public static void beforeProcess() {
        HTTPPluginControl.getConnectionDefaults().timeout = 30000
        chargeTest      = new Test(1, "POST /charges")
        payTest         = new Test(2, "POST /payments/pessimistic")
        transferTest    = new Test(3, "POST /transfers")
        chargeRequest   = new HTTPRequest()
        payRequest      = new HTTPRequest()
        transferRequest = new HTTPRequest()
        grinder.logger.info("target: " + TARGET)
    }

    @BeforeThread
    public void beforeThread() {
        long vuId = (long)(grinder.getProcessNumber() * THREADS_PER_PROCESS + grinder.getThreadNumber())
        userId        = (long)(rnd.nextInt(USER_COUNT) + 1)
        bankAccountId = "BA" + String.valueOf(userId).padLeft(14, '0')

        if (vuId < CHARGE_VU_END) {
            role = "charge"
            chargeTest.record(chargeRequest)
        } else if (vuId < PAYMENT_VU_END) {
            role = "payment"
            payTest.record(payRequest)
        } else {
            role = "transfer"
            long receiverId = (long)(rnd.nextInt(USER_COUNT) + 1)
            toUserId = (receiverId != userId) ? receiverId : (userId % USER_COUNT) + 1
            transferTest.record(transferRequest)
        }

        grinder.statistics.delayReports = true
        grinder.logger.info("VU ${vuId} role=${role} userId=${userId}")
    }

    @JTest
    public void test01() {
        if ("charge".equals(role)) {
            NVPair[] headers = [
                new NVPair("Content-Type",    "application/json"),
                new NVPair("X-User-Id",       String.valueOf(userId)),
                new NVPair("Idempotency-Key", UUID.randomUUID().toString())
            ] as NVPair[]

            def res = chargeRequest.POST(
                TARGET + "/charges",
                """{"amount":${CHARGE_AMT},"bankAccountId":"${bankAccountId}"}""".getBytes("UTF-8"),
                headers
            )
            if (res.statusCode != 201) {
                grinder.logger.warn("charge HTTP ${res.statusCode} userId=${userId}")
                throw new AssertionError("charge expected 201, got ${res.statusCode}")
            }

        } else if ("payment".equals(role)) {
            NVPair[] headers = [
                new NVPair("Content-Type",    "application/json"),
                new NVPair("X-User-Id",       String.valueOf(userId)),
                new NVPair("Idempotency-Key", UUID.randomUUID().toString())
            ] as NVPair[]

            def res = payRequest.POST(
                TARGET + "/payments/pessimistic",
                """{"amount":${PAY_AMT},"merchantId":"${MERCHANT_ID}"}""".getBytes("UTF-8"),
                headers
            )
            if (res.statusCode != 201) {
                grinder.logger.warn("pay HTTP ${res.statusCode} userId=${userId}")
                throw new AssertionError("pay expected 201, got ${res.statusCode}")
            }

        } else {
            NVPair[] headers = [
                new NVPair("Content-Type",    "application/json"),
                new NVPair("X-User-Id",       String.valueOf(userId)),
                new NVPair("Idempotency-Key", UUID.randomUUID().toString())
            ] as NVPair[]

            def res = transferRequest.POST(
                TARGET + "/transfers",
                """{"toUserId":${toUserId},"amount":${TRANSFER_AMT}}""".getBytes("UTF-8"),
                headers
            )
            if (res.statusCode != 201) {
                grinder.logger.warn("transfer HTTP ${res.statusCode} userId=${userId}")
                throw new AssertionError("transfer expected 201, got ${res.statusCode}")
            }
        }
    }
}