package de.unigoettingen.ibeetlebase.images.repository;

public interface Repository<T> {
    T get(String id);
}
