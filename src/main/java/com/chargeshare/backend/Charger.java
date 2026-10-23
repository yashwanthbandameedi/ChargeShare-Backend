package com.chargeshare.backend;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "chargers")
public class Charger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String city;
    private String address;

    // Nullable to keep existing charger rows valid.
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    private String vehicleType;
    private double pricePerHour;
    private boolean available = true;

    @Column(name = "payment_qr_path", length = 500)
    private String paymentQrPath;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id")
    private HostAccount host;

    public Charger() { }

    public Charger(String name, String city, String address, String vehicleType,
                   double pricePerHour, boolean available) {
        this.name = name;
        this.city = city;
        this.address = address;
        this.vehicleType = vehicleType;
        this.pricePerHour = pricePerHour;
        this.available = available;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getVehicleType() { return vehicleType; }
    public double getPricePerHour() { return pricePerHour; }
    public boolean isAvailable() { return available; }
    public String getPaymentQrPath() { return paymentQrPath; }

    @JsonIgnore
    public HostAccount getHost() { return host; }

    public void setName(String name) { this.name = name; }
    public void setCity(String city) { this.city = city; }
    public void setAddress(String address) { this.address = address; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public void setPricePerHour(double pricePerHour) { this.pricePerHour = pricePerHour; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setPaymentQrPath(String paymentQrPath) { this.paymentQrPath = paymentQrPath; }
    public void setHost(HostAccount host) { this.host = host; }
}
