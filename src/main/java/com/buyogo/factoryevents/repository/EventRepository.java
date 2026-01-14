package com.buyogo.factoryevents.repository;

import com.buyogo.factoryevents.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, String> {
    
    /**
     * Find all events for a machine within a time window (start inclusive, end exclusive)
     */
    @Query("SELECT e FROM Event e WHERE e.machineId = :machineId " +
           "AND e.eventTime >= :start AND e.eventTime < :end")
    List<Event> findByMachineIdAndEventTimeBetween(
        @Param("machineId") String machineId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );
    
    /**
     * Count events for a machine within a time window
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.machineId = :machineId " +
           "AND e.eventTime >= :start AND e.eventTime < :end")
    long countByMachineIdAndEventTimeBetween(
        @Param("machineId") String machineId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );
    
    /**
     * Sum defects for a machine within a time window, excluding defectCount = -1
     */
    @Query("SELECT COALESCE(SUM(e.defectCount), 0) FROM Event e " +
           "WHERE e.machineId = :machineId " +
           "AND e.eventTime >= :start AND e.eventTime < :end " +
           "AND e.defectCount >= 0")
    long sumDefectsByMachineIdAndEventTimeBetween(
        @Param("machineId") String machineId,
        @Param("start") Instant start,
        @Param("end") Instant end
    );
    
    /**
     * Find top defect lines for a factory within a time window
     */
    @Query("SELECT e.lineId as lineId, " +
           "SUM(e.defectCount) as totalDefects, " +
           "COUNT(e) as eventCount " +
           "FROM Event e " +
           "WHERE e.factoryId = :factoryId " +
           "AND e.eventTime >= :from AND e.eventTime < :to " +
           "AND e.defectCount >= 0 " +
           "AND e.lineId IS NOT NULL " +
           "GROUP BY e.lineId " +
           "ORDER BY totalDefects DESC")
    List<Object[]> findTopDefectLines(
        @Param("factoryId") String factoryId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );
    
    /**
     * Find event by eventId
     */
    Optional<Event> findByEventId(String eventId);
}
