package com.example.devops;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitCountRepository extends JpaRepository<VisitCount, Long> {
    
}
