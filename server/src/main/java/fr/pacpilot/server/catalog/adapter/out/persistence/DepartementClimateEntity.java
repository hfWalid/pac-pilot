package fr.pacpilot.server.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The {@code catalog_departement_climate} row. Reference data; never written by the application. */
@Entity
@Table(name = "catalog_departement_climate")
class DepartementClimateEntity {

    @Id
    @Column(name = "departement_code")
    private String departementCode;

    @Column(name = "zone", nullable = false)
    private String zone;

    @Column(name = "base_temperature_deci_c", nullable = false)
    private int baseTemperatureDeciC;

    @Column(name = "source", nullable = false)
    private String source;

    protected DepartementClimateEntity() {}

    String getDepartementCode() {
        return departementCode;
    }

    String getZone() {
        return zone;
    }

    int getBaseTemperatureDeciC() {
        return baseTemperatureDeciC;
    }

    String getSource() {
        return source;
    }
}
