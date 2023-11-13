package ibb.api.imageservice.repo;

public interface Repository<T> {
    T get(String id);
}
