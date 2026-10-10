package com.example.marketpulse.repository;

import com.example.marketpulse.entity.DailyPrice;
import com.example.marketpulse.entity.Instrument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {

    Optional<DailyPrice> findByInstrumentAndPriceDate(Instrument instrument, LocalDate priceDate);

    List<DailyPrice> findByInstrumentAndPriceDateBetweenOrderByPriceDateAsc(
            Instrument instrument, LocalDate from, LocalDate to);

    List<DailyPrice> findByInstrumentOrderByPriceDateAsc(Instrument instrument);

    List<DailyPrice> findTop2ByInstrumentOrderByPriceDateDesc(Instrument instrument);

    List<DailyPrice> findByInstrumentAndPriceDateLessThanEqualOrderByPriceDateDesc(
            Instrument instrument, LocalDate asOfDate, Pageable pageable);

    Optional<DailyPrice> findTopByInstrumentOrderByPriceDateDesc(Instrument instrument);

    long countByInstrument(Instrument instrument);
}
