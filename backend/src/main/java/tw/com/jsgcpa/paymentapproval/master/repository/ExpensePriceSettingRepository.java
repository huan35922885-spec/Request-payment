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
