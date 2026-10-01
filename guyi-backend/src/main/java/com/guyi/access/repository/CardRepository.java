package com.guyi.access.repository;

import com.guyi.access.entity.Card;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Integer> {

    Optional<Card> findByCardCode(String cardCode);

    Optional<Card> findByCardCodeAndAppId(String cardCode, Integer appId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Card c WHERE c.cardCode = :cardCode AND c.appId = :appId")
    Optional<Card> findByCardCodeAndAppIdForUpdate(@Param("cardCode") String cardCode, @Param("appId") Integer appId);

    List<Card> findByIdIn(List<Integer> ids);

    @Modifying
    @Query("DELETE FROM Card c WHERE c.id IN :ids")
    int deleteByIdIn(@Param("ids") List<Integer> ids);

    long countByAppIdGreaterThan(Integer appId);

    long countByStatusAndAppIdGreaterThan(Integer status, Integer appId);

    long countByStatusAndAppId(Integer status, Integer appId);

    /** Dashboard "expired" metric: activated cards whose expiry has already passed. */
    long countByStatusAndExpireTimeBefore(Integer status, LocalDateTime expireTime);

    long countByAppId(Integer appId);

    @Query("SELECT c.cardType, COUNT(c) FROM Card c WHERE c.appId > 0 GROUP BY c.cardType")
    List<Object[]> countGroupByCardType();

    @Query("SELECT a.appName, COUNT(c.id) FROM Card c JOIN Application a ON c.appId = a.id WHERE c.appId > 0 GROUP BY c.appId ORDER BY COUNT(c.id) DESC")
    List<Object[]> countGroupByApp();

    @Query("SELECT c FROM Card c WHERE c.status = 0 AND c.appId > 0")
    Page<Card> findUnusedCards(Pageable pageable);

    @Query("SELECT c FROM Card c WHERE c.appId = :appId")
    Page<Card> findByAppId(@Param("appId") Integer appId, Pageable pageable);

    @Query("SELECT c FROM Card c WHERE c.status = :status AND c.appId > 0")
    Page<Card> findByStatus(@Param("status") Integer status, Pageable pageable);

    @Query("SELECT c FROM Card c WHERE c.status = :status AND c.appId = :appId")
    Page<Card> findByStatusAndAppId(@Param("status") Integer status, @Param("appId") Integer appId, Pageable pageable);

    @Query("SELECT c FROM Card c WHERE c.appId = :appId AND c.cardType = :cardType")
    Page<Card> findByAppIdAndCardType(@Param("appId") Integer appId, @Param("cardType") String cardType, Pageable pageable);

    @Query("SELECT c FROM Card c WHERE c.status = :status AND c.appId = :appId AND c.cardType = :cardType")
    Page<Card> findByStatusAndAppIdAndCardType(@Param("status") Integer status, @Param("appId") Integer appId, @Param("cardType") String cardType, Pageable pageable);

    // Pattern is pre-escaped with '!' by the service layer to neutralize LIKE wildcards
    @Query("SELECT c FROM Card c JOIN Application a ON c.appId = a.id WHERE c.appId > 0 AND " +
           "(c.cardCode LIKE :kw ESCAPE '!' OR c.notes LIKE :kw ESCAPE '!' OR c.deviceHash LIKE :kw ESCAPE '!' OR a.appName LIKE :kw ESCAPE '!' OR c.cardType LIKE :kw ESCAPE '!')")
    List<Card> searchByKeyword(@Param("kw") String kw);

    /**
     * Keyword search honouring the same filters as the plain list view. Absent filters are bound as
     * sentinels (status = -1, appId = 0, cardType = "") so the query never receives an untyped NULL.
     */
    @Query("SELECT c FROM Card c JOIN Application a ON c.appId = a.id WHERE c.appId > 0 " +
           "AND (:status < 0 OR c.status = :status) " +
           "AND (:appId <= 0 OR c.appId = :appId) " +
           "AND (:cardType = '' OR c.cardType = :cardType) AND " +
           "(c.cardCode LIKE :kw ESCAPE '!' OR c.notes LIKE :kw ESCAPE '!' OR c.deviceHash LIKE :kw ESCAPE '!' OR a.appName LIKE :kw ESCAPE '!' OR c.cardType LIKE :kw ESCAPE '!')")
    Page<Card> searchByKeywordPaged(@Param("kw") String kw, @Param("status") Integer status,
                                    @Param("appId") Integer appId, @Param("cardType") String cardType,
                                    Pageable pageable);

    @Query("SELECT c FROM Card c WHERE c.status = 1 AND c.expireTime < :now")
    List<Card> findExpiredCards(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Card c SET c.deviceHash = NULL WHERE c.id IN :ids")
    int unbindByIds(@Param("ids") List<Integer> ids);

    @Modifying
    @Query(value = "UPDATE cards SET expire_time = DATE_ADD(expire_time, INTERVAL :seconds SECOND) WHERE id IN :ids AND status = 1", nativeQuery = true)
    int addTimeByIds(@Param("ids") List<Integer> ids, @Param("seconds") long seconds);

    @Modifying
    @Query(value = "UPDATE cards SET expire_time = DATE_SUB(expire_time, INTERVAL :seconds SECOND) WHERE id IN :ids AND status = 1", nativeQuery = true)
    int subTimeByIds(@Param("ids") List<Integer> ids, @Param("seconds") long seconds);

    @Modifying
    @Query(value = "UPDATE cards SET expire_time = DATE_ADD(expire_time, INTERVAL :seconds SECOND) WHERE status = 1 AND expire_time > NOW() AND (:appId IS NULL OR app_id = :appId)", nativeQuery = true)
    int globalAddTime(@Param("seconds") long seconds, @Param("appId") Integer appId);
}
