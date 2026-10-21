package com.chargeshare.backend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChargerRepository extends JpaRepository<Charger, Long> {

    // Search available chargers by city and vehicle type
    List<Charger> findByCityContainingIgnoreCaseAndVehicleTypeIgnoreCaseAndAvailableTrue(
            String city,
            String vehicleType
    );

    // Find chargers belonging to the logged-in host
    List<Charger> findByHost_EmailIgnoreCase(String email);
}