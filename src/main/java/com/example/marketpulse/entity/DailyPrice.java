package com.example.marketpulse.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "daily_prices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_prices_instrument_date", columnNames = {"instrument_id", "price_date"})
        },
        indexes = {
                @Index(name = "idx_daily_prices_instrument_date", columnList = "instrument_id, price_date")
        }
)
public class DailyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false, foreignKey = @ForeignKey(name = "fk_daily_prices_instrument"))
    private Instrument instrument;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "open_price", precision = 19, scale = 6)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 19, scale = 6)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 19, scale = 6)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal closePrice;

    @Column(name = "volume")
    private Long volume;

    public DailyPrice() {
    }

    public DailyPrice(Instrument instrument, LocalDate priceDate, BigDecimal openPrice,
                      BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice, Long volume) {
        this.instrument = instrument;
        this.priceDate = priceDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public void setPriceDate(LocalDate priceDate) {
        this.priceDate = priceDate;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DailyPrice that = (DailyPrice) o;
        return Objects.equals(instrument, that.instrument) && Objects.equals(priceDate, that.priceDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrument, priceDate);
    }

    @Override
    public String toString() {
        return "DailyPrice{" +
                "id=" + id +
                ", priceDate=" + priceDate +
                ", closePrice=" + closePrice +
                ", volume=" + volume +
                '}';
    }
}
