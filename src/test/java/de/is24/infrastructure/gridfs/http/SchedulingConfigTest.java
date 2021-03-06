package de.is24.infrastructure.gridfs.http;

import de.is24.infrastructure.gridfs.http.security.UserAuthorities;
import de.is24.util.monitoring.InApplicationMonitor;
import de.is24.util.monitoring.StateValueProvider;
import de.is24.util.monitoring.TestingInApplicationMonitor;
import de.is24.util.monitoring.tools.DoNothingReportVisitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;


public class SchedulingConfigTest {
  private SchedulingConfig schedulingConfig;
  private ScheduledExecutorService scheduledExecutorService;

  @Before
  public void setUp() {
    TestingInApplicationMonitor.resetInstanceForTesting();
    schedulingConfig = new SchedulingConfig();
    schedulingConfig.inApplicationMonitor = TestingInApplicationMonitor.getInstance();
    schedulingConfig.schedulerPoolSize = 1;

    scheduledExecutorService = schedulingConfig.scheduledExecutorService();
  }

  @After
  public void tearDown() {
    scheduledExecutorService.shutdown();
  }

  @Test
  public void taskSchedulerRegistersQueueSizeValueProvider() throws Exception {
    QueueSizeVisitor queueSizeVisitor = new QueueSizeVisitor();
    InApplicationMonitor.getInstance().getCorePlugin().reportInto(queueSizeVisitor);
    assertThat(queueSizeVisitor.knowsQueueSizeState(), is(true));
  }

  @Test
  public void taskSchedulerRegistersPoolSizeValueProvider() throws Exception {
    QueueSizeVisitor queueSizeVisitor = new QueueSizeVisitor();
    InApplicationMonitor.getInstance().getCorePlugin().reportInto(queueSizeVisitor);
    assertThat(queueSizeVisitor.knowsPoolSizeState(), is(true));
  }

  @Test
  public void taskSchedulerRegistersActiveCountValueProvider() throws Exception {
    QueueSizeVisitor queueSizeVisitor = new QueueSizeVisitor();
    InApplicationMonitor.getInstance().getCorePlugin().reportInto(queueSizeVisitor);
    assertThat(queueSizeVisitor.knowsActiveCountState(), is(true));
  }

  @Test
  public void scheduledRunnablesGetASecurityContextSet() throws Exception {
    ContextValidatingRunnable task = new ContextValidatingRunnable();
    Future<?> taskFuture = scheduledExecutorService.submit(task);
    taskFuture.get(10, TimeUnit.SECONDS);

    Authentication authentication = task.getAuthentication();
    assertThat(authentication, notNullValue());
    assertThat(authentication.getAuthorities().size(), is(1));
    assertThat(authentication.getAuthorities(), is(UserAuthorities.USER_AUTHORITIES));


  }

  private final class QueueSizeVisitor extends DoNothingReportVisitor {
    private boolean knowsQueueSizeState = false;
    private boolean knowsPoolSizeState = false;
    private boolean knowsActiveCountState = false;


    public boolean knowsQueueSizeState() {
      return knowsQueueSizeState;
    }

    public boolean knowsPoolSizeState() {
      return knowsPoolSizeState;
    }

    public boolean knowsActiveCountState() {
      return knowsActiveCountState;
    }

    @Override
    public void reportStateValue(StateValueProvider stateValueProvider) {
      if ("metadata.scheduler.queueSize".equals(stateValueProvider.getName())) {
        knowsQueueSizeState = true;
      } else if ("metadata.scheduler.poolSize".equals(stateValueProvider.getName())) {
        knowsPoolSizeState = true;
      } else if ("metadata.scheduler.activeCount".equals(stateValueProvider.getName())) {
        knowsActiveCountState = true;
      }
    }

  }

  private class ContextValidatingRunnable implements Runnable {
    private Authentication authentication;

    @Override
    public void run() {
      authentication = SecurityContextHolder.getContext().getAuthentication();
    }

    public Authentication getAuthentication() {
      return authentication;
    }
  }
}
