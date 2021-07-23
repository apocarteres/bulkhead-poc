import java.util.concurrent.ExecutorService;

final class Tenant {
  final String name;
  final ExecutorService service;

  Tenant(String name, ExecutorService service) {
    this.name = name;
    this.service = service;
  }
}
