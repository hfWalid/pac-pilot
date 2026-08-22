package fr.pacpilot.server.catalog.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data's view of the two reference tables. Package-private. */
interface DepartementClimateJpaRepository extends JpaRepository<DepartementClimateEntity, String> {}

/** Package-private; the api surface is what other contexts see. */
interface ProductJpaRepository extends JpaRepository<ProductEntity, String> {

    @Query(
            "select p from ProductEntity p"
                    + " where p.powerAtMinusSevenWatts between :minimum and :maximum"
                    + " order by p.powerAtMinusSevenWatts asc")
    List<ProductEntity> withinBand(@Param("minimum") int minimum, @Param("maximum") int maximum);
}
