import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;

/**
 * Allows to achieve fine-grained control over pool of shared threads utilization per tenant.
 * <p>
 * The idea is to have single shared thread pool which easy to control, along with virtual thread pool
 * per tenant which refers to shared pool under the hood, but also tracks number of used threads per tenant.
 * <p>
 * Internal counter allows to prevent swallowing all the available threads by single tenant. In case of failure on
 * one tenant, it guarantees that shared pool won't be DoS attacked by the tenant i will continue serving for other tenants.
 * <p>
 * see more https://docs.microsoft.com/en-us/azure/architecture/patterns/bulkhead
 */
final class TenantAwareExecutor extends AbstractExecutorService {
  private final AtomicInteger counter; //count current used threads by tenant
  private final String tenantId; // just for logging purposes
  private final int tenantLimit; //controls how many threads at most tenant can use from shared pool
  private final ExecutorService origin; // shared pool

  TenantAwareExecutor(String tenantId, int tenantLimit, ExecutorService origin) {
    this.tenantId = tenantId;
    this.tenantLimit = tenantLimit;
    this.origin = origin;
    counter = new AtomicInteger();
  }

  @Override
  public void shutdown() {
    origin.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return origin.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return origin.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return origin.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return origin.awaitTermination(timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    if (counter.incrementAndGet() > tenantLimit) {
      counter.decrementAndGet();
      throw new RuntimeException(format("limit for tenant %s is reached", tenantId));
    }
    origin.execute(() -> {
      try {
        command.run();
      } finally {
        counter.decrementAndGet();
      }
    });
  }
}
