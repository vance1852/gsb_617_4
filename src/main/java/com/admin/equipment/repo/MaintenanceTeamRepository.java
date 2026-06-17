package com.admin.equipment.repo;

import com.admin.equipment.model.MaintenanceTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaintenanceTeamRepository extends JpaRepository<MaintenanceTeam, Long> {
    Optional<MaintenanceTeam> findByName(String name);
}
