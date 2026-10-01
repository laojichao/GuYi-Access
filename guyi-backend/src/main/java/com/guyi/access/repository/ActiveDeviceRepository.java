package com.guyi.access.repository;

import com.guyi.access.entity.ActiveDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ActiveDeviceRepository extends JpaRepository<ActiveDevice, Integer> {

    Optional<ActiveDevice> findByDeviceHashAndStatusAndExpireTimeAfterAndAppId(
            String deviceHash, Integer status, LocalDateTime expireTime, Integer appId);

    @Modifying
    @Query("DELETE FROM ActiveDevice d WHERE d.cardCode = :cardCode")
    int deleteByCardCode(@Param("cardCode") String cardCode);

    @Modifying
    @Query("DELETE FROM ActiveDevice d WHERE d.cardCode IN :cardCodes")
    int deleteByCardCodeIn(@Param("cardCodes") List<String> cardCodes);

    @Query("SELECT d FROM ActiveDevice d JOIN Application a ON d.appId = a.id WHERE d.status = 1 AND d.expireTime > CURRENT_TIMESTAMP AND d.appId > 0 ORDER BY d.activateTime DESC")
    List<Object[]> findActiveDevicesWithAppName();

    @Query("SELECT COUNT(d) FROM ActiveDevice d WHERE d.status = 1 AND d.expireTime > CURRENT_TIMESTAMP AND d.appId > 0")
    long countActiveDevices();

    @Modifying
    @Query("UPDATE ActiveDevice d SET d.status = 0 WHERE d.status = 1 AND d.expireTime <= CURRENT_TIMESTAMP")
    int deactivateExpiredDevices();

    // HQL timestampadd instead of MySQL-only DATE_ADD/INTERVAL (see CardRepository for the rationale)
    @Modifying
    @Query("UPDATE ActiveDevice d SET d.expireTime = timestampadd(SECOND, :seconds, d.expireTime) "
         + "WHERE d.cardCode IN :cardCodes")
    int addTimeByCardCodes(@Param("cardCodes") List<String> cardCodes, @Param("seconds") long seconds);

    @Modifying
    @Query("UPDATE ActiveDevice d SET d.expireTime = timestampadd(SECOND, (0 - :seconds), d.expireTime) "
         + "WHERE d.cardCode IN :cardCodes")
    int subTimeByCardCodes(@Param("cardCodes") List<String> cardCodes, @Param("seconds") long seconds);

    /** {@code appId <= 0} means "every application". */
    @Modifying
    @Query("UPDATE ActiveDevice d SET d.expireTime = timestampadd(SECOND, :seconds, d.expireTime) "
         + "WHERE d.status = 1 AND d.expireTime > CURRENT_TIMESTAMP AND (:appId <= 0 OR d.appId = :appId)")
    int globalAddTime(@Param("seconds") long seconds, @Param("appId") int appId);
}
