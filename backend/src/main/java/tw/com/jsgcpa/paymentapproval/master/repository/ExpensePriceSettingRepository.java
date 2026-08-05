package tw.com.jsgcpa.paymentapproval.master.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;

public interface ExpensePriceSettingRepository extends JpaRepository<ExpensePriceSetting, Long> {

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            join fetch priceSetting.expenseType expenseType
            order by expenseType.code asc,
                     priceSetting.effectiveFrom desc,
                     priceSetting.id desc
            """)
    List<ExpensePriceSetting> findAdminAll();

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            join fetch priceSetting.expenseType expenseType
            where priceSetting.expenseType.id = :expenseTypeId
            order by expenseType.code asc,
                     priceSetting.effectiveFrom desc,
                     priceSetting.id desc
            """)
    List<ExpensePriceSetting> findAdminByExpenseTypeId(
            @Param("expenseTypeId") Long expenseTypeId
    );

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            join fetch priceSetting.expenseType expenseType
            where priceSetting.active = :active
            order by expenseType.code asc,
                     priceSetting.effectiveFrom desc,
                     priceSetting.id desc
            """)
    List<ExpensePriceSetting> findAdminByActive(@Param("active") Boolean active);

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            join fetch priceSetting.expenseType expenseType
            where priceSetting.effectiveFrom <= :effectiveOn
              and (priceSetting.effectiveTo is null
                   or priceSetting.effectiveTo >= :effectiveOn)
            order by expenseType.code asc,
                     priceSetting.effectiveFrom desc,
                     priceSetting.id desc
            """)
    List<ExpensePriceSetting> findAdminByEffectiveOn(
            @Param("effectiveOn") LocalDate effectiveOn
    );

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            join fetch priceSetting.expenseType expenseType
            where priceSetting.expenseType.id = :expenseTypeId
              and priceSetting.active = :active
            order by expenseType.code asc,
                     priceSetting.effectiveFrom desc,
                     priceSetting.id desc
            """)
    List<ExpensePriceSetting> findAdminByExpenseTypeIdAndActive(
            @Param("expenseTypeId") Long expenseTypeId,
            @Param("active") Boolean active
    );

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            join fetch priceSetting.expenseType expenseType
            where priceSetting.expenseType.id = :expenseTypeId
              and priceSetting.effectiveFrom <= :effectiveOn
              and (priceSetting.effectiveTo is null
                   or priceSetting.effectiveTo >= :effectiveOn)
            order by expenseType.code asc,
                     priceSetting.effectiveFrom desc,
                     priceSetting.id desc
            """)
    List<ExpensePriceSetting> findAdminByExpenseTypeIdAndEffectiveOn(
            @Param("expenseTypeId") Long expenseTypeId,
            @Param("effectiveOn") LocalDate effectiveOn
    );

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            join fetch priceSetting.expenseType expenseType
            where priceSetting.active = :active
              and priceSetting.effectiveFrom <= :effectiveOn
              and (priceSetting.effectiveTo is null
                   or priceSetting.effectiveTo >= :effectiveOn)
            order by expenseType.code asc,
                     priceSetting.effectiveFrom desc,
                     priceSetting.id desc
            """)
    List<ExpensePriceSetting> findAdminByActiveAndEffectiveOn(
            @Param("active") Boolean active,
            @Param("effectiveOn") LocalDate effectiveOn
    );

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            join fetch priceSetting.expenseType expenseType
            where priceSetting.expenseType.id = :expenseTypeId
              and priceSetting.active = :active
              and priceSetting.effectiveFrom <= :effectiveOn
              and (priceSetting.effectiveTo is null
                   or priceSetting.effectiveTo >= :effectiveOn)
            order by expenseType.code asc,
                     priceSetting.effectiveFrom desc,
                     priceSetting.id desc
            """)
    List<ExpensePriceSetting> findAdminByExpenseTypeIdAndActiveAndEffectiveOn(
            @Param("expenseTypeId") Long expenseTypeId,
            @Param("active") Boolean active,
            @Param("effectiveOn") LocalDate effectiveOn
    );

    List<ExpensePriceSetting> findByExpenseType_IdOrderByEffectiveFromDescIdDesc(
            Long expenseTypeId
    );

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            where priceSetting.expenseType.id = :expenseTypeId
              and priceSetting.priceCode = :priceCode
              and priceSetting.id <> :excludedId
              and priceSetting.effectiveFrom <= coalesce(:newTo, priceSetting.effectiveFrom)
              and coalesce(priceSetting.effectiveTo, :newFrom) >= :newFrom
            """)
    List<ExpensePriceSetting> findOverlappingPeriods(
            @Param("expenseTypeId") Long expenseTypeId,
            @Param("priceCode") String priceCode,
            @Param("newFrom") LocalDate newFrom,
            @Param("newTo") LocalDate newTo,
            @Param("excludedId") Long excludedId
    );

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            where priceSetting.expenseType.id = :expenseTypeId
              and priceSetting.priceCode = :priceCode
              and priceSetting.active = true
              and priceSetting.id <> :excludedId
              and priceSetting.effectiveFrom <= coalesce(:newTo, priceSetting.effectiveFrom)
              and coalesce(priceSetting.effectiveTo, :newFrom) >= :newFrom
            """)
    List<ExpensePriceSetting> findOverlappingActivePeriods(
            @Param("expenseTypeId") Long expenseTypeId,
            @Param("priceCode") String priceCode,
            @Param("newFrom") LocalDate newFrom,
            @Param("newTo") LocalDate newTo,
            @Param("excludedId") Long excludedId
    );

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            where priceSetting.expenseType.id = :expenseTypeId
              and priceSetting.priceCode = :priceCode
              and priceSetting.active = true
              and priceSetting.effectiveFrom <= :effectiveDate
              and (
                    priceSetting.effectiveTo is null
                    or priceSetting.effectiveTo >= :effectiveDate
              )
            order by priceSetting.effectiveFrom desc,
                     priceSetting.id desc
            """)
    List<ExpensePriceSetting> findEffectivePriceSettings(
            @Param("expenseTypeId") Long expenseTypeId,
            @Param("priceCode") String priceCode,
            @Param("effectiveDate") LocalDate effectiveDate
    );

    @Query("""
            select priceSetting
            from ExpensePriceSetting priceSetting
            where priceSetting.expenseType.id = :expenseTypeId
              and priceSetting.active = true
              and priceSetting.effectiveFrom <= :today
              and (
                    priceSetting.effectiveTo is null
                    or priceSetting.effectiveTo >= :today
              )
            order by priceSetting.priceCode asc, priceSetting.id asc
            """)
    List<ExpensePriceSetting> findEffectivePrices(
            @Param("expenseTypeId") Long expenseTypeId,
            @Param("today") LocalDate today
    );
}
