package fr.pacpilot.server.catalog.adapter.out.persistence;

import fr.pacpilot.core.dimensioning.model.EmitterType;
import fr.pacpilot.core.shared.ClimateZone;
import fr.pacpilot.core.shared.Departement;
import fr.pacpilot.core.shared.DepartementClimate;
import fr.pacpilot.core.shared.PowerKw;
import fr.pacpilot.core.shared.TemperatureC;
import fr.pacpilot.server.catalog.api.CatalogProduct;
import fr.pacpilot.server.catalog.api.ClimateReference;
import fr.pacpilot.server.catalog.api.ProductCatalogue;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Satisfies both Catalog surfaces from the reference tables.
 *
 * <p>One class for two interfaces because they share nothing but a data source and would otherwise
 * be two near-identical files. If either grows behaviour, split it then.
 */
@Repository
@Transactional(readOnly = true)
class JpaCatalogReference implements ClimateReference, ProductCatalogue {

    private final DepartementClimateJpaRepository climates;
    private final ProductJpaRepository products;

    JpaCatalogReference(DepartementClimateJpaRepository climates, ProductJpaRepository products) {
        this.climates = climates;
        this.products = products;
    }

    @Override
    public Optional<DepartementClimate> forDepartement(String departementCode) {
        return climates
                .findById(departementCode)
                .map(
                        row ->
                                new DepartementClimate(
                                        new Departement(row.getDepartementCode()),
                                        ClimateZone.valueOf(row.getZone()),
                                        new TemperatureC(row.getBaseTemperatureDeciC()),
                                        row.getSource()));
    }

    @Override
    public Optional<CatalogProduct> findById(String id) {
        return products.findById(id).map(JpaCatalogReference::toProduct);
    }

    @Override
    public List<CatalogProduct> withinBand(PowerKw minimum, PowerKw maximum) {
        return products.withinBand(minimum.getWatts(), maximum.getWatts()).stream()
                .map(JpaCatalogReference::toProduct)
                .toList();
    }

    private static CatalogProduct toProduct(ProductEntity row) {
        Set<EmitterType> emitters =
                Arrays.stream(row.getCompatibleEmitters())
                        .map(EmitterType::valueOf)
                        .collect(Collectors.toUnmodifiableSet());
        return new CatalogProduct(
                row.getId(),
                row.getBrand(),
                row.getModel(),
                new PowerKw(row.getPowerAtMinusSevenWatts()),
                emitters,
                row.getSource());
    }
}
