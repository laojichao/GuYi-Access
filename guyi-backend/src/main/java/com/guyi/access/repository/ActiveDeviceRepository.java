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

    @Modifying
    @Query(value = "UPDATE active_devices SET expire_time = DATE_ADD(expire_time, INTERVAL :seconds SECOND) WHERE card_code IN :cardCodes", nativeQuery = true)
    int addTimeByCardCodes(@Param("cardCodes") List<String> cardCodes, @Param("seconds") long seconds);

    @Modifying
    @Query(value = "UPDATE active_devices SET expire_time = DATE_SUB(expire_time, INTERVAL :seconds SECOND) WHERE card_code IN :cardCodes", nativeQuery = true)
    int subTimeByCardCodes(@Param("cardCodes") List<String> cardCodes, @Param("seconds") long seconds);

    @Modifying
    @Query(value = "UPDATE active_devices SET expire_time = DATE_ADD(expire_time, INTERVAL :seconds SECOND) WHERE status = 1 AND expire_time > NOW() AND (:appId IS NULL OR app_id = :appId)", nativeQuery = true)
    int globalAddTime(@Param("seconds") long seconds, @Param("appId") Integer appId);
}
