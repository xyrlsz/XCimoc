package io.objectbox.converter;

/**
 * Stub for ObjectBox PropertyConverter interface
 */
public interface PropertyConverter<E, D> {
    E convertToEntityProperty(D databaseValue);
    D convertToDatabaseValue(E entityProperty);
}
