package inno.orderservice.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceLoggingAspectTest {

    private final ServiceLoggingAspect aspect = new ServiceLoggingAspect();

    private Logger aspectLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        aspectLogger = (Logger) LoggerFactory.getLogger(ServiceLoggingAspect.class);
        appender = new ListAppender<>();
        appender.start();
        aspectLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        aspectLogger.detachAppender(appender);
    }

    @Test
    void shouldLogEntryAndExitAndReturnResult() throws Throwable {
        Object result = new Object();
        ProceedingJoinPoint joinPoint = joinPointMock();
        when(joinPoint.proceed()).thenReturn(result);

        Object returned = aspect.logMethodExecution(joinPoint);

        assertSame(result, returned);
        assertEquals(2, appender.list.size());
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage()
                .contains("Entering OrderService.createOrder with args="));
        assertTrue(appender.list.get(1).getFormattedMessage()
                .contains("Exiting OrderService.createOrder in "));
    }

    @Test
    void shouldLogExceptionAndRethrow() throws Throwable {
        IllegalStateException failure = new IllegalStateException("boom");
        ProceedingJoinPoint joinPoint = joinPointMock();
        when(joinPoint.proceed()).thenThrow(failure);

        assertThrows(IllegalStateException.class, () -> aspect.logMethodExecution(joinPoint));

        assertEquals(2, appender.list.size());
        assertEquals(Level.ERROR, appender.list.get(1).getLevel());
        assertTrue(appender.list.get(1).getFormattedMessage()
                .contains("Exception in OrderService.createOrder after "));
        assertTrue(appender.list.get(1).getFormattedMessage().contains("boom"));
    }

    private ProceedingJoinPoint joinPointMock() {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringType()).thenReturn(inno.orderservice.service.OrderService.class);
        when(signature.getName()).thenReturn("createOrder");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"vova@gmail.com"});
        return joinPoint;
    }
}