import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.lang.String.format;
import static java.lang.Thread.sleep;

public final class BulkheadPoC {

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    ExecutorService shared = Executors.newFixedThreadPool(8);
    try {
      List<Tenant> tenants = new ArrayList<>();
      //let's spawn 10 000 tenants. For each tenant at most 5 threads is allowed.
      // In case of tenant specific failures shared pool won't be suffering from DoS.
      for (int i = 0; i < 10_000; i++) {
        String tenantId = format("tenant-%d", i);
        tenants.add(new Tenant(tenantId, new TenantAwareExecutor(tenantId, 5, shared)));
      }
      List<Future<?>> futures = new ArrayList<>();
      //submit 5 jobs for tenant 100
      futures.addAll(submitTasksFor(tenants, 100, 5));
      //submit 5 jobs for tenant 200
      futures.addAll(submitTasksFor(tenants, 200, 5));
      //submit 5 jobs for tenant 300
      futures.addAll(submitTasksFor(tenants, 300, 5));
      //... an attempt to submit one more job which MUST be rejected due to 5 threads limit per tenant
      futures.addAll(submitTasksFor(tenants, 100, 1));
      System.out.println("all the tasks submitted. Pending results");
      for (Future<?> future : futures) {
        future.get();
      }
    } finally {
      shared.shutdown();
    }
  }

  private static List<Future<?>> submitTasksFor(List<Tenant> tenants, int tenantId, int tasks) {
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < tasks; ++i) {
      System.out.printf("submitting task %d for tenant %s%n", i, tenantId);
      final int taskId = i;
      futures.add(tenants.get(tenantId).service.submit(() -> {
        System.out.printf("executing task %d for tenant %s%n", taskId, tenantId);
        sleep(5_000);//simulate long running job
        System.out.printf("task %d for tenant %s is completed%n", taskId, tenantId);
        return true;
      }));
    }
    return futures;
  }
}
