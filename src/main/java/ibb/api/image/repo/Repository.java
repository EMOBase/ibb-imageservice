package ibb.api.image.repo;

public interface Repository<T> {
    T get(String id);
}
