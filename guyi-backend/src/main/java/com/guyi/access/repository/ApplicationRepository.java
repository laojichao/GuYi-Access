package com.guyi.access.repository;

import com.guyi.access.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Integer> {

    Optional<Application> findByAppKey(String appKey);

    boolean existsByAppNameAndIdNot(String appName, Integer id);

    @Query("SELECT a, (SELECT COUNT(c) FROM Card c WHERE c.appId = a.id) as cardCount FROM Application a ORDER BY a.createTime DESC")
    List<Object[]> findAllWithCardCount();

    List<Application> findByStatusOrderByCreateTimeDesc(Integer status);
}
