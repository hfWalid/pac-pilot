package fr.pacpilot.server.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The {@code catalog_product} row. Reference data; never written by the application. */
@Entity
@Table(name = "catalog_product")
class ProductEntity {

    @Id private String id;

    @Column(name = "brand", nullable = false)
    private String brand;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "power_at_minus_seven_w", nullable = false)
    private int powerAtMinusSevenWatts;

    /**
     * A Postgres {@code text[]}. Mapped as an array rather than a join table because the set is tiny,
     * fixed by the {@code EmitterType} enum, and never queried on its own.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "compatible_emitters", nullable = false)
    private String[] compatibleEmitters;

    @Column(name = "source", nullable = false)
    private String source;

    protected ProductEntity() {}

    String getId() {
        return id;
    }

    String getBrand() {
        return brand;
    }

    String getModel() {
        return model;
    }

    int getPowerAtMinusSevenWatts() {
        return powerAtMinusSevenWatts;
    }

    String[] getCompatibleEmitters() {
        return compatibleEmitters.clone();
    }

    String getSource() {
        return source;
    }
}
